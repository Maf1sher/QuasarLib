package mafisher.quasar.api.client.render;

import java.util.List;

/**
 * Optional visual-effect renderer capability for static GPU-cached world passes. The engine uploads each mesh once
 * and later draws it from a {@link com.mojang.blaze3d.vertex.VertexBuffer}, avoiding per-frame CPU vertex emission
 * for heavy geometry such as high-segment spheres.
 *
 * <p>Cached passes are submitted before the renderer's CPU
 * {@link VisualEffectRenderer#renderPasses(EffectRenderContext)} passes, so opaque depth-writing geometry can
 * precede translucent CPU passes. Compatible passes whose render type has an instanced twin (see
 * {@link EffectShader#createBatched}) batch automatically into ordered instanced runs; other passes keep one cached
 * draw. Mesh, tint, overlay, and light stay static; the batch path carries tint through the instance payload. See
 * {@link EffectStaticMeshPass}.</p>
 */
public interface StaticMeshVisualEffectRenderer extends VisualEffectRenderer {
    /**
     * Returns the static cached world passes for this visible instance or segment cell. The default empty list keeps
     * the CPU render path. The list must not contain duplicate pass IDs for one context.
     */
    default List<EffectStaticMeshPass> staticMeshPasses(EffectRenderContext context) {
        return List.of();
    }
}
