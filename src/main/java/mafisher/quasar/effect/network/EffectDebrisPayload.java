package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import java.util.Objects;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.EffectDebrisSpec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * One client-only block-debris burst. The server sends a compact description; clients derive every
 * piece from the seed, so this payload never spawns entities and is not persisted.
 */
public record EffectDebrisPayload(
    ResourceLocation dimensionId,
    Vec3 origin,
    int blockStateId,
    int pieces,
    Vec3 direction,
    float speed,
    float spread,
    float gravity,
    int lifetimeTicks,
    long seed,
    long serverGameTime
) implements CustomPacketPayload {
    public static final Type<EffectDebrisPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "effect_debris")
    );
    public static final StreamCodec<ByteBuf, EffectDebrisPayload> STREAM_CODEC = StreamCodec.of(
        EffectDebrisPayload::encode,
        EffectDebrisPayload::decode
    );

    public EffectDebrisPayload {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(direction, "direction");
        if (!isFinite(origin) || !isFinite(direction)) {
            throw new IllegalArgumentException("Debris payload positions must be finite");
        }
        if (blockStateId <= 0) {
            throw new IllegalArgumentException("Debris payload needs a non-air block state");
        }
        if (pieces < 1 || pieces > EffectDebrisSpec.MAX_PIECES) {
            throw new IllegalArgumentException("Invalid debris piece count");
        }
        if (!Float.isFinite(speed) || speed < 0.0F || speed > EffectDebrisSpec.MAX_SPEED) {
            throw new IllegalArgumentException("Invalid debris speed");
        }
        if (!Float.isFinite(spread) || spread < 0.0F || spread > 1.0F) {
            throw new IllegalArgumentException("Invalid debris spread");
        }
        if (!Float.isFinite(gravity)
            || gravity < -EffectDebrisSpec.MAX_GRAVITY || gravity > EffectDebrisSpec.MAX_GRAVITY) {
            throw new IllegalArgumentException("Invalid debris gravity");
        }
        if (lifetimeTicks < 1 || lifetimeTicks > EffectDebrisSpec.MAX_LIFETIME_TICKS) {
            throw new IllegalArgumentException("Invalid debris lifetime");
        }
    }

    private static void encode(ByteBuf buffer, EffectDebrisPayload payload) {
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimensionId());
        writeVec3(buffer, payload.origin());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.blockStateId());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.pieces());
        writeVec3(buffer, payload.direction());
        buffer.writeFloat(payload.speed());
        buffer.writeFloat(payload.spread());
        buffer.writeFloat(payload.gravity());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.lifetimeTicks());
        buffer.writeLong(payload.seed());
        buffer.writeLong(payload.serverGameTime());
    }

    private static EffectDebrisPayload decode(ByteBuf buffer) {
        return new EffectDebrisPayload(
            ResourceLocation.STREAM_CODEC.decode(buffer),
            readVec3(buffer),
            ByteBufCodecs.VAR_INT.decode(buffer),
            ByteBufCodecs.VAR_INT.decode(buffer),
            readVec3(buffer),
            buffer.readFloat(),
            buffer.readFloat(),
            buffer.readFloat(),
            ByteBufCodecs.VAR_INT.decode(buffer),
            buffer.readLong(),
            buffer.readLong()
        );
    }

    private static void writeVec3(ByteBuf buffer, Vec3 value) {
        buffer.writeDouble(value.x);
        buffer.writeDouble(value.y);
        buffer.writeDouble(value.z);
    }

    private static Vec3 readVec3(ByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    @Override
    public Type<EffectDebrisPayload> type() {
        return TYPE;
    }
}
