package mafisher.quasar.client.effect;

import mafisher.quasar.effect.network.StartVisualEffectPayload;
import mafisher.quasar.effect.network.StopVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateVisualEffectPayload;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-lifetime owner and narrow entry point for effect event delegates. */
public final class ClientEffectRuntime {
    private static final Object INIT_LOCK = new Object();
    private static volatile ClientVisualEffectManager manager;
    private static volatile ClientScreenCompositor screenCompositor;

    private ClientEffectRuntime() {
    }

    /**
     * Builds the renderer registry on first use. The registration event is posted lazily so the game bus is fully
     * active; posting it during client setup can run before consumer game-bus listeners are ready.
     */
    private static ClientVisualEffectManager manager() {
        ClientVisualEffectManager current = manager;
        if (current != null) {
            return current;
        }
        synchronized (INIT_LOCK) {
            if (manager == null) {
                VisualEffectRendererRegistry rendererRegistry = new VisualEffectRendererRegistry();
                rendererRegistry.registerBuiltIns();
                NeoForge.EVENT_BUS.post(new RegisterVisualEffectRenderersEvent(rendererRegistry));
                rendererRegistry.freeze();
                screenCompositor = new ClientScreenCompositor();
                manager = new ClientVisualEffectManager(rendererRegistry);
            }
            return manager;
        }
    }

    public static void start(StartVisualEffectPayload payload) {
        manager().start(payload);
    }

    public static void stop(StopVisualEffectPayload payload) {
        manager().stop(payload);
    }

    public static void update(UpdateVisualEffectPayload payload) {
        manager().update(payload);
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            manager().render(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            ClientVisualEffectManager effectManager = manager();
            ClientScreenCompositor compositor = screenCompositor;
            if (compositor != null) {
                List<ScreenEffectFrame> screenEffects = effectManager.screenEffects(event);
                compositor.render(event, effectManager, screenEffects);
            }
        }
    }

    static void onLevelUnload(ClientLevel level) {
        ClientVisualEffectManager current = manager;
        if (current != null) {
            current.onLevelUnload(level);
        }
        ClientScreenCompositor compositor = screenCompositor;
        if (compositor != null) {
            compositor.onLevelUnload();
        }
    }

    static void onResourceReload(ResourceManager resourceManager) {
        ClientVisualEffectManager current = manager;
        if (current != null) {
            current.onResourceReload(resourceManager);
        }
        ClientScreenCompositor compositor = screenCompositor;
        if (compositor != null) {
            compositor.onResourceReload();
        }
    }

    public static boolean armScreenCompositorFailureOnce() {
        ClientScreenCompositor compositor = screenCompositor;
        return compositor != null && compositor.armFailureAfterCompositionOnce();
    }
}
