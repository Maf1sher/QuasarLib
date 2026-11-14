package mafisher.quasar.api.client.render;

import java.util.Objects;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Per-renderer policy for provider-depth occlusion.
 *
 * <p>The engine may skip every pass of an effect when the active scene depth proves that its
 * conservative world-space bounds are completely hidden. The test never changes gameplay or the
 * effect lifetime; it is a presentation optimization with a safe draw fallback.</p>
 *
 * <p>{@link #AUTO} uses {@link VisualEffectRenderer#cullingBounds(EffectRenderContext)}. A
 * renderer that emits visible screen-space work outside those bounds, or that deliberately draws
 * through terrain, must return {@link #DISABLED} or declare enclosing bounds with
 * {@link #bounds(AABB)}.</p>
 */
public record EffectDepthOcclusion(boolean enabled, @Nullable AABB bounds) {
    /** Use the renderer's culling bounds and allow the engine to skip fully occluded passes. */
    public static final EffectDepthOcclusion AUTO = new EffectDepthOcclusion(true, null);

    /** Never skip this renderer, even when the world-space bounds are occluded. */
    public static final EffectDepthOcclusion DISABLED = new EffectDepthOcclusion(false, null);

    public EffectDepthOcclusion {
        if (!enabled && bounds != null) {
            throw new IllegalArgumentException("Disabled depth occlusion must not declare bounds");
        }
        if (bounds != null) {
            requireFinite(bounds.minX, "minX");
            requireFinite(bounds.minY, "minY");
            requireFinite(bounds.minZ, "minZ");
            requireFinite(bounds.maxX, "maxX");
            requireFinite(bounds.maxY, "maxY");
            requireFinite(bounds.maxZ, "maxZ");
            if (bounds.minX > bounds.maxX || bounds.minY > bounds.maxY || bounds.minZ > bounds.maxZ) {
                throw new IllegalArgumentException("Depth occlusion bounds must be ordered");
            }
        }
    }

    /**
     * Declares explicit conservative bounds instead of using the renderer's culling bounds.
     * The bounds must enclose every visible pass, including screen-space lensing or warping.
     */
    public static EffectDepthOcclusion bounds(AABB bounds) {
        return new EffectDepthOcclusion(true, Objects.requireNonNull(bounds, "bounds"));
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Depth occlusion " + name + " must be finite");
        }
    }
}
