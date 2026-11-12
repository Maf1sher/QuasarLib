package mafisher.quasar.client.effect;

import java.util.Objects;
import mafisher.quasar.effect.EffectTimeline;
import net.minecraft.world.phys.Vec3;

/**
 * One distance-attenuated additive screen-flash impulse.
 *
 * <p>The flash is strongest on the tick it is triggered and fades out
 * quadratically. Strength falls off linearly from the source to {@code radius};
 * at or beyond the radius the flash is invisible. Flashes are drawn after screen
 * composition and do not require a {@link ScreenEffectSettings} operation.</p>
 *
 * @param source world-space origin of the flash
 * @param radius distance at which the flash becomes invisible; positive
 * @param intensity overall strength in {@code [0, 1]}
 * @param durationTicks lifetime in ticks, 1 to 72,000
 * @param red red component in {@code [0, 1]}
 * @param green green component in {@code [0, 1]}
 * @param blue blue component in {@code [0, 1]}
 */
public record ScreenFlashImpulse(
    Vec3 source,
    double radius,
    float intensity,
    int durationTicks,
    float red,
    float green,
    float blue
) {
    public static final int MAX_DURATION_TICKS = EffectTimeline.MAX_DURATION_TICKS;

    public ScreenFlashImpulse {
        Objects.requireNonNull(source, "source");
        if (!Double.isFinite(source.x) || !Double.isFinite(source.y) || !Double.isFinite(source.z)) {
            throw new IllegalArgumentException("Screen flash source must be finite");
        }
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("Screen flash radius must be finite and positive");
        }
        if (!Float.isFinite(intensity) || intensity < 0.0F || intensity > 1.0F) {
            throw new IllegalArgumentException("Screen flash intensity must be between 0 and 1");
        }
        if (!EffectTimeline.isValidDuration(durationTicks)) {
            throw new IllegalArgumentException("Screen flash duration must be between 1 and "
                + MAX_DURATION_TICKS + " ticks");
        }
        if (!Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue)
            || red < 0.0F || red > 1.0F || green < 0.0F || green > 1.0F
            || blue < 0.0F || blue > 1.0F) {
            throw new IllegalArgumentException("Screen flash color components must be between 0 and 1");
        }
    }

    /** Returns linear distance falloff in {@code [0, 1]} for a camera position. */
    public float attenuationAt(Vec3 cameraPosition) {
        Objects.requireNonNull(cameraPosition, "cameraPosition");
        double distance = this.source.distanceTo(cameraPosition);
        if (distance >= this.radius) {
            return 0.0F;
        }
        return (float) (1.0 - distance / this.radius);
    }

    /** Returns the attenuated, time-decayed flash strength in {@code [0, 1]}. */
    public float strengthAt(Vec3 cameraPosition, float ageTicks) {
        if (ageTicks < 0.0F || ageTicks >= this.durationTicks) {
            return 0.0F;
        }
        double remaining = 1.0 - ageTicks / (double) this.durationTicks;
        return this.intensity * this.attenuationAt(cameraPosition) * (float) (remaining * remaining);
    }
}
