package mafisher.quasar.api.gameplay.block;

import java.util.Objects;
import mafisher.quasar.api.effect.EffectBounds;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Authored input for one budgeted terrain carve.
 *
 * <p>The volume is the only source of the requested crater or shaft size. A plan may shrink it
 * uniformly to fit the effect timeline and the engine section or chunk-ticket caps; it never
 * enlarges it. An optional core volume is carved exactly with a block cursor before the bulk pass,
 * so a small sphere or shaft still has a clean interior. The core is scaled by the same factor as
 * the main volume.</p>
 *
 * <p>{@code surfaceY} is the sampled terrain surface used to pace the visible front by removable
 * volume instead of bounding-box volume. When it is absent, the whole volume is treated as solid.
 * The warp never samples or loads a chunk; callers that need a surface sample supply it.</p>
 */
public record EffectCarveSpec(
    EffectBlockVolume volume,
    BlockState replacement,
    boolean removeBlockEntities,
    boolean journal,
    EffectBulkBlockEditor.SyncPolicy syncPolicy,
    float windowStart,
    float windowEnd,
    @Nullable EffectBlockVolume coreVolume,
    float coreStartProgress,
    @Nullable Double surfaceY
) {
    public EffectCarveSpec {
        Objects.requireNonNull(volume, "volume");
        Objects.requireNonNull(replacement, "replacement");
        syncPolicy = syncPolicy == null ? EffectBulkBlockEditor.SyncPolicy.ON_COMPLETE : syncPolicy;
        requireUnitProgress(windowStart, "window start");
        requireUnitProgress(windowEnd, "window end");
        if (windowStart >= windowEnd) {
            throw new IllegalArgumentException("Carve window start must be before its end");
        }
        requireUnitProgress(coreStartProgress, "core start progress");
        if (surfaceY != null && !Double.isFinite(surfaceY)) {
            throw new IllegalArgumentException("Carve surface height must be finite");
        }
        if (coreVolume != null && !contains(volume.bounds(), coreVolume.bounds())) {
            throw new IllegalArgumentException("Core volume must be contained in the carve volume");
        }
    }

    /** A default progressive air carve over the whole effect timeline. */
    public static EffectCarveSpec air(EffectBlockVolume volume) {
        return new EffectCarveSpec(
            volume,
            Blocks.AIR.defaultBlockState(),
            true,
            true,
            EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
            0.0F,
            1.0F,
            null,
            0.0F,
            null
        );
    }

    /** Whether this carve has an exact core pre-pass. */
    public boolean hasCore() {
        return this.coreVolume != null;
    }

    private static boolean contains(EffectBounds outer, EffectBounds inner) {
        double epsilon = 1.0E-6;
        if (inner.minX() < outer.minX() - epsilon || inner.maxX() > outer.maxX() + epsilon) {
            return false;
        }
        if (inner.minY() < outer.minY() - epsilon || inner.maxY() > outer.maxY() + epsilon) {
            return false;
        }
        return inner.minZ() >= outer.minZ() - epsilon
            && inner.maxZ() <= outer.maxZ() + epsilon;
    }

    private static void requireUnitProgress(float value, String label) {
        if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
            throw new IllegalArgumentException(label + " must be finite and inside [0, 1]");
        }
    }
}
