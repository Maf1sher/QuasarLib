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
 * Resolves the surface column under an aimed ray for the orbital-laser command.
 *
 * <p>The ray stops on a colliding block and ignores fluids. The target X and Z come from the hit
 * block; the marker Y comes from the motion-blocking heightmap so the locked column is the surface
 * a sky beam would actually reach. The query is command-time only, outside the gameplay tick
 * budget, and rejects an unloaded hit column.</p>
 */
public final class ContentOrbitalLaserTarget {
    private ContentOrbitalLaserTarget() {
    }

    /** One resolved target column. {@code firstFreeY} is the same value the heightmap returns. */
    public record TargetColumn(int x, int z, int firstFreeY) {
        /** World-space column centre at the first free Y above the surface. */
        public Vec3 center() {
            return new Vec3(this.x + 0.5, this.firstFreeY, this.z + 0.5);
        }

        /** World-space marker position, lifted just clear of the surface. */
        public Vec3 markerPosition() {
            return this.center().add(0.0, ContentOrbitalLaserSettings.MARKER_LIFT, 0.0);
        }

        /** Y of the top blocking block in the column. */
        public int surfaceBlockY() {
            return this.firstFreeY - 1;
        }
    }

    /**
     * Traces the aimed ray and returns the surface column, or empty when the ray misses, the
     * column is unloaded, or the heightmap has no safe surface.
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
        int firstFreeY = level.getHeight(
            Heightmap.Types.MOTION_BLOCKING, hitPos.getX(), hitPos.getZ()
        );
        if (firstFreeY <= level.getMinBuildHeight() || firstFreeY <= hitPos.getY()) {
            return Optional.empty();
        }
        return Optional.of(new TargetColumn(hitPos.getX(), hitPos.getZ(), firstFreeY));
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
