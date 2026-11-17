package mafisher.quasar.content.orbital;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the aimed block column for the orbital-laser command.
 *
 * <p>The ray stops on a colliding block and ignores fluids. The target X and Z come from the hit
 * block; the marker and impact anchor sit on top of that block, even when a canopy or overhang
 * raises the heightmap. The destructive session samples the non-air surface separately to raise
 * its carve ceiling to the highest loaded block inside the radius. The query is command-time only,
 * outside the gameplay tick budget, and rejects an unloaded hit column.</p>
 */
public final class ContentOrbitalLaserTarget {
    private ContentOrbitalLaserTarget() {
    }

    /** One resolved target block. {@code anchorY} is the top of the aimed block. */
    public record TargetColumn(int x, int z, int anchorY) {
        /** World-space column centre on top of the aimed block. */
        public Vec3 center() {
            return new Vec3(this.x + 0.5, this.anchorY, this.z + 0.5);
        }

        /** World-space marker position, lifted just clear of the aimed block. */
        public Vec3 markerPosition() {
            return this.center().add(0.0, ContentOrbitalLaserSettings.MARKER_LIFT, 0.0);
        }

        /** Y of the aimed block. */
        public int targetBlockY() {
            return this.anchorY - 1;
        }
    }

    /**
     * Highest loaded non-air surface inside the radius, never below the minimum.
     *
     * <p>Command-time only: the scan reads loaded chunks and never loads or generates one. Block
     * columns are selected by their centre, matching the cylinder containment used by the carve
     * editor. {@code WORLD_SURFACE} includes plants, snow, and other non-motion-blocking cover, so
     * the carve ceiling clears them too.</p>
     */
    static int highestFirstFreeY(
        ServerLevel level,
        int centerX,
        int centerZ,
        int minimumTopY,
        int radius
    ) {
        Objects.requireNonNull(level, "level");
        if (radius < 0) {
            throw new IllegalArgumentException("Radius must be non-negative");
        }
        int highest = minimumTopY;
        int limit = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > limit) {
                    continue;
                }
                int x = centerX + dx;
                int z = centerZ + dz;
                if (!level.hasChunkAt(x, z)) {
                    continue;
                }
                highest = Math.max(
                    highest, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
                );
            }
        }
        return highest;
    }

    /**
     * Traces the aimed ray and returns the hit block column, or empty when the ray misses or the
     * column is unloaded.
     */
    public static Optional<TargetColumn> resolve(
        ServerLevel level,
        Vec3 origin,
        Vec3 direction,
        @Nullable Entity entity
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(direction, "direction");
        if (!isFinite(origin) || !isFinite(direction)) {
            return Optional.empty();
        }
        double length = direction.length();
        if (length < 1.0E-6) {
            return Optional.empty();
        }
        Vec3 end = origin.add(direction.scale(
            ContentOrbitalLaserSettings.MAX_TARGET_DISTANCE / length
        ));
        BlockHitResult hit = level.clip(new ClipContext(
            origin,
            end,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            entity == null ? CollisionContext.empty() : CollisionContext.of(entity)
        ));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        BlockPos hitPos = hit.getBlockPos();
        if (!level.isInWorldBounds(hitPos) || !level.hasChunkAt(hitPos)) {
            return Optional.empty();
        }
        return Optional.of(new TargetColumn(
            hitPos.getX(), hitPos.getZ(), hitPos.getY() + 1
        ));
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
