package mafisher.quasar.client.effect;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import mafisher.quasar.api.client.ClientEffectImpulses;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.compat.RenderEnvironment;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.scale.StartSegmentedVisualEffectPayload;
import mafisher.quasar.client.effect.audio.ClientEffectAudio;
import mafisher.quasar.client.effect.frame.ClientEffectClock;
import mafisher.quasar.client.effect.frame.CompatFrameEnvironment;
import mafisher.quasar.client.effect.quality.ClientFarPlane;
import mafisher.quasar.client.effect.quality.ClientQualityController;
import mafisher.quasar.client.effect.render.ClientVisualEffectManager;
import mafisher.quasar.client.effect.render.ScreenEffectBatch;
import mafisher.quasar.client.effect.screen.ClientScreenCompositor;
import mafisher.quasar.client.effect.screen.ScreenFlashRenderer;
import mafisher.quasar.effect.network.EffectAudioPlanPayload;
import mafisher.quasar.effect.network.EffectAudioTriggerPayload;
import mafisher.quasar.effect.network.EffectDebrisPayload;
import mafisher.quasar.effect.network.EffectTransformBatchPayload;
import mafisher.quasar.effect.network.ResumeEffectsPayload;
import mafisher.quasar.effect.network.StopEffectAudioPayload;
import mafisher.quasar.effect.network.StopSegmentedVisualEffectPayload;
import mafisher.quasar.effect.network.StopVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateSegmentedVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateVisualEffectPayload;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-only owner of the effect manager and screen compositor. Common payload handlers must
 * never reference this class, so a dedicated server cannot load client-only descriptors.
 */
final class ClientEffectFrameRuntime {
    private static final Object INIT_LOCK = new Object();
    private static final CompatFrameEnvironment FRAME_ENVIRONMENT =
        new CompatFrameEnvironment(CompatRegistry::environment);
    private static volatile ClientVisualEffectManager manager;
    private static volatile ClientScreenCompositor screenCompositor;

    private ClientEffectFrameRuntime() {
    }

    /**
     * Closes client registration before the first effect frame. Called from the client load-complete
     * hook; {@link #manager()} keeps an idempotent lazy fallback for tests and unusual call orders.
     */
    static void initialize() {
        ClientEffectRegistries.shared();
    }

    private static ClientVisualEffectManager manager() {
        ClientVisualEffectManager current = manager;
        if (current != null) {
            return current;
        }
        synchronized (INIT_LOCK) {
            if (manager == null) {
                ClientEffectRegistries currentRegistries = ClientEffectRegistries.shared();
                screenCompositor = new ClientScreenCompositor(currentRegistries.operations());
                manager = new ClientVisualEffectManager(currentRegistries.renderers());
            }
            return manager;
        }
    }

    static void start(StartVisualEffectPayload payload) {
        manager().start(payload);
    }

    static void resume(ResumeEffectsPayload payload) {
        ClientEffectAudio.clear();
        ClientEffectDebris.clear();
        ClientEffectClock.reset();
        manager().resume(payload);
    }

    static void stop(StopVisualEffectPayload payload) {
        manager().stop(payload);
    }

    static void update(UpdateVisualEffectPayload payload) {
        manager().update(payload);
    }

    static void updateBatch(EffectTransformBatchPayload payload) {
        ClientVisualEffectManager current = manager();
        for (EffectTransformBatchPayload.Entry update : payload.updates()) {
            if (update.segmented()) {
                current.updateSegmented(new UpdateSegmentedVisualEffectPayload(
                    update.instanceId(),
                    payload.dimensionId(),
                    update.x(),
                    update.y(),
                    update.z(),
                    update.yaw(),
                    update.pitch(),
                    update.roll(),
                    payload.updateGameTime()
                ));
            } else {
                current.update(new UpdateVisualEffectPayload(
                    update.instanceId(),
                    payload.dimensionId(),
                    update.x(),
                    update.y(),
                    update.z(),
                    update.yaw(),
                    update.pitch(),
                    update.roll(),
                    payload.updateGameTime()
                ));
            }
        }
    }

    static void startSegmented(StartSegmentedVisualEffectPayload payload) {
        manager().startSegmented(payload);
    }

    static void updateSegmented(UpdateSegmentedVisualEffectPayload payload) {
        manager().updateSegmented(payload);
    }

    static void stopSegmented(StopSegmentedVisualEffectPayload payload) {
        manager().stopSegmented(payload);
    }

    static void effectAudioPlan(EffectAudioPlanPayload payload) {
        ClientEffectAudio.accept(payload, ClientEffectFrameRuntime::effectOrigin);
    }

    static void stopEffectAudio(StopEffectAudioPayload payload) {
        ClientEffectAudio.stop(payload);
    }

    static void effectAudioTrigger(EffectAudioTriggerPayload payload) {
        ClientEffectAudio.trigger(payload);
    }

    static void effectDebris(EffectDebrisPayload payload) {
        ClientEffectDebris.accept(payload);
    }

    /** Current interpolated world origin for one effect, or null when it has no visual instance. */
    static Vec3 effectOrigin(UUID effectId) {
        ClientVisualEffectManager current = manager;
        return current == null ? null : current.effectOrigin(effectId);
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            ClientEffectDebris.render(event);
            ClientScreenCompositor compositor = screenCompositor;
            ClientVisualEffectManager effectManager = manager;
            if (compositor != null && effectManager != null) {
                compositor.captureOpaqueDepth(event, effectManager);
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            ClientScreenCompositor compositor = screenCompositor;
            ClientVisualEffectManager effectManager = manager;
            if (compositor != null && effectManager != null) {
                compositor.captureForegroundDepth(event, effectManager);
            }
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            long start = System.nanoTime();
            ClientVisualEffectManager effectManager = manager();
            RenderEnvironment environment = FRAME_ENVIRONMENT.beginFrame(currentFarPlane());
            effectManager.render(event, environment);
            ClientQualityController.recordWorldRender(System.nanoTime() - start);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            long start = System.nanoTime();
            try {
                RenderEnvironment environment = FRAME_ENVIRONMENT.current(currentFarPlane());
                ClientVisualEffectManager effectManager = manager();
                ClientScreenCompositor compositor = screenCompositor;
                if (compositor != null) {
                    List<ScreenEffectBatch> batches = effectManager.screenEffects(event, environment);
                    compositor.render(event, effectManager, batches, environment);
                }
                ScreenFlashRenderer.render(
                    event.getCamera().getPosition(),
                    (float) event.getPartialTick().getGameTimeDeltaPartialTick(false)
                );
            } finally {
                FRAME_ENVIRONMENT.endFrame();
                ClientQualityController.endFrameGpu();
            }
            ClientQualityController.recordLevelRender(System.nanoTime() - start);
        }
    }

    /** Resolves the far plane once so both client stages share the same frame environment. */
    private static double currentFarPlane() {
        return ClientFarPlane.current();
    }

    static void onLevelUnload(ClientLevel level) {
        ClientVisualEffectManager current = manager;
        if (current != null) {
            current.onLevelUnload(level);
        }
        ClientQualityController.reset();
        ClientEffectClock.reset();
        ClientEffectAudio.clear();
        ClientEffectDebris.clear();
        ClientEffectImpulses.clear();
        FRAME_ENVIRONMENT.endFrame();
        ScreenFlashRenderer.onResourceReload();
        ClientScreenCompositor compositor = screenCompositor;
        if (compositor != null) {
            compositor.onLevelUnload();
        }
    }

    /** Drops all client presentation state when the player leaves a server. */
    static void onDisconnect() {
        ClientVisualEffectManager current = manager;
        if (current != null) {
            current.clear();
        }
        ClientQualityController.reset();
        ClientEffectClock.reset();
        ClientEffectAudio.clear();
        ClientEffectDebris.clear();
        ClientEffectImpulses.clear();
        FRAME_ENVIRONMENT.endFrame();
        ScreenFlashRenderer.onResourceReload();
        ClientScreenCompositor compositor = screenCompositor;
        if (compositor != null) {
            compositor.onLevelUnload();
        }
    }

    static void onResourceReload(ResourceManager resourceManager, Consumer<Runnable> dispatcher) {
        dispatcher.accept(() -> applyResourceReload(resourceManager));
    }

    private static void applyResourceReload(ResourceManager resourceManager) {
        ClientVisualEffectManager current = manager;
        if (current != null) {
            current.onResourceReload(resourceManager);
        }
        ClientEffectDebris.onResourceReload();
        ScreenFlashRenderer.onResourceReload();
        ClientScreenCompositor compositor = screenCompositor;
        if (compositor != null) {
            compositor.onResourceReload();
        }
    }

    /** Logs one composed frame of presentation GL state; the next composed frame clears the request. */
    static boolean requestCompositorDiagnostics() {
        if (manager == null) {
            manager();
        }
        ClientScreenCompositor compositor = screenCompositor;
        return compositor != null && compositor.requestDiagnostics();
    }
}
