package mafisher.quasar.client.effect;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * One distance-attenuated rotational camera-shake impulse.
 *
 * <p>An impulse is authored on the client and decays over its lifetime. Its
 * strength falls off linearly from the source to {@code radius}; at or beyond
 * the radius it has no effect. Angles are added to the camera's yaw, pitch, and
 * roll, never to player rotation, so aim and gameplay stay untouched.</p>
 *
 * @param source world-space origin of the shake
 * @param radius distance at which the impulse becomes silent; positive
 * @param intensity overall strength in {@code [0, 1]}
 * @param durationTicks lifetime in ticks, 1 to 72,000
 * @param frequencyHz oscillation frequency in hertz, 0.1 to 10
 * @param maxAngleDegrees peak offset per axis in degrees, 0 to 30
 */
public record CameraShakeImpulse(
    Vec3 source,
    double radius,
    float intensity,
    int durationTicks,
    float frequencyHz,
    float maxAngleDegrees
) {
    public static final int MAX_DURATION_TICKS = 72_000;
    public static final float MIN_FREQUENCY_HZ = 0.1F;
    public static final float MAX_FREQUENCY_HZ = 10.0F;
    public static final float MAX_ANGLE_DEGREES = 30.0F;
    private static final double MIN_ATTACK_TICKS = 1.0;
    private static final double MAX_ATTACK_TICKS = 2.0;
    private static final double ATTACK_FRACTION = 0.05;
    private static final double TICKS_PER_SECOND = 20.0;

    public CameraShakeImpulse {
        Objects.requireNonNull(source, "source");
        if (!Double.isFinite(source.x) || !Double.isFinite(source.y) || !Double.isFinite(source.z)) {
            throw new IllegalArgumentException("Camera shake source must be finite");
        }
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("Camera shake radius must be finite and positive");
        }
        if (!Float.isFinite(intensity) || intensity < 0.0F || intensity > 1.0F) {
            throw new IllegalArgumentException("Camera shake intensity must be between 0 and 1");
        }
        if (durationTicks < 1 || durationTicks > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("Camera shake duration must be between 1 and "
                + MAX_DURATION_TICKS + " ticks");
        }
        if (!Float.isFinite(frequencyHz) || frequencyHz < MIN_FREQUENCY_HZ
            || frequencyHz > MAX_FREQUENCY_HZ) {
            throw new IllegalArgumentException("Camera shake frequency must be between "
                + MIN_FREQUENCY_HZ + " and " + MAX_FREQUENCY_HZ + " Hz");
        }
        if (!Float.isFinite(maxAngleDegrees) || maxAngleDegrees < 0.0F
            || maxAngleDegrees > MAX_ANGLE_DEGREES) {
            throw new IllegalArgumentException("Camera shake angle must be between 0 and "
                + MAX_ANGLE_DEGREES + " degrees");
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

    /** Returns the combined intensity and distance/envelope amplitude in {@code [0, 1]}. */
    public float amplitudeAt(Vec3 cameraPosition, float ageTicks) {
        return this.intensity * this.attenuationAt(cameraPosition) * this.envelopeAt(ageTicks);
    }

    /**
     * Samples yaw, pitch, and roll offsets in degrees for the current frame.
     *
     * <p>The wave is deterministic from the age, so repeated calls with the same
     * arguments return the same angles. Pitch and roll use lower and shifted
     * amplitudes so the shake reads as an impact rather than a uniform spin.</p>
     */
    public Vector3f angleOffsetsDegrees(Vec3 cameraPosition, float ageTicks) {
        float amplitude = this.amplitudeAt(cameraPosition, ageTicks) * this.maxAngleDegrees;
        if (amplitude <= 0.0F) {
            return new Vector3f();
        }
        double seconds = Math.max(0.0, ageTicks) / TICKS_PER_SECOND;
        double phase = seconds * this.frequencyHz * Math.PI * 2.0;
        float yaw = (float) (Math.sin(phase) * amplitude);
        float pitch = (float) (Math.sin(phase * 1.37 + 1.1) * amplitude * 0.7);
        float roll = (float) (Math.sin(phase * 0.73 + 2.3) * amplitude * 0.5);
        return new Vector3f(yaw, pitch, roll);
    }

    /** Returns the attack/decay envelope in {@code [0, 1]}; zero outside the lifetime. */
    float envelopeAt(float ageTicks) {
        if (ageTicks < 0.0F || ageTicks >= this.durationTicks) {
            return 0.0F;
        }
        double attack = Math.min(MAX_ATTACK_TICKS,
            Math.max(MIN_ATTACK_TICKS, this.durationTicks * ATTACK_FRACTION));
        if (ageTicks < attack) {
            return (float) (ageTicks / attack);
        }
        double remaining = 1.0 - (ageTicks - attack) / Math.max(1.0, this.durationTicks - attack);
        return (float) (remaining * remaining);
    }
}
