package mafisher.quasar.client.effect.screen;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import mafisher.quasar.api.client.ClientEffectImpulses;
import mafisher.quasar.client.effect.render.EffectRenderState;
import mafisher.quasar.client.effect.render.EffectShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.ApiStatus;

/** Draws active additive screen flashes over the composed level target. */
@ApiStatus.Internal
public final class ScreenFlashRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScreenFlashRenderer.class);
    private static volatile boolean unavailable;

    private ScreenFlashRenderer() {
    }

    public static void render(Vec3 cameraPosition, float partialTick) {
        if (unavailable || !ClientEffectImpulses.hasFlash()) {
            return;
        }
        ClientEffectImpulses.FlashSample sample =
            ClientEffectImpulses.sampleFlash(cameraPosition, partialTick);
        if (sample.strength() <= 0.0F) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ShaderInstance shader = EffectShaders.SCREEN_FLASH.instance().orElse(null);
        if (shader == null) {
            return;
        }
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        ScreenGlState state = ScreenGlState.capture();
        boolean shaderApplied = false;
        try {
            EffectRenderState.bindTarget(mainTarget);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(false);
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            shader.safeGetUniform("FlashColor")
                .set(sample.red(), sample.green(), sample.blue());
            shader.safeGetUniform("FlashStrength").set(sample.strength());
            shader.apply();
            shaderApplied = true;
            BufferBuilder builder = RenderSystem.renderThreadTesselator()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.addVertex(0.0F, 0.0F, 0.0F);
            builder.addVertex(1.0F, 0.0F, 0.0F);
            builder.addVertex(1.0F, 1.0F, 0.0F);
            builder.addVertex(0.0F, 1.0F, 0.0F);
            BufferUploader.draw(builder.buildOrThrow());
        } catch (RuntimeException exception) {
            unavailable = true;
            LOGGER.warn("Screen flash rendering is unavailable; continuing without it.", exception);
        } finally {
            if (shaderApplied) {
                shader.clear();
            }
            state.restore();
        }
    }

    public static void onResourceReload() {
        unavailable = false;
    }
}
