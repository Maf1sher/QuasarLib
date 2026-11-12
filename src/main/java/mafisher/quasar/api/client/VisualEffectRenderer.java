package mafisher.quasar.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.AABB;

/**
 * Client-only renderer for one registered transient visual effect type. The pose stack is positioned and oriented
 * at the effect's interpolated world transform before this callback.
 */
public interface VisualEffectRenderer {
    String LEGACY_PASS_ID = "legacy";

    /**
     * Declares the ordered passes for this effect at the current point in its timeline. The default adapts the legacy
     * single-pass API.
     */
    default List<EffectRenderPass> renderPasses(EffectRenderContext context) {
        Objects.requireNonNull(context, "context");
        return List.of(new EffectRenderPass(LEGACY_PASS_ID, this.renderType()));
    }

    /** Renders one declared pass. The default adapts the legacy single-pass API. */
    default void render(EffectRenderContext context, EffectRenderPass pass, PoseStack poseStack,
        MultiBufferSource bufferSource) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(pass, "pass");
        if (!LEGACY_PASS_ID.equals(pass.id())) {
            throw new IllegalArgumentException("The legacy renderer only supports its single render pass");
        }
        this.render(context.effect(), context.ageTicks(), context.progress(), poseStack, bufferSource);
    }

    /**
     * Returns a conservative radius in blocks around the interpolated transform position that contains every pass
     * this renderer can emit at any age. The client spatial index uses it as a broad phase before exact frustum
     * culling. The value must not shrink with age; a renderer whose silhouette grows must return the largest radius it
     * can reach. Returning {@link Double#POSITIVE_INFINITY} keeps the effect out of the index and always considered.
     *
     * <p>The default is infinite so existing renderers remain correct without changes.
     */
    default double spatialRadius(EffectRenderContext context) {
        Objects.requireNonNull(context, "context");
        return Double.POSITIVE_INFINITY;
    }

    /**
     * Declares ordered level-of-detail distance bands for this renderer. Thresholds are surface gaps in multiples of
     * {@link #spatialRadius(EffectRenderContext)}; the engine selects a level per instance and, for segmented
     * coordinators, per visible grid cell. The default keeps full detail, so existing renderers are unchanged.
     *
     * <p>LOD requires a finite spatial radius. A renderer with the default infinite radius always receives level
     * {@code 0}.
     */
    default EffectLodProfile lodProfile() {
        return EffectLodProfile.FULL;
    }

    /**
     * Returns a conservative world-space bound for culling. The default preserves the old scale-based bound.
     */
    default AABB cullingBounds(EffectRenderContext context) {
        double radius = context.scale();
        double x = context.transform().position().x();
        double y = context.transform().position().y();
        double z = context.transform().position().z();
        return new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    /** Legacy single-pass callback, retained as an adapter for existing renderers. */
    @Deprecated
    default void render(VisualEffectInstance effect, float ageTicks, float progress, PoseStack poseStack,
        MultiBufferSource bufferSource) {
        throw new UnsupportedOperationException("Renderer must implement the context or legacy render callback");
    }

    /** Legacy single-pass render type, retained as an adapter for existing renderers. */
    @Deprecated
    default RenderType renderType() {
        throw new UnsupportedOperationException("Renderer must implement renderPasses or the legacy render type");
    }

    /** Refreshes any renderer-owned resources after Minecraft applies a client resource reload. */
    default void onResourceReload(ResourceManager resourceManager) {
    }
}
