package mafisher.quasar.content.demo;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderPass;
import mafisher.quasar.api.client.render.EffectRibbon;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import mafisher.quasar.api.client.render.VisualEffectRenderer;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.content.ContentBootstrap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Content-owned camera-facing ribbon acceptance demo.
 *
 * <p>The renderer rebuilds one deterministic helix per render call on a shared scratch ribbon. It
 * keeps no per-instance state, which is the required shape for a renderer shared by every instance
 * of one effect ID.</p>
 */
public final class ContentRibbonRenderer implements VisualEffectRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "textures/effect/rune_circle.png"
    );
    private static final String PASS_ID = "ribbon-test";
    private static final int POINTS = 96;
    private static final float WIDTH_SCALE = 0.16F;
    private static final RenderType RENDER_TYPE = QuasarRenderTypes.worldVisual(TEXTURE);
    private static final List<EffectRenderPass> RENDER_PASSES = List.of(
        new EffectRenderPass(PASS_ID, RENDER_TYPE)
    );
    private static final EffectRibbon RIBBON = EffectRibbon.builder()
        .maxPoints(POINTS)
        .subdivisions(4)
        .taper(WIDTH_SCALE, WIDTH_SCALE * 0.12F)
        .build();

    @Override
    public List<EffectRenderPass> renderPasses(EffectRenderContext context) {
        return RENDER_PASSES;
    }

    @Override
    public void render(EffectRenderContext context, EffectRenderPass pass, PoseStack poseStack,
        MultiBufferSource bufferSource) {
        if (!PASS_ID.equals(pass.id())) {
            throw new IllegalArgumentException("Unknown ribbon render pass: " + pass.id());
        }
        float alpha = EffectTimeline.fadeEnvelope(context.progress(), 0.08F, 0.15F);
        if (alpha <= 0.0F) {
            return;
        }
        buildHelix(context);
        poseStack.pushPose();
        try {
            poseStack.scale(context.scale(), context.scale(), context.scale());
            RIBBON.emit(bufferSource.getBuffer(pass.renderType()), poseStack.last(),
                0.72F, 0.92F, 1.0F, alpha, OverlayTexture.NO_OVERLAY, 0x00F000F0);
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    public AABB cullingBounds(EffectRenderContext context) {
        double radius = spatialRadius(context);
        double x = context.transform().position().x();
        double y = context.transform().position().y();
        double z = context.transform().position().z();
        return new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    @Override
    public double spatialRadius(EffectRenderContext context) {
        return context.scale() * 1.35;
    }

    private static void buildHelix(EffectRenderContext context) {
        RIBBON.reset();
        double age = context.ageTicks();
        for (int point = 0; point < POINTS; point++) {
            float progress = (float)point / (POINTS - 1);
            double angle = progress * Math.PI * 2.5 + age * 0.04;
            double radius = 0.45 + 0.15 * Math.sin(progress * Math.PI);
            RIBBON.addPoint(Math.cos(angle) * radius, (progress - 0.5) * 1.1,
                Math.sin(angle) * radius);
        }
    }
}
