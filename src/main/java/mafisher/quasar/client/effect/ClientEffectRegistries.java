package mafisher.quasar.client.effect;

import mafisher.quasar.api.client.screen.RegisterScreenEffectOperationsEvent;
import mafisher.quasar.api.client.render.RegisterVisualEffectRenderersEvent;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.compat.RegisterCompatProvidersEvent;
import mafisher.quasar.client.effect.render.VisualEffectRendererRegistry;
import mafisher.quasar.client.effect.screen.ScreenEffectOperationRegistry;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client registry setup for renderers, screen operations, and compatibility providers.
 *
 * <p>Initialization is intentionally lazy and runs on the first effect frame (or first start
 * payload). At that point every consumer game-bus listener has been registered by its mod
 * constructor, so the registration events cannot close an empty registry. Do not move this back
 * to client load completion: that ordering dropped content renderers such as the black hole.</p>
 */
final class ClientEffectRegistries {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEffectRegistries.class);
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
        LOGGER.info(
            "Initialized client effect registries with {} renderer(s) and {} screen operation(s)",
            rendererRegistry.size(),
            operationRegistry.size()
        );
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
