package mafisher.quasar.effect;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/** Deterministic origin distribution for one client-only debris burst. */
public sealed interface EffectDebrisSpawn permits EffectDebrisSpawn.Point, EffectDebrisSpawn.Shell {
    /** A point burst using the spec's origin plus a small jitter. */
    record Point() implements EffectDebrisSpawn {
    }

    /** Pieces start on a random point in the closed spherical shell. */
    record Shell(Vec3 center, double minRadius, double maxRadius) implements EffectDebrisSpawn {
        public Shell {
            Objects.requireNonNull(center, "center");
            if (!isFinite(center)) {
                throw new IllegalArgumentException("Debris shell center must be finite");
            }
            if (!Double.isFinite(minRadius) || minRadius < 0.0
                || !Double.isFinite(maxRadius) || maxRadius < minRadius) {
                throw new IllegalArgumentException("Debris shell radii must be finite and ordered");
            }
            if (maxRadius > 1_024.0) {
                throw new IllegalArgumentException("Debris shell radius must be at most 1024");
            }
        }

        private static boolean isFinite(Vec3 value) {
            return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
        }
    }
}
