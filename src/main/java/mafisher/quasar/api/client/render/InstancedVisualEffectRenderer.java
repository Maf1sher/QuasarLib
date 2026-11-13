package mafisher.quasar.api.client.render;

import java.util.List;

/**
 * Optional visual-effect renderer capability for GPU-instanced world passes. When
 * {@link #instancedPasses(EffectRenderContext)} returns a non-empty list, the client renders those passes instead
 * of the CPU {@link VisualEffectRenderer#renderPasses(EffectRenderContext)} for that context. Visible instances and
 * segmented grid cells that return the same pass ID, render type, and mesh are batched into one instanced draw.
 *
 * <p>Instanced passes require a shader that consumes the engine instance arrays. See
 * {@link QuasarRenderTypes#instancedWorldVisual(net.minecraft.resources.ResourceLocation)} and
 * {@link EffectInstancedPass}.</p>
 */
public interface InstancedVisualEffectRenderer extends VisualEffectRenderer {
    /**
     * Returns the GPU-instanced passes for this visible instance or segment cell. The default empty list keeps the
     * CPU render path. The list must not contain duplicate pass IDs for one context.
     */
    default List<EffectInstancedPass> instancedPasses(EffectRenderContext context) {
        return List.of();
    }
}
