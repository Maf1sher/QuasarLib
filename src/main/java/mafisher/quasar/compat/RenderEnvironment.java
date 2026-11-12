package mafisher.quasar.compat;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable per-frame view of the active compatibility stack.
 *
 * <p>Resolved once per client frame through {@link CompatRegistry}; renderers and
 * screen operations read it through their contexts. A missing provider never
 * fails an effect: it only limits range, depth accuracy, or physics interaction.</p>
 */
public record RenderEnvironment(
    double horizonDistance,
    double farPlane,
    boolean horizonProvided,
    SceneDepthSource depthSource,
    ShaderPackState shaderPackState,
    FogPolicy fogPolicy,
    boolean physicsAvailable,
    boolean protectionAvailable,
    List<ResourceLocation> activeProviders
) {
    /** Fallback environment used when no frame is being rendered. */
    public static final RenderEnvironment VANILLA = new RenderEnvironment(
        256.0,
        256.0,
        false,
        SceneDepthSource.MAIN_TARGET,
        ShaderPackState.INACTIVE,
        FogPolicy.RESPECT,
        false,
        false,
        List.of()
    );

    public RenderEnvironment {
        if (!Double.isFinite(horizonDistance) || horizonDistance <= 0.0
            || !Double.isFinite(farPlane) || farPlane <= 0.0) {
            throw new IllegalArgumentException("Horizon distance and far plane must be finite and positive");
        }
        Objects.requireNonNull(depthSource, "depthSource");
        Objects.requireNonNull(shaderPackState, "shaderPackState");
        Objects.requireNonNull(fogPolicy, "fogPolicy");
        activeProviders = List.copyOf(Objects.requireNonNull(activeProviders, "activeProviders"));
    }

    /** Whether an external shaderpack renders the final frame. */
    public boolean shaderPackActive() {
        return this.shaderPackState == ShaderPackState.ACTIVE;
    }

    /** Whether a shaderpack shadow pass is running; composition must be skipped. */
    public boolean shadowPass() {
        return this.shaderPackState == ShaderPackState.SHADOW_PASS;
    }

    /** Screen composition is allowed outside shaderpack shadow passes. */
    public boolean screenCompositionAllowed() {
        return !this.shadowPass();
    }

    /** Whether depth comes from a provider rather than the vanilla main target. */
    public boolean hasDepthProvider() {
        return this.depthSource != SceneDepthSource.MAIN_TARGET;
    }

    /** Whether the frame uses a horizon provider rather than the vanilla far plane. */
    public boolean hasHorizonProvider() {
        return this.horizonProvided;
    }
}
