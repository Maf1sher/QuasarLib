package mafisher.quasar.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.EffectTimeline;
import mafisher.quasar.effect.entity.EffectProbeEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Renderer for the harmless, server-spawned framework test entity. */
public final class EffectProbeRenderer extends EntityRenderer<EffectProbeEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        QuasarLib.MOD_ID, "textures/effect/ice_shard.png"
    );

    public EffectProbeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
        EffectProbeEntity entity,
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
            : EffectRenderTypes.entityUnsorted(TEXTURE);
        VertexConsumer vertices = bufferSource.getBuffer(renderType);

        poseStack.pushPose();
        poseStack.translate(0.0, 0.05, 0.0);
        EffectRenderTransform.applyRotation(poseStack, entityYaw, entity.getXRot(), entity.getEffectRoll());
        poseStack.scale(1.15F * grow, 2.8F * grow, 1.0F);
        drawShardPlane(vertices, poseStack.last(), alpha, packedLight);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90.0F));
        drawShardPlane(vertices, poseStack.last(), alpha, packedLight);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(EffectProbeEntity entity) {
        return TEXTURE;
    }

    private static void drawShardPlane(VertexConsumer vertices, PoseStack.Pose pose, float alpha, int packedLight) {
        EffectMeshes.verticalPlane().emit(vertices, pose, 0.72F, 0.94F, 1.0F, alpha,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
            Math.max(packedLight, LightTexture.FULL_BRIGHT));
    }
}
