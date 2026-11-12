package mafisher.quasar.content.demo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mafisher.quasar.api.client.EffectMeshes;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.content.ContentBootstrap;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Renderer for the content-owned harmless probe entity. */
public final class ContentProbeRenderer extends EntityRenderer<ContentProbeEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "textures/effect/ice_shard.png"
    );

    public ContentProbeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
        ContentProbeEntity entity,
        float entityYaw,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight
    ) {
        float progress = entity.getEffectProgress(partialTick);
        float grow = EffectTimeline.easeOutCubic(Math.min(1.0F, progress / 0.22F));
        float alpha = EffectTimeline.fadeEnvelope(progress, 0.02F, 0.18F);
        RenderType renderType = alpha >= 1.0F
            ? RenderType.entityCutoutNoCull(TEXTURE)
            : RenderType.entityTranslucent(TEXTURE);
        VertexConsumer vertices = bufferSource.getBuffer(renderType);

        poseStack.pushPose();
        poseStack.translate(0.0, 0.05, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(entityYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getEffectRoll()));
        poseStack.scale(1.15F * grow, 2.8F * grow, 1.0F);
        drawShardPlane(vertices, poseStack.last(), alpha, packedLight);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        drawShardPlane(vertices, poseStack.last(), alpha, packedLight);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ContentProbeEntity entity) {
        return TEXTURE;
    }

    private static void drawShardPlane(VertexConsumer vertices, PoseStack.Pose pose, float alpha, int packedLight) {
        EffectMeshes.verticalPlane().emit(vertices, pose, 0.72F, 0.94F, 1.0F, alpha,
            OverlayTexture.NO_OVERLAY,
            Math.max(packedLight, LightTexture.FULL_BRIGHT));
    }
}
