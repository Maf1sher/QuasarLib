package mafisher.quasar.api.gameplay.block;

import java.util.HashSet;
import java.util.Set;
import mafisher.quasar.api.effect.EffectBounds;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.jspecify.annotations.Nullable;

/** Shared, Minecraft-independent math for carve plans and the bulk editor. */
@SuppressWarnings({
    "PMD.TooManyMethods",
    "PMD.CognitiveComplexity",
    "checkstyle:ParameterNumber"
})
final class CarveVolumeMath {
    private static final double FOUR_THIRDS_PI = (4.0 / 3.0) * Math.PI;

    private CarveVolumeMath() {
    }

    /** Scales a volume for the selected front. Top-down keeps the vertical extent fixed. */
    static EffectBlockVolume scaleForOrdering(
        EffectBlockVolume volume,
        double factor,
        EffectBulkBlockEditor.Ordering ordering
    ) {
        if (ordering == EffectBulkBlockEditor.Ordering.TOP_DOWN) {
            return scaleRadius(volume, factor);
        }
        return scale(volume, factor);
    }

    /**
     * Shrinks only the horizontal radius of a vertical cylinder. The authored bottom and top stay
     * exact so a budget reduction degrades the hole width, never its reach to the protected floor.
     */
    static EffectBlockVolume scaleRadius(EffectBlockVolume volume, double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0) {
            throw new IllegalArgumentException("Carve scale must be finite and positive");
        }
        if (volume instanceof EffectBlockVolume.Cylinder cylinder) {
            return EffectBlockVolume.cylinder(
                cylinder.center(),
                cylinder.radius() * factor,
                cylinder.minY(),
                cylinder.maxY()
            );
        }
        throw new IllegalArgumentException("Radius-only carve scaling requires a cylinder");
    }

    static EffectBlockVolume scale(EffectBlockVolume volume, double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0) {
            throw new IllegalArgumentException("Carve scale must be finite and positive");
        }
        if (volume instanceof EffectBlockVolume.Sphere sphere) {
            return EffectBlockVolume.sphere(sphere.center(), sphere.radius() * factor);
        }
        if (volume instanceof EffectBlockVolume.Ellipsoid ellipsoid) {
            return EffectBlockVolume.ellipsoid(
                ellipsoid.center(),
                ellipsoid.radiusX() * factor,
                ellipsoid.radiusY() * factor,
                ellipsoid.radiusZ() * factor
            );
        }
        if (volume instanceof EffectBlockVolume.Cylinder cylinder) {
            double centerY = (cylinder.minY() + cylinder.maxY()) * 0.5;
            double halfHeight = (cylinder.maxY() - cylinder.minY()) * 0.5 * factor;
            return EffectBlockVolume.cylinder(
                cylinder.center(),
                cylinder.radius() * factor,
                centerY - halfHeight,
                centerY + halfHeight
            );
        }
        EffectBounds bounds = volume.bounds();
        double centerX = (bounds.minX() + bounds.maxX()) * 0.5;
        double centerY = (bounds.minY() + bounds.maxY()) * 0.5;
        double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5;
        double halfX = (bounds.maxX() - bounds.minX()) * 0.5 * factor;
        double halfY = (bounds.maxY() - bounds.minY()) * 0.5 * factor;
        double halfZ = (bounds.maxZ() - bounds.minZ()) * 0.5 * factor;
        return EffectBlockVolume.box(new EffectBounds(
            centerX - halfX,
            centerY - halfY,
            centerZ - halfZ,
            centerX + halfX,
            centerY + halfY,
            centerZ + halfZ
        ));
    }

    /** Largest half-extent of the volume, used for the inner-core front ratio. */
    static double maxRadius(EffectBlockVolume volume) {
        EffectBounds bounds = volume.bounds();
        return Math.max(
            bounds.maxX() - bounds.minX(),
            Math.max(bounds.maxY() - bounds.minY(), bounds.maxZ() - bounds.minZ())
        ) * 0.5;
    }

    static double geometricVolume(EffectBlockVolume volume) {
        if (volume instanceof EffectBlockVolume.Sphere sphere) {
            double radius = sphere.radius();
            return FOUR_THIRDS_PI * radius * radius * radius;
        }
        if (volume instanceof EffectBlockVolume.Ellipsoid ellipsoid) {
            return FOUR_THIRDS_PI
                * ellipsoid.radiusX()
                * ellipsoid.radiusY()
                * ellipsoid.radiusZ();
        }
        EffectBounds bounds = volume.bounds();
        if (volume instanceof EffectBlockVolume.Cylinder cylinder) {
            return Math.PI
                * cylinder.radius()
                * cylinder.radius()
                * (cylinder.maxY() - cylinder.minY());
        }
        return (bounds.maxX() - bounds.minX())
            * (bounds.maxY() - bounds.minY())
            * (bounds.maxZ() - bounds.minZ());
    }

    /**
     * Volume between the protected world floor and the sampled surface. Above-surface volume is
     * air and never contributes to the destruction front.
     */
    static double pacingVolume(EffectBlockVolume volume, double surfaceY, double floorY) {
        return pacingVolumeAtScale(volume, 1.0, surfaceY, floorY);
    }

    /** Volume between the protected world floor and surface after uniform scaling. */
    static double pacingVolumeAtScale(
        EffectBlockVolume volume,
        double scale,
        double surfaceY,
        double floorY
    ) {
        if (scale <= 0.0 || surfaceY <= floorY) {
            return 0.0;
        }
        if (volume instanceof EffectBlockVolume.Sphere sphere) {
            double radius = sphere.radius() * scale;
            return ellipsoidSlabVolume(
                radius, radius, radius, sphere.center().y(), floorY, surfaceY
            );
        }
        if (volume instanceof EffectBlockVolume.Ellipsoid ellipsoid) {
            return ellipsoidSlabVolume(
                ellipsoid.radiusX() * scale,
                ellipsoid.radiusY() * scale,
                ellipsoid.radiusZ() * scale,
                ellipsoid.center().y(),
                floorY,
                surfaceY
            );
        }
        EffectBounds bounds = volume.bounds();
        double centerY = (bounds.minY() + bounds.maxY()) * 0.5;
        double halfHeight = (bounds.maxY() - bounds.minY()) * 0.5 * scale;
        double low = Math.max(centerY - halfHeight, floorY);
        double high = Math.min(centerY + halfHeight, surfaceY);
        if (high <= low) {
            return 0.0;
        }
        if (volume instanceof EffectBlockVolume.Cylinder cylinder) {
            double radius = cylinder.radius() * scale;
            return Math.PI * radius * radius * (high - low);
        }
        return (bounds.maxX() - bounds.minX())
            * scale
            * (bounds.maxZ() - bounds.minZ())
            * scale
            * (high - low);
    }

    /** Uniformly scaled geometric volume; the shape factor is always scale cubed. */
    static double geometricVolumeAtScale(EffectBlockVolume volume, double scale) {
        return scale <= 0.0 ? 0.0 : geometricVolume(volume) * scale * scale * scale;
    }

    static double volumeBelowPlane(double radius, double centerY, double planeY) {
        return ellipsoidSlabVolume(
            radius, radius, radius, centerY, Double.NEGATIVE_INFINITY, planeY
        );
    }

    /** Inverse of {@link #pacingVolume} over one normalized front interval. */
    static double scaleForVolume(
        EffectBlockVolume volume,
        double targetVolume,
        double minScale,
        double maxScale,
        double surfaceY,
        double floorY
    ) {
        double low = Math.max(0.0, minScale);
        double high = Math.max(low, maxScale);
        if (high <= low) {
            return high;
        }
        double lowVolume = low <= 0.0
            ? 0.0
            : pacingVolumeAtScale(volume, low, surfaceY, floorY);
        if (targetVolume <= lowVolume) {
            return low;
        }
        if (targetVolume >= pacingVolumeAtScale(volume, high, surfaceY, floorY)) {
            return high;
        }
        for (int step = 0; step < 40; step++) {
            double middle = (low + high) * 0.5;
            if (pacingVolumeAtScale(volume, middle, surfaceY, floorY) < targetVolume) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) * 0.5;
    }

    /**
     * Counts intersecting sections and distinct chunk columns. Returns {@code null} as soon as
     * either cap is exceeded, so oversized authored volumes do not allocate a full metric set.
     */
    static @Nullable Metrics countMetrics(
        EffectBlockVolume volume,
        LevelHeightAccessor level,
        int sectionCap,
        int ticketCap
    ) {
        EffectBounds bounds = volume.bounds();
        int minSectionX = SectionPos.blockToSectionCoord(floor(bounds.minX()));
        int maxSectionX = SectionPos.blockToSectionCoord(floor(bounds.maxX()));
        int minSectionY = Math.max(
            SectionPos.blockToSectionCoord(floor(bounds.minY())),
            level.getMinSection()
        );
        int maxSectionY = Math.min(
            SectionPos.blockToSectionCoord(floor(bounds.maxY())),
            level.getMaxSection() - 1
        );
        int minSectionZ = SectionPos.blockToSectionCoord(floor(bounds.minZ()));
        int maxSectionZ = SectionPos.blockToSectionCoord(floor(bounds.maxZ()));
        if (minSectionY > maxSectionY) {
            return new Metrics(0, 0);
        }

        int sections = 0;
        Set<Long> tickets = new HashSet<>();
        for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
            for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
                for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
                    if (!volume.intersectsSection(sectionX, sectionY, sectionZ)) {
                        continue;
                    }
                    sections++;
                    if (sections > sectionCap) {
                        return null;
                    }
                    tickets.add(ChunkPos.asLong(sectionX, sectionZ));
                    if (tickets.size() > ticketCap) {
                        return null;
                    }
                }
            }
        }
        return new Metrics(sections, tickets.size());
    }

    /** Normalized squared distance from a section box to the volume centre. */
    static double sectionDistanceSquared(
        EffectBlockVolume volume,
        int sectionX,
        int sectionY,
        int sectionZ
    ) {
        double minX = (double)sectionX * 16.0;
        double minY = (double)sectionY * 16.0;
        double minZ = (double)sectionZ * 16.0;
        if (volume instanceof EffectBlockVolume.Sphere sphere) {
            return ellipsoidDistanceSquared(
                sphere.center().x(), sphere.center().y(), sphere.center().z(),
                sphere.radius(), sphere.radius(), sphere.radius(),
                minX, minY, minZ
            );
        }
        if (volume instanceof EffectBlockVolume.Ellipsoid ellipsoid) {
            return ellipsoidDistanceSquared(
                ellipsoid.center().x(), ellipsoid.center().y(), ellipsoid.center().z(),
                ellipsoid.radiusX(), ellipsoid.radiusY(), ellipsoid.radiusZ(),
                minX, minY, minZ
            );
        }
        if (volume instanceof EffectBlockVolume.Cylinder cylinder) {
            double radialDistance = Math.sqrt(
                Math.pow(axisMinDistance(minX, minX + 16.0, cylinder.center().x()), 2.0)
                    + Math.pow(
                        axisMinDistance(minZ, minZ + 16.0, cylinder.center().z()), 2.0
                    )
            ) / cylinder.radius();
            double centerY = (cylinder.minY() + cylinder.maxY()) * 0.5;
            double halfHeight = Math.max(
                1.0E-9, (cylinder.maxY() - cylinder.minY()) * 0.5
            );
            double vertical = axisMinDistance(minY, minY + 16.0, centerY) / halfHeight;
            return Math.max(radialDistance, vertical) * Math.max(radialDistance, vertical);
        }
        EffectBounds bounds = volume.bounds();
        double centerX = (bounds.minX() + bounds.maxX()) * 0.5;
        double centerY = (bounds.minY() + bounds.maxY()) * 0.5;
        double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5;
        double halfX = Math.max(1.0E-9, (bounds.maxX() - bounds.minX()) * 0.5);
        double halfY = Math.max(1.0E-9, (bounds.maxY() - bounds.minY()) * 0.5);
        double halfZ = Math.max(1.0E-9, (bounds.maxZ() - bounds.minZ()) * 0.5);
        double dx = axisMinDistance(minX, minX + 16.0, centerX) / halfX;
        double dy = axisMinDistance(minY, minY + 16.0, centerY) / halfY;
        double dz = axisMinDistance(minZ, minZ + 16.0, centerZ) / halfZ;
        double maximum = Math.max(dx, Math.max(dy, dz));
        return maximum * maximum;
    }

    private static double ellipsoidDistanceSquared(
        double centerX,
        double centerY,
        double centerZ,
        double radiusX,
        double radiusY,
        double radiusZ,
        double sectionMinX,
        double sectionMinY,
        double sectionMinZ
    ) {
        double dx = axisMinDistance(sectionMinX, sectionMinX + 16.0, centerX) / radiusX;
        double dy = axisMinDistance(sectionMinY, sectionMinY + 16.0, centerY) / radiusY;
        double dz = axisMinDistance(sectionMinZ, sectionMinZ + 16.0, centerZ) / radiusZ;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Distance from one interval to a coordinate, zero when the coordinate is inside it. */
    private static double axisMinDistance(double min, double max, double value) {
        if (value < min) {
            return min - value;
        }
        return value > max ? value - max : 0.0;
    }

    private static double ellipsoidSlabVolume(
        double radiusX,
        double radiusY,
        double radiusZ,
        double centerY,
        double floorY,
        double surfaceY
    ) {
        double belowSurface = ellipsoidBelowPlane(
            radiusX, radiusY, radiusZ, centerY, surfaceY
        );
        double belowFloor = ellipsoidBelowPlane(
            radiusX, radiusY, radiusZ, centerY, floorY
        );
        return Math.max(0.0, belowSurface - belowFloor);
    }

    private static double ellipsoidBelowPlane(
        double radiusX,
        double radiusY,
        double radiusZ,
        double centerY,
        double planeY
    ) {
        if (planeY == Double.POSITIVE_INFINITY) {
            return FOUR_THIRDS_PI * radiusX * radiusY * radiusZ;
        }
        if (planeY == Double.NEGATIVE_INFINITY) {
            return 0.0;
        }
        double offset = (planeY - centerY) / radiusY;
        if (offset <= -1.0) {
            return 0.0;
        }
        if (offset >= 1.0) {
            return FOUR_THIRDS_PI * radiusX * radiusY * radiusZ;
        }
        double capHeight = 1.0 + offset;
        double unitCap = Math.PI * capHeight * capHeight * (3.0 - capHeight) / 3.0;
        return unitCap * radiusX * radiusY * radiusZ;
    }

    private static int floor(double value) {
        return (int)Math.floor(value);
    }

    record Metrics(int sections, int tickets) {
    }
}
