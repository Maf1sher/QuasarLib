package mafisher.quasar.content;

import mafisher.quasar.api.client.RegisterScreenEffectOperationsEvent;
import mafisher.quasar.api.client.RegisterVisualEffectRenderersEvent;
import mafisher.quasar.content.blackhole.ContentBlackHoleImpulses;
import mafisher.quasar.content.blackhole.ContentBlackHoleMeshRenderer;
import mafisher.quasar.content.blackhole.ContentBlackHoleOperation;
import mafisher.quasar.content.blackhole.ContentBlackHoleRenderer;
import mafisher.quasar.content.demo.ContentBloomRenderer;
import mafisher.quasar.content.demo.ContentProbeRenderer;
import mafisher.quasar.content.demo.ContentSceneBlurRenderer;
import mafisher.quasar.content.demo.ContentWaveDistortionRenderer;
import mafisher.quasar.content.rune.ContentRuneCircleRenderer;
import mafisher.quasar.content.rune.ContentRuneGlowOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Client-only content renderer registration through the public QuasarLib SPI. */
public final class ContentClientEffects {
    private ContentClientEffects() {
    }

    public static void registerOperations(RegisterScreenEffectOperationsEvent event) {
        event.register(ContentScreenOperations.RUNE_GLOW, new ContentRuneGlowOperation());
        event.register(ContentScreenOperations.BLACK_HOLE, new ContentBlackHoleOperation());
    }

    public static void registerRenderers(RegisterVisualEffectRenderersEvent event) {
        event.register(ContentEffectIds.RUNE_CIRCLE, new ContentRuneCircleRenderer());
        event.register(ContentEffectIds.WAVE_DISTORTION, new ContentWaveDistortionRenderer());
        event.register(ContentEffectIds.SCENE_BLUR, new ContentSceneBlurRenderer());
        event.register(ContentEffectIds.BLOOM, new ContentBloomRenderer());
        event.register(ContentEffectIds.BLACK_HOLE, new ContentBlackHoleRenderer());
        event.register(ContentEffectIds.BLACK_HOLE_MESH, new ContentBlackHoleMeshRenderer());
    }

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ContentEffectEntities.PROBE.get(), ContentProbeRenderer::new);
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            ContentBlackHoleImpulses.clear();
        }
    }
}
