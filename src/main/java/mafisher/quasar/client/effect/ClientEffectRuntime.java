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
    private static volatile ClientVisualEffectManager manager;
    private static volatile ClientScreenCompositor screenCompositor;

    private ClientEffectRuntime() {
    }

    static void initialize() {
        if (manager != null) {
            return;
        }
        VisualEffectRendererRegistry rendererRegistry = new VisualEffectRendererRegistry();
        rendererRegistry.registerBuiltIns();
        NeoForge.EVENT_BUS.post(new RegisterVisualEffectRenderersEvent(rendererRegistry));
        rendererRegistry.freeze();
        manager = new ClientVisualEffectManager(rendererRegistry);
        screenCompositor = new ClientScreenCompositor();
    }

    public static void start(StartVisualEffectPayload payload) {
        ClientVisualEffectManager current = manager;
        if (current != null) {
            current.start(payload);
        }
    }

    public static void stop(StopVisualEffectPayload payload) {
        ClientVisualEffectManager current = manager;
        if (current != null) {
            current.stop(payload);
        }
    }

    public static void update(UpdateVisualEffectPayload payload) {
        ClientVisualEffectManager current = manager;
        if (current != null) {
            current.update(payload);
        }
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            ClientVisualEffectManager current = manager;
            if (current != null) {
                current.render(event);
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            ClientScreenCompositor current = screenCompositor;
            if (current != null) {
                ClientVisualEffectManager effectManager = manager;
                List<ScreenEffectFrame> screenEffects = effectManager == null
                    ? List.of()
                    : effectManager.screenEffects(event);
                current.render(event, effectManager, screenEffects);
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
