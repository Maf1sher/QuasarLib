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
    EffectAudience audience
) {
    public static final int MAX_PIECES = 64;
    public static final int MAX_LIFETIME_TICKS = 200;
    public static final double MAX_SPEED = 16.0;
    public static final double MAX_GRAVITY = 1.0;

    public EffectDebrisSpec {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(audience, "audience");
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

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
