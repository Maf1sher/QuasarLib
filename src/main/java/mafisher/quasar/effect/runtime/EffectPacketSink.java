package mafisher.quasar.effect.runtime;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/** Per-level packet delivery seam. Tests record payloads without touching NeoForge networking. */
@ApiStatus.Internal
public interface EffectPacketSink {
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    void sendToPlayersNear(ServerLevel level, Vec3 center, double radius, CustomPacketPayload payload);

    void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload);
}
