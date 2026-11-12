package mafisher.quasar.effect.runtime;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Test sink that records every payload instead of touching NeoForge networking. */
public final class RecordingPacketSink implements EffectPacketSink {
    private final List<SentPacket> sent = new ArrayList<>();

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        this.sent.add(new SentPacket(player, payload));
    }

    @Override
    public void sendToPlayersNear(
        ServerLevel level,
        Vec3 center,
        double radius,
        CustomPacketPayload payload
    ) {
        this.sent.add(new SentPacket(null, payload));
    }

    @Override
    public void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload) {
        this.sent.add(new SentPacket(null, payload));
    }

    public List<SentPacket> sent() {
        return List.copyOf(this.sent);
    }

    public void clear() {
        this.sent.clear();
    }

    /** One recorded packet and its recipient when the send was player-scoped. */
    public record SentPacket(ServerPlayer player, CustomPacketPayload payload) {
    }
}
