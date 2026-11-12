package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.effect.network.EffectTransformBatchPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class EffectTransformBatchServiceTest {
    private ServerLevel level;
    private ServerPlayer player;
    private Set<UUID> tracking;

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
    }

    @AfterEach
    void tearDown() {
        EffectTransformBatchService.clear(this.level);
    }

    @Test
    void sendsOneBatchPerPlayerAndKeepsTheLatestTransform() {
        UUID instanceId = UUID.randomUUID();
        EffectTransformBatchService.queueVisual(this.level, instanceId, transform(1.0), this.tracking);
        EffectTransformBatchService.queueVisual(this.level, instanceId, transform(2.0), this.tracking);

        try (MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
            EffectTransformBatchService.flush(this.level);

            ArgumentCaptor<EffectTransformBatchPayload> payloads =
                ArgumentCaptor.forClass(EffectTransformBatchPayload.class);
            packets.verify(
                () -> PacketDistributor.sendToPlayer(eq(this.player), payloads.capture()),
                times(1)
            );
            assertEquals(1, payloads.getValue().updates().size());
            assertEquals(2.0, payloads.getValue().updates().getFirst().x());
            assertEquals(120L, payloads.getValue().updateGameTime());
        }
    }

    @Test
    void mergesVisualAndSegmentedUpdatesIntoOnePayload() {
        EffectTransformBatchService.queueVisual(this.level, UUID.randomUUID(), transform(1.0), this.tracking);
        EffectTransformBatchService.queueSegmented(this.level, UUID.randomUUID(), transform(2.0), this.tracking);

        try (MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
            EffectTransformBatchService.flush(this.level);

            ArgumentCaptor<EffectTransformBatchPayload> payloads =
                ArgumentCaptor.forClass(EffectTransformBatchPayload.class);
            packets.verify(
                () -> PacketDistributor.sendToPlayer(eq(this.player), payloads.capture()),
                times(1)
            );
            assertEquals(2, payloads.getValue().updates().size());
            assertEquals(1, payloads.getValue().updates().stream().filter(entry -> !entry.segmented()).count());
            assertEquals(1, payloads.getValue().updates().stream().filter(EffectTransformBatchPayload.Entry::segmented).count());
        }
    }

    @Test
    void splitsBatchesAtThePerPayloadCap() {
        for (int index = 0; index < EffectTransformBatchPayload.MAX_UPDATES + 1; index++) {
            EffectTransformBatchService.queueVisual(this.level, UUID.randomUUID(), transform(index), this.tracking);
        }

        try (MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
            EffectTransformBatchService.flush(this.level);

            packets.verify(
                () -> PacketDistributor.sendToPlayer(eq(this.player), any(EffectTransformBatchPayload.class)),
                times(2)
            );
        }
    }

    @Test
    void dropsUpdatesWhenTheTrackingSetWasClearedBeforeFlush() {
        EffectTransformBatchService.queueVisual(this.level, UUID.randomUUID(), transform(1.0), this.tracking);
        this.tracking.clear();

        try (MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
            EffectTransformBatchService.flush(this.level);

            packets.verifyNoInteractions();
        }
    }

    @Test
    void sendsNothingWithoutTrackers() {
        EffectTransformBatchService.queueVisual(this.level, UUID.randomUUID(), transform(1.0), Set.of());

        try (MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
            EffectTransformBatchService.flush(this.level);

            packets.verifyNoInteractions();
        }
    }

    private static EffectTransform transform(double x) {
        return new EffectTransform(new EffectPoint(x, 64.0, -x), 15.0F, 20.0F, 25.0F);
    }
}
