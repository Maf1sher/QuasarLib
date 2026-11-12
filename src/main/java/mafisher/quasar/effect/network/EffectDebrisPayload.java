package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import java.util.Objects;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.effect.EffectDebrisAttractor;
import mafisher.quasar.api.effect.EffectDebrisSpawn;
import mafisher.quasar.api.effect.EffectDebrisSpec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/**
 * One client-only block-debris burst. The server sends a compact description; clients derive every
 * piece from the seed, so this payload never spawns entities and is not persisted.
 */
@ApiStatus.Internal
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
    long serverGameTime,
    EffectDebrisAttractor attractor,
    EffectDebrisSpawn spawn,
    float scale
) implements CustomPacketPayload {
    public static final Type<EffectDebrisPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "effect_debris")
    );
    public static final StreamCodec<ByteBuf, EffectDebrisPayload> STREAM_CODEC = StreamCodec.of(
        EffectDebrisPayload::encode,
        EffectDebrisPayload::decode
    );

    /** Source-compatible ballistic payload. */
    public EffectDebrisPayload(
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
    ) {
        this(dimensionId, origin, blockStateId, pieces, direction, speed, spread, gravity,
            lifetimeTicks, seed, serverGameTime, null, new EffectDebrisSpawn.Point(), 1.0F);
    }

    /** Source-compatible payload without an explicit piece-size multiplier. */
    public EffectDebrisPayload(
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
        long serverGameTime,
        EffectDebrisAttractor attractor,
        EffectDebrisSpawn spawn
    ) {
        this(dimensionId, origin, blockStateId, pieces, direction, speed, spread, gravity,
            lifetimeTicks, seed, serverGameTime, attractor, spawn, 1.0F);
    }

    public EffectDebrisPayload {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(direction, "direction");
        spawn = spawn == null ? new EffectDebrisSpawn.Point() : spawn;
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
        if (!Float.isFinite(scale) || scale <= 0.0F || scale > EffectDebrisSpec.MAX_SCALE) {
            throw new IllegalArgumentException("Invalid debris scale");
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
        buffer.writeFloat(payload.scale());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.lifetimeTicks());
        buffer.writeLong(payload.seed());
        buffer.writeLong(payload.serverGameTime());
        buffer.writeBoolean(payload.attractor() != null);
        if (payload.attractor() != null) {
            EffectDebrisAttractor attractor = payload.attractor();
            writeVec3(buffer, attractor.center());
            writeVec3(buffer, attractor.axis());
            buffer.writeDouble(attractor.horizonRadius());
            buffer.writeDouble(attractor.radialPull());
            buffer.writeDouble(attractor.swirl());
        }
        if (payload.spawn() instanceof EffectDebrisSpawn.Shell shell) {
            buffer.writeByte(1);
            writeVec3(buffer, shell.center());
            buffer.writeDouble(shell.minRadius());
            buffer.writeDouble(shell.maxRadius());
        } else if (payload.spawn() instanceof EffectDebrisSpawn.Disk disk) {
            buffer.writeByte(2);
            writeVec3(buffer, disk.center());
            buffer.writeDouble(disk.minRadius());
            buffer.writeDouble(disk.maxRadius());
            buffer.writeDouble(disk.ySpread());
        } else if (payload.spawn() instanceof EffectDebrisSpawn.Surface surface) {
            buffer.writeByte(3);
            writeVec3(buffer, surface.center());
            buffer.writeDouble(surface.minRadius());
            buffer.writeDouble(surface.maxRadius());
            buffer.writeDouble(surface.lift());
        } else {
            buffer.writeByte(0);
        }
    }

    private static EffectDebrisPayload decode(ByteBuf buffer) {
        ResourceLocation dimensionId = ResourceLocation.STREAM_CODEC.decode(buffer);
        Vec3 origin = readVec3(buffer);
        int blockStateId = ByteBufCodecs.VAR_INT.decode(buffer);
        int pieces = ByteBufCodecs.VAR_INT.decode(buffer);
        Vec3 direction = readVec3(buffer);
        float speed = buffer.readFloat();
        float spread = buffer.readFloat();
        float gravity = buffer.readFloat();
        float scale = buffer.readFloat();
        int lifetimeTicks = ByteBufCodecs.VAR_INT.decode(buffer);
        long seed = buffer.readLong();
        long serverGameTime = buffer.readLong();
        EffectDebrisAttractor attractor = null;
        if (buffer.readBoolean()) {
            attractor = new EffectDebrisAttractor(
                readVec3(buffer), readVec3(buffer), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble()
            );
        }
        EffectDebrisSpawn spawn = new EffectDebrisSpawn.Point();
        int spawnType = buffer.readUnsignedByte();
        if (spawnType == 1) {
            spawn = new EffectDebrisSpawn.Shell(
                readVec3(buffer), buffer.readDouble(), buffer.readDouble()
            );
        } else if (spawnType == 2) {
            spawn = new EffectDebrisSpawn.Disk(
                readVec3(buffer), buffer.readDouble(), buffer.readDouble(), buffer.readDouble()
            );
        } else if (spawnType == 3) {
            spawn = new EffectDebrisSpawn.Surface(
                readVec3(buffer), buffer.readDouble(), buffer.readDouble(), buffer.readDouble()
            );
        }
        return new EffectDebrisPayload(
            dimensionId, origin, blockStateId, pieces, direction, speed, spread, gravity,
            lifetimeTicks, seed, serverGameTime, attractor, spawn, scale
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
