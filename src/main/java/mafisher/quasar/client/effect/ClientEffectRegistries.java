package mafisher.quasar.client.effect;

import mafisher.quasar.api.client.screen.RegisterScreenEffectOperationsEvent;
import mafisher.quasar.api.client.render.RegisterVisualEffectRenderersEvent;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.compat.RegisterCompatProvidersEvent;
import mafisher.quasar.client.effect.render.VisualEffectRendererRegistry;
import mafisher.quasar.client.effect.screen.ScreenEffectOperationRegistry;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client registry setup shared by the load-complete hook and the lazy frame fallback.
 *
 * <p>Registration events are posted once, then every registry closes. Running this during
 * client load completion guarantees that consumer game-bus listeners are present before the
 * first effect frame.</p>
 */
final class ClientEffectRegistries {
    private static final Object LOCK = new Object();
    private static volatile ClientEffectRegistries shared;

    private final ScreenEffectOperationRegistry operations;
    private final VisualEffectRendererRegistry renderers;

    private ClientEffectRegistries(ScreenEffectOperationRegistry operations,
        VisualEffectRendererRegistry renderers) {
        this.operations = operations;
        this.renderers = renderers;
    }

    /** Returns the client-wide registries, initializing them once. */
    static ClientEffectRegistries shared() {
        ClientEffectRegistries current = shared;
        if (current != null) {
            return current;
        }
        synchronized (LOCK) {
            if (shared == null) {
                shared = initialize();
            }
            return shared;
        }
    }

    static ClientEffectRegistries initialize() {
        ScreenEffectOperationRegistry operationRegistry = new ScreenEffectOperationRegistry();
        operationRegistry.registerBuiltIns();
        NeoForge.EVENT_BUS.post(new RegisterScreenEffectOperationsEvent(operationRegistry::register));
        operationRegistry.freeze();
        VisualEffectRendererRegistry rendererRegistry = new VisualEffectRendererRegistry();
        NeoForge.EVENT_BUS.post(new RegisterVisualEffectRenderersEvent(rendererRegistry::register));
        rendererRegistry.freeze();
        try {
            NeoForge.EVENT_BUS.post(new RegisterCompatProvidersEvent());
        } finally {
            CompatRegistry.freezeClientProviders();
        }
        return new ClientEffectRegistries(operationRegistry, rendererRegistry);
    }

    ScreenEffectOperationRegistry operations() {
        return this.operations;
    }

    VisualEffectRendererRegistry renderers() {
        return this.renderers;
    }
}
