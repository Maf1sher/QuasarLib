package mafisher.quasar.content;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mafisher.quasar.client.effect.EffectMeshes;
import mafisher.quasar.client.effect.EffectRenderContext;
import mafisher.quasar.client.effect.EffectRenderPass;
import mafisher.quasar.client.effect.QuasarRenderTypes;
import mafisher.quasar.client.effect.VisualEffectRenderer;
import mafisher.quasar.effect.EffectTimeline;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/** Content-owned rune circle and migration template for effects that can leave the library package. */
final class ContentRuneCircleRenderer implements VisualEffectRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "textures/effect/rune_circle.png"
    );
    private static final RenderType RENDER_TYPE = QuasarRenderTypes.worldVisual(TEXTURE);
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
        float seedRotation = seedRotation(context.seed());
        VertexConsumer vertices = bufferSource.getBuffer(pass.renderType());

        switch (pass.id()) {
            case OUTER_PASS -> renderLayer(poseStack, vertices, worldScale,
                seedRotation + context.ageTicks() * 0.030F, alpha * 0.90F, 0.06F, 0.62F, 0.90F, 1.0F);
            case MIDDLE_PASS -> renderLayer(poseStack, vertices, worldScale * 0.72F,
                seedRotation - context.ageTicks() * 0.045F, alpha * 0.72F, 0.16F, 0.82F, 0.97F, 1.0F);
            case INNER_PASS -> renderLayer(poseStack, vertices, worldScale * 0.43F,
                seedRotation + context.ageTicks() * 0.065F, alpha * 0.55F, 0.26F, 1.0F, 1.0F, 1.0F);
            default -> throw new IllegalArgumentException("Unknown content rune render pass: " + pass.id());
        }
    }

    @Override
    public AABB cullingBounds(EffectRenderContext context) {
        double maximumHeight = 0.26;
        double radius = Math.sqrt(2.0 * context.scale() * context.scale() + maximumHeight * maximumHeight);
        double x = context.transform().position().x();
        double y = context.transform().position().y();
        double z = context.transform().position().z();
        return new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    private static void renderLayer(
        PoseStack poseStack,
        VertexConsumer vertices,
        float scale,
        float rotation,
        float alpha,
        float height,
        float red,
        float green,
        float blue
    ) {
        poseStack.pushPose();
        poseStack.translate(0.0, height, 0.0);
        poseStack.mulPose(Axis.YP.rotation(rotation));
        poseStack.scale(scale, 1.0F, scale);
        EffectMeshes.horizontalQuad().emit(vertices, poseStack.last(), red, green, blue, alpha,
            0, 0x00F000F0);
        poseStack.popPose();
    }

    private static float seedRotation(long seed) {
        return (Long.hashCode(seed) & 0xFFFF) / 65535.0F * 360.0F;
    }
}
