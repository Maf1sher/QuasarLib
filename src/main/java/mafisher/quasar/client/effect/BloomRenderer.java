package mafisher.quasar.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.EffectTimeline;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Standalone debug visual for validating mask-scoped scene bloom. */
final class BloomRenderer implements ScreenEffectRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        QuasarLib.MOD_ID, "textures/effect/rune_circle.png"
    );
    private static final String PASS_ID = "bloom-test";
    private static final RenderType WORLD_RENDER_TYPE = QuasarRenderTypes.worldVisual(TEXTURE);
    private static final RenderType SCREEN_MASK_RENDER_TYPE = QuasarRenderTypes.screenEffectMask(TEXTURE);
    private static final List<EffectRenderPass> RENDER_PASSES = List.of(
        new EffectRenderPass(PASS_ID, WORLD_RENDER_TYPE)
    );

    @Override
    public List<EffectRenderPass> renderPasses(EffectRenderContext context) {
        return RENDER_PASSES;
    }

    @Override
    public void render(EffectRenderContext context, EffectRenderPass pass, PoseStack poseStack,
        MultiBufferSource bufferSource) {
        if (!PASS_ID.equals(pass.id())) {
            throw new IllegalArgumentException("Unknown bloom render pass: " + pass.id());
        }
        float alpha = EffectTimeline.fadeEnvelope(context.progress(), 0.08F, 0.15F);
        renderCircle(poseStack, bufferSource.getBuffer(pass.renderType()), context.scale(),
            context.ageTicks() * 0.035F, alpha);
    }

    @Override
    public AABB cullingBounds(EffectRenderContext context) {
        double radius = Math.sqrt(2.0) * context.scale();
        double x = context.transform().position().x();
        double y = context.transform().position().y();
        double z = context.transform().position().z();
        return new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    @Override
    public ScreenEffectSettings screenEffectSettings(EffectRenderContext context) {
        return ScreenEffectSettings.bloom(BloomSettings.DEBUG_DEFAULT);
    }

    @Override
    public ScreenEffectMask screenEffectMask(EffectRenderContext context) {
        return new ScreenEffectMask(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public RenderType screenMaskRenderType(EffectRenderContext context) {
        return SCREEN_MASK_RENDER_TYPE;
    }

    @Override
    public void renderScreenMask(EffectRenderContext context, RenderType maskRenderType, PoseStack poseStack,
        MultiBufferSource bufferSource) {
        float alpha = EffectTimeline.fadeEnvelope(context.progress(), 0.08F, 0.15F);
        renderCircle(poseStack, bufferSource.getBuffer(maskRenderType), context.scale(),
            context.ageTicks() * 0.035F, alpha);
    }

    private static void renderCircle(PoseStack poseStack, VertexConsumer vertices, float scale, float rotation,
        float alpha) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.025, 0.0);
        poseStack.mulPose(Axis.YP.rotation(rotation));
        poseStack.scale(scale, 1.0F, scale);
        EffectMeshes.horizontalQuad().emit(vertices, poseStack.last(), 1.0F, 1.0F, 1.0F, alpha,
            0, 0x00F000F0);
        poseStack.popPose();
    }
}
