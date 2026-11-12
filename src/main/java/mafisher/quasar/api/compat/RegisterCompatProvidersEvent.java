package mafisher.quasar.api.compat;

import java.util.Objects;
import net.neoforged.bus.api.Event;

/**
 * Fired once on the client before the first effect frame so consumers can
 * register optional capability providers.
 *
 * <p>Handlers run on the client setup/render thread and must only call the
 * {@code register*} methods. Registration closes after the event, and duplicate
 * provider IDs throw.</p>
 */
public final class RegisterCompatProvidersEvent extends Event {
    /** Registers a scene-depth provider. */
    public void registerSceneDepthProvider(SceneDepthProvider provider) {
        CompatRegistry.registerSceneDepthProvider(Objects.requireNonNull(provider, "provider"));
    }

    /** Registers the single active horizon provider. */
    public void registerHorizonProvider(HorizonProvider provider) {
        CompatRegistry.registerHorizonProvider(Objects.requireNonNull(provider, "provider"));
    }

    /** Registers a shaderpack-state provider. */
    public void registerShaderPackProvider(ShaderPackProvider provider) {
        CompatRegistry.registerShaderPackProvider(Objects.requireNonNull(provider, "provider"));
    }
}
