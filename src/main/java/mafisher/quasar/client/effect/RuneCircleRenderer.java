package mafisher.quasar.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.EffectTimeline;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Textured, layered sample renderer that also serves as a debug test for world-space VFX. */
final class RuneCircleRenderer implements ScreenEffectRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        QuasarLib.MOD_ID, "textures/effect/rune_circle.png"
    );
    private static final RenderType RENDER_TYPE = EffectRenderTypes.debugVisual(TEXTURE);
    private static final RenderType SCREEN_MASK_RENDER_TYPE = EffectRenderTypes.screenMask(TEXTURE);
    private static final String OUTER_PASS = "outer";
    private static final String MIDDLE_PASS = "middle";
    private static final String INNER_PASS = "inner";
    private static final List<EffectRenderPass> RENDER_PASSES = List.of(
        new EffectRenderPass(OUTER_PASS, RENDER_TYPE),
        new EffectRenderPass(MIDDLE_PASS, RENDER_TYPE),
        new EffectRenderPass(INNER_PASS, RENDER_TYPE)
    );

    @Override
    public List<EffectRenderPass> renderPasses(EffectRenderContext context) {
        return RENDER_PASSES;
    }

    @Override
    public void render(
        EffectRenderContext context,
        EffectRenderPass pass,
        PoseStack poseStack,
        MultiBufferSource bufferSource
    ) {
        float entrance = EffectTimeline.easeOutCubic(Math.min(1.0F, context.progress() / 0.15F));
        float alpha = EffectTimeline.fadeEnvelope(context.progress(), 0.10F, 0.25F);
        float worldScale = context.scale() * entrance;
        int shaderData = shaderData(context);
        VertexConsumer vertices = bufferSource.getBuffer(RENDER_TYPE);

        switch (pass.id()) {
            case OUTER_PASS -> renderLayer(poseStack, vertices, worldScale,
                context.ageTicks() * 0.035F, alpha, 0.30F, shaderData);
            case MIDDLE_PASS -> renderLayer(poseStack, vertices, worldScale * 0.72F,
                -context.ageTicks() * 0.052F, alpha, 0.20F, shaderData);
            case INNER_PASS -> renderLayer(poseStack, vertices, worldScale * 0.43F,
                context.ageTicks() * 0.075F, alpha, 0.10F, shaderData);
            default -> throw new IllegalArgumentException("Unknown rune circle render pass: " + pass.id());
        }
    }

    @Override
    public AABB cullingBounds(EffectRenderContext context) {
        double maximumHeight = 0.30;
        double radius = Math.sqrt(2.0 * context.scale() * context.scale() + maximumHeight * maximumHeight);
        double x = context.transform().position().x();
        double y = context.transform().position().y();
        double z = context.transform().position().z();
        return new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    @Override
    public ScreenEffectSettings screenEffectSettings(EffectRenderContext context) {
        return ScreenEffectSettings.DIAGNOSTIC_PREVIEW;
    }

    @Override
    public ScreenEffectMask screenEffectMask(EffectRenderContext context) {
        int color = Long.hashCode(context.seed());
        float red = 0.25F + ((color >>> 16) & 0xFF) / 255.0F * 0.65F;
        float green = 0.25F + ((color >>> 8) & 0xFF) / 255.0F * 0.65F;
        float blue = 0.25F + (color & 0xFF) / 255.0F * 0.65F;
        return new ScreenEffectMask(red, green, blue, 0.45F);
    }

    @Override
    public RenderType screenMaskRenderType(EffectRenderContext context) {
        return SCREEN_MASK_RENDER_TYPE;
    }

    @Override
    public void renderScreenMask(EffectRenderContext context, RenderType maskRenderType, PoseStack poseStack,
        MultiBufferSource bufferSource) {
        float entrance = EffectTimeline.easeOutCubic(Math.min(1.0F, context.progress() / 0.15F));
        float alpha = EffectTimeline.fadeEnvelope(context.progress(), 0.10F, 0.25F);
        float worldScale = context.scale() * entrance;
        VertexConsumer vertices = bufferSource.getBuffer(maskRenderType);

        renderLayer(poseStack, vertices, worldScale, context.ageTicks() * 0.035F, alpha, 0.30F, 0);
        renderLayer(poseStack, vertices, worldScale * 0.72F, -context.ageTicks() * 0.052F, alpha, 0.20F, 0);
        renderLayer(poseStack, vertices, worldScale * 0.43F, context.ageTicks() * 0.075F, alpha, 0.10F, 0);
    }

    private static void renderLayer(
        PoseStack poseStack,
        VertexConsumer vertices,
        float scale,
        float rotation,
        float alpha,
        float height,
        int shaderData
    ) {
        poseStack.pushPose();
        poseStack.translate(0.0, height, 0.0);
        poseStack.mulPose(Axis.YP.rotation(rotation));
        poseStack.scale(scale, 1.0F, scale);
        EffectMeshes.horizontalQuad().emit(vertices, poseStack.last(), 0.45F, 0.92F, 1.0F,
            alpha, shaderData, 0x00F000F0);
        poseStack.popPose();
    }

    /** Packs per-effect shader inputs into the overlay attribute unused by this renderer. */
    private static int shaderData(EffectRenderContext context) {
        int progress = Math.round(context.progress() * 32767.0F);
        int seed = Math.floorMod(Long.hashCode(context.seed()), 32768);
        return OverlayTexture.pack(progress, seed);
    }
}
