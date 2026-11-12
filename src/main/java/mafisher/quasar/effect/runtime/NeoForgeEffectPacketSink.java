package mafisher.quasar.effect.runtime;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.ApiStatus;

/** Default packet sink backed by NeoForge's packet distributor. */
@ApiStatus.Internal
public final class NeoForgeEffectPacketSink implements EffectPacketSink {
    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public void sendToPlayersNear(
        ServerLevel level,
        Vec3 center,
        double radius,
        CustomPacketPayload payload
    ) {
        PacketDistributor.sendToPlayersNear(
            level, null, center.x, center.y, center.z, radius, payload
        );
    }

    @Override
    public void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersInDimension(level, payload);
    }
}
