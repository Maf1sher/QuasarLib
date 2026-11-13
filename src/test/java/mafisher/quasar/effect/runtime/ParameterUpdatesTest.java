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
import mafisher.quasar.api.effect.EffectCurve;
import mafisher.quasar.api.effect.EffectParameterState;
import mafisher.quasar.effect.network.EffectParameterBatchPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import mafisher.quasar.testing.GlobalStateTest;

@GlobalStateTest
class ParameterUpdatesTest {
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
    void sendsOneBatchPerPlayerAndKeepsTheLatestState() {
        UUID instanceId = UUID.randomUUID();
        this.runtime.parameterUpdates().queueVisual(
            instanceId, state(1.0F), this.tracking
        );
        this.runtime.parameterUpdates().queueVisual(
            instanceId, state(2.0F), this.tracking
        );

        this.runtime.parameterUpdates().flush();

        EffectParameterBatchPayload payload = soloPayload();
        assertEquals(1, payload.updates().size());
        assertEquals(2.0F, payload.updates().getFirst().state().value("fade").x(), 0.0F);
        assertEquals(120L, payload.updateGameTime());
    }

    @Test
    void mergesSeveralInstancesIntoOnePayload() {
        this.runtime.parameterUpdates().queueVisual(UUID.randomUUID(), state(1.0F), this.tracking);
        this.runtime.parameterUpdates().queueVisual(UUID.randomUUID(), state(2.0F), this.tracking);

        this.runtime.parameterUpdates().flush();

        assertEquals(2, soloPayload().updates().size());
    }

    @Test
    void splitsBatchesAtThePerPayloadCap() {
        for (int index = 0; index <= EffectParameterBatchPayload.MAX_UPDATES; index++) {
            this.runtime.parameterUpdates().queueVisual(
                UUID.randomUUID(), state(index), this.tracking
            );
        }

        this.runtime.parameterUpdates().flush();

        assertEquals(2, this.packets.sent().size());
    }

    @Test
    void dropsUpdatesWhenTheTrackingSetWasClearedBeforeFlush() {
        this.runtime.parameterUpdates().queueVisual(UUID.randomUUID(), state(1.0F), this.tracking);
        this.tracking.clear();

        this.runtime.parameterUpdates().flush();

        assertTrue(this.packets.sent().isEmpty());
    }

    @Test
    void sendsNothingWithoutTrackers() {
        this.runtime.parameterUpdates().queueVisual(UUID.randomUUID(), state(1.0F), Set.of());

        this.runtime.parameterUpdates().flush();

        assertTrue(this.packets.sent().isEmpty());
    }

    @Test
    void runtimeCloseDropsPendingParameters() {
        this.runtime.parameterUpdates().queueVisual(UUID.randomUUID(), state(1.0F), this.tracking);

        this.runtime.close();
        this.runtime.parameterUpdates().flush();

        assertTrue(this.packets.sent().isEmpty());
    }

    @Test
    void runtimeTickFlushesQueuedParameters() {
        this.runtime.parameterUpdates().queueVisual(UUID.randomUUID(), state(3.0F), this.tracking);

        this.runtime.tick();

        assertEquals(3.0F, soloPayload().updates().getFirst().state().value("fade").x(), 0.0F);
    }

    private EffectParameterBatchPayload soloPayload() {
        List<RecordingPacketSink.SentPacket> sent = this.packets.sent();
        assertEquals(1, sent.size());
        assertSame(this.player, sent.getFirst().player());
        return (EffectParameterBatchPayload) sent.getFirst().payload();
    }

    private static EffectParameterState state(float fade) {
        return EffectParameterState.builder()
            .put("fade", EffectCurve.Value.scalar(fade))
            .build();
    }
}
