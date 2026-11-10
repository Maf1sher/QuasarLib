package mafisher.quasar.effect;

import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only block debris emitted by a gameplay effect. The server sends one burst description;
 * clients derive deterministic piece positions and velocities from the effect seed, so no entities
 * are spawned and no per-piece state is persisted.
 *
 * @param origin burst center in world space
 * @param state block state supplying the debris texture; must not be air
 * @param pieces number of pieces, 1 to {@link #MAX_PIECES}
 * @param direction optional initial direction bias; {@link Vec3#ZERO} means a uniform random spread
 * @param speed maximum initial speed in blocks per tick
 * @param spread {@code 0} follows {@code direction}, {@code 1} is fully random
 * @param gravity downward acceleration in blocks per tick squared; negative values float upward
 * @param lifetimeTicks visible lifetime, 1 to {@link #MAX_LIFETIME_TICKS}
 * @param audience players that should receive the burst
 * @param scale multiplier applied to every piece size; 1 is the base debris cube
 */
public record EffectDebrisSpec(
    Vec3 origin,
    BlockState state,
    int pieces,
    Vec3 direction,
    double speed,
    double spread,
    double gravity,
    int lifetimeTicks,
    EffectAudience audience,
    EffectDebrisAttractor attractor,
    EffectDebrisSpawn spawn,
    float scale
) {
    public static final int MAX_PIECES = 64;
    public static final int MAX_LIFETIME_TICKS = 200;
    public static final double MAX_SPEED = 16.0;
    public static final double MAX_GRAVITY = 1.0;
    public static final float MAX_SCALE = 8.0F;

    /** Source-compatible constructor for bursts without an explicit scale. */
    public EffectDebrisSpec(
        Vec3 origin,
        BlockState state,
        int pieces,
        Vec3 direction,
        double speed,
        double spread,
        double gravity,
        int lifetimeTicks,
        EffectAudience audience,
        EffectDebrisAttractor attractor,
        EffectDebrisSpawn spawn
    ) {
        this(origin, state, pieces, direction, speed, spread, gravity, lifetimeTicks, audience,
            attractor, spawn, 1.0F);
    }

    /** Source-compatible point burst without an attractor. */
    public EffectDebrisSpec(
        Vec3 origin,
        BlockState state,
        int pieces,
        Vec3 direction,
        double speed,
        double spread,
        double gravity,
        int lifetimeTicks,
        EffectAudience audience
    ) {
        this(origin, state, pieces, direction, speed, spread, gravity, lifetimeTicks, audience,
            null, new EffectDebrisSpawn.Point());
    }

    public EffectDebrisSpec {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(audience, "audience");
        spawn = spawn == null ? new EffectDebrisSpawn.Point() : spawn;
        if (!isFinite(origin) || !isFinite(direction)) {
            throw new IllegalArgumentException("Debris origin and direction must be finite");
        }
        if (state.isAir()) {
            throw new IllegalArgumentException("Debris block state must not be air");
        }
        if (pieces < 1 || pieces > MAX_PIECES) {
            throw new IllegalArgumentException("Debris pieces must be between 1 and " + MAX_PIECES);
        }
        if (!Double.isFinite(speed) || speed < 0.0 || speed > MAX_SPEED) {
            throw new IllegalArgumentException("Debris speed must be between 0 and " + MAX_SPEED);
        }
        if (!Double.isFinite(spread) || spread < 0.0 || spread > 1.0) {
            throw new IllegalArgumentException("Debris spread must be between 0 and 1");
        }
        if (!Double.isFinite(gravity) || gravity < -MAX_GRAVITY || gravity > MAX_GRAVITY) {
            throw new IllegalArgumentException(
                "Debris gravity must be between -" + MAX_GRAVITY + " and " + MAX_GRAVITY
            );
        }
        if (lifetimeTicks < 1 || lifetimeTicks > MAX_LIFETIME_TICKS) {
            throw new IllegalArgumentException(
                "Debris lifetime must be between 1 and " + MAX_LIFETIME_TICKS
            );
        }
        if (!Float.isFinite(scale) || scale <= 0.0F || scale > MAX_SCALE) {
            throw new IllegalArgumentException("Debris scale must be greater than 0 and at most " + MAX_SCALE);
        }
    }

    /** A short ballistic burst with a small upward bias. */
    public static EffectDebrisSpec ballistic(
        Vec3 origin,
        BlockState state,
        int pieces,
        double speed,
        int lifetimeTicks,
        EffectAudience audience
    ) {
        return new EffectDebrisSpec(
            origin, state, pieces, new Vec3(0.0, 1.0, 0.0), speed, 0.6, 0.08, lifetimeTicks, audience
        );
    }

    /**
     * A curved field burst: pieces start in {@code spawn} and spiral into the attractor horizon.
     * The server emits a few of these per second instead of one payload per destroyed block.
     */
    public static EffectDebrisSpec infallField(
        Vec3 origin,
        BlockState state,
        int pieces,
        double speed,
        double spread,
        int lifetimeTicks,
        EffectAudience audience,
        EffectDebrisAttractor attractor,
        EffectDebrisSpawn spawn
    ) {
        return infallField(
            origin, state, pieces, speed, spread, lifetimeTicks, audience, attractor, spawn, 1.0F
        );
    }

    /** Infall burst with an explicit piece-size multiplier. */
    public static EffectDebrisSpec infallField(
        Vec3 origin,
        BlockState state,
        int pieces,
        double speed,
        double spread,
        int lifetimeTicks,
        EffectAudience audience,
        EffectDebrisAttractor attractor,
        EffectDebrisSpawn spawn,
        float scale
    ) {
        return new EffectDebrisSpec(
            origin, state, pieces, Vec3.ZERO, speed, spread, 0.0, lifetimeTicks, audience,
            Objects.requireNonNull(attractor, "attractor"),
            Objects.requireNonNull(spawn, "spawn"),
            scale
        );
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
