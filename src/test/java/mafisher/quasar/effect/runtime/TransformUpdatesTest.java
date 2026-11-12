package mafisher.quasar.effect.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.effect.network.EffectTransformBatchPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransformUpdatesTest {
    private ServerLevel level;
    private ServerPlayer player;
    private Set<UUID> tracking;
    private RecordingPacketSink packets;
    private LevelEffectRuntime runtime;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenReturn(120L);

        this.player = mock(ServerPlayer.class);
        when(this.player.level()).thenReturn(this.level);
        when(this.player.getUUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        MinecraftServer server = mock(MinecraftServer.class);
        PlayerList playerList = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(playerList);
        when(playerList.getPlayer(this.player.getUUID())).thenReturn(this.player);
        when(this.level.getServer()).thenReturn(server);

        this.tracking = new HashSet<>();
        this.tracking.add(this.player.getUUID());
        this.packets = new RecordingPacketSink();
        this.runtime = TestEffectRuntime.install(this.level, this.packets);
    }

    @AfterEach
    void tearDown() {
        TestEffectRuntime.uninstall(this.level);
    }

    @Test
    void sendsOneBatchPerPlayerAndKeepsTheLatestTransform() {
        UUID instanceId = UUID.randomUUID();
        this.runtime.transforms().queueVisual(instanceId, transform(1.0), this.tracking);
        this.runtime.transforms().queueVisual(instanceId, transform(2.0), this.tracking);

        this.runtime.transforms().flush();

        EffectTransformBatchPayload payload = soloPayload();
        assertEquals(1, payload.updates().size());
        assertEquals(2.0, payload.updates().getFirst().x());
        assertEquals(120L, payload.updateGameTime());
    }

    @Test
    void mergesVisualAndSegmentedUpdatesIntoOnePayload() {
        this.runtime.transforms().queueVisual(UUID.randomUUID(), transform(1.0), this.tracking);
        this.runtime.transforms().queueSegmented(UUID.randomUUID(), transform(2.0), this.tracking);

        this.runtime.transforms().flush();

        EffectTransformBatchPayload payload = soloPayload();
        assertEquals(2, payload.updates().size());
        assertEquals(1, payload.updates().stream().filter(entry -> !entry.segmented()).count());
        assertEquals(1, payload.updates().stream()
            .filter(EffectTransformBatchPayload.Entry::segmented).count());
    }

    @Test
    void splitsBatchesAtThePerPayloadCap() {
        for (int index = 0; index < EffectTransformBatchPayload.MAX_UPDATES + 1; index++) {
            this.runtime.transforms().queueVisual(
                UUID.randomUUID(), transform(index), this.tracking
            );
        }

        this.runtime.transforms().flush();

        assertEquals(2, this.packets.sent().size());
    }

    @Test
    void dropsUpdatesWhenTheTrackingSetWasClearedBeforeFlush() {
        this.runtime.transforms().queueVisual(UUID.randomUUID(), transform(1.0), this.tracking);
        this.tracking.clear();

        this.runtime.transforms().flush();

        assertTrue(this.packets.sent().isEmpty());
    }

    @Test
    void sendsNothingWithoutTrackers() {
        this.runtime.transforms().queueVisual(UUID.randomUUID(), transform(1.0), Set.of());

        this.runtime.transforms().flush();

        assertTrue(this.packets.sent().isEmpty());
    }

    @Test
    void runtimeCloseDropsPendingTransforms() {
        this.runtime.transforms().queueVisual(UUID.randomUUID(), transform(1.0), this.tracking);

        this.runtime.close();
        this.runtime.transforms().flush();

        assertTrue(this.packets.sent().isEmpty());
    }

    @Test
    void runtimeTickFlushesQueuedTransforms() {
        this.runtime.transforms().queueVisual(UUID.randomUUID(), transform(3.0), this.tracking);

        this.runtime.tick();

        EffectTransformBatchPayload payload = soloPayload();
        assertEquals(3.0, payload.updates().getFirst().x());
    }

    private EffectTransformBatchPayload soloPayload() {
        List<RecordingPacketSink.SentPacket> sent = this.packets.sent();
        assertEquals(1, sent.size());
        assertSame(this.player, sent.getFirst().player());
        return (EffectTransformBatchPayload) sent.getFirst().payload();
    }

    private static EffectTransform transform(double x) {
        return new EffectTransform(new EffectPoint(x, 64.0, -x), 15.0F, 20.0F, 25.0F);
    }
}
