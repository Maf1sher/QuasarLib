package mafisher.quasar.api.client.particle;

import java.util.List;
import java.util.Objects;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderPass;
import mafisher.quasar.api.client.render.VisualEffectRenderer;

/**
 * Optional visual-effect renderer capability for stateless GPU micro-particles.
 *
 * <p>The engine draws each declared field with one instanced pass. Particle state is
 * computed in the vertex shader from the effect seed, the effect age, and the particle
 * instance index; no per-particle CPU buffer exists. The engine applies the quality
 * tier and the global client particle budget to the requested count.</p>
 *
 * <p>A particle-only renderer may leave {@link #renderPasses} empty. A renderer that
 * also declares regular, static, or instanced passes overrides the relevant methods
 * and the engine draws every declared family.</p>
 */
public interface ParticleVisualEffectRenderer extends VisualEffectRenderer {
    /**
     * Returns the stateless particle fields for this visible instance or segment cell.
     * The default empty list keeps the regular render path. The list must not contain
     * duplicate pass IDs for one context.
     */
    default List<EffectParticlePass> particlePasses(EffectRenderContext context) {
        Objects.requireNonNull(context, "context");
        return List.of();
    }

    /** A particle-only renderer declares no regular render passes. */
    @Override
    default List<EffectRenderPass> renderPasses(EffectRenderContext context) {
        Objects.requireNonNull(context, "context");
        return List.of();
    }
}
