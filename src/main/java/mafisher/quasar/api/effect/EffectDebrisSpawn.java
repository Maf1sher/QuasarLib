package mafisher.quasar.api.effect;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/** Deterministic origin distribution for one client-only debris burst. */
public sealed interface EffectDebrisSpawn
    permits EffectDebrisSpawn.Point, EffectDebrisSpawn.Shell, EffectDebrisSpawn.Disk,
    EffectDebrisSpawn.Surface {
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

    /** Pieces start on a horizontal annulus at {@code center}, with a small vertical spread. */
    record Disk(Vec3 center, double minRadius, double maxRadius, double ySpread)
        implements EffectDebrisSpawn {
        public Disk {
            Objects.requireNonNull(center, "center");
            if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
                throw new IllegalArgumentException("Debris disk center must be finite");
            }
            if (!Double.isFinite(minRadius) || minRadius < 0.0
                || !Double.isFinite(maxRadius) || maxRadius < minRadius) {
                throw new IllegalArgumentException("Debris disk radii must be finite and ordered");
            }
            if (!Double.isFinite(ySpread) || ySpread < 0.0 || ySpread > 64.0) {
                throw new IllegalArgumentException("Debris disk vertical spread must be between 0 and 64");
            }
            if (maxRadius > 1_024.0) {
                throw new IllegalArgumentException("Debris disk radius must be at most 1024");
            }
        }
    }

    /**
     * Pieces start on the terrain surface inside the horizontal annulus around {@code center}.
     * Clients project every candidate onto their local heightmap and sample the block state there,
     * so the chunks leave the ground where it is actually visible instead of floating in a volume.
     * Unloaded or empty columns are skipped. {@code lift} raises the start point above the surface.
     */
    record Surface(Vec3 center, double minRadius, double maxRadius, double lift)
        implements EffectDebrisSpawn {
        public Surface {
            Objects.requireNonNull(center, "center");
            if (!Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
                throw new IllegalArgumentException("Debris surface center must be finite");
            }
            if (!Double.isFinite(minRadius) || minRadius < 0.0
                || !Double.isFinite(maxRadius) || maxRadius < minRadius) {
                throw new IllegalArgumentException("Debris surface radii must be finite and ordered");
            }
            if (maxRadius > 1_024.0) {
                throw new IllegalArgumentException("Debris surface radius must be at most 1024");
            }
            if (!Double.isFinite(lift) || lift < 0.0 || lift > 8.0) {
                throw new IllegalArgumentException("Debris surface lift must be between 0 and 8");
            }
        }
    }
}
