package mafisher.quasar.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.EffectQualityTier;
import mafisher.quasar.effect.EffectTimeline;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

/** Debug renderer for a cinematic, three-dimensional black hole and its plasma flows. */
final class BlackHoleRenderer implements ScreenEffectRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        QuasarLib.MOD_ID, "textures/effect/rune_circle.png"
    );
    private static final RenderType CORE_RENDER_TYPE = EffectRenderTypes.blackHoleCore(TEXTURE);
    private static final RenderType DISK_RENDER_TYPE = EffectRenderTypes.blackHoleDisk(TEXTURE);
    private static final RenderType SCREEN_MASK_RENDER_TYPE = EffectRenderTypes.blackHoleScreenMask(TEXTURE);
    private static final String CORE_PASS = "core";
    private static final String DISK_PASS = "disk";
    private static final float CORE_RADIUS_FACTOR = 0.38F;
    private static final BlackHoleScreenSettings SCREEN_SETTINGS = BlackHoleScreenSettings.CINEMATIC_DEFAULT;
    private static final List<EffectRenderPass> RENDER_PASSES = List.of(
        new EffectRenderPass(CORE_PASS, CORE_RENDER_TYPE),
        new EffectRenderPass(DISK_PASS, DISK_RENDER_TYPE)
    );
    private static final EffectLodProfile LOD_PROFILE = EffectLodProfile.of(2.0, 8.0);

    @Override
    public List<EffectRenderPass> renderPasses(EffectRenderContext context) {
        return RENDER_PASSES;
    }

    @Override
    public EffectLodProfile lodProfile() {
        return LOD_PROFILE;
    }

    @Override
    public void render(EffectRenderContext context, EffectRenderPass pass, PoseStack poseStack,
        MultiBufferSource bufferSource) {
        float scale = animatedScale(context);
        if (scale <= 0.0F) {
            return;
        }

        int shaderData = shaderData(context);
        int sphereLod = context.lod().level();
        if (context.quality() == EffectQualityTier.LOW) {
            sphereLod = Math.max(sphereLod, 2);
        } else if (context.quality() == EffectQualityTier.MEDIUM) {
            sphereLod = Math.max(sphereLod, 1);
        }
        switch (pass.id()) {
            case CORE_PASS -> renderCore(poseStack, bufferSource.getBuffer(CORE_RENDER_TYPE),
                scale * CORE_RADIUS_FACTOR, sphereLod, shaderData);
            case DISK_PASS -> renderDisk(poseStack, bufferSource.getBuffer(DISK_RENDER_TYPE), context,
                scale, EffectTimeline.fadeEnvelope(context.progress(), 0.08F, 0.12F),
                BlackHoleLighting.emissionScale(daylightFactor(context)), shaderData);
            default -> throw new IllegalArgumentException("Unknown black-hole render pass: " + pass.id());
        }
    }

    @Override
    public AABB cullingBounds(EffectRenderContext context) {
        double radius = context.scale() * 1.55;
        double x = context.transform().position().x();
        double y = context.transform().position().y();
        double z = context.transform().position().z();
        return new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    @Override
    public double spatialRadius(EffectRenderContext context) {
        return context.scale() * 1.55;
    }

    @Override
    public ScreenEffectSettings screenEffectSettings(EffectRenderContext context) {
        BlackHoleScreenSettings adaptedSettings = BlackHoleLighting.screenSettings(
            SCREEN_SETTINGS, daylightFactor(context));
        return ScreenEffectSettings.blackHole(adaptedSettings);
    }

    @Override
    public ScreenEffectMask screenEffectMask(EffectRenderContext context) {
        float strength = 0.96F * EffectTimeline.fadeEnvelope(context.progress(), 0.08F, 0.12F);
        return new ScreenEffectMask(1.0F, 1.0F, 1.0F, strength);
    }

    @Override
    public RenderType screenMaskRenderType(EffectRenderContext context) {
        return SCREEN_MASK_RENDER_TYPE;
    }

    @Override
    public void renderScreenMask(EffectRenderContext context, RenderType maskRenderType, PoseStack poseStack,
        MultiBufferSource bufferSource) {
        float alpha = EffectTimeline.fadeEnvelope(context.progress(), 0.08F, 0.12F);
        renderInfluenceMask(poseStack, bufferSource.getBuffer(maskRenderType),
            context.scale() * SCREEN_SETTINGS.lensRadiusScale(), alpha);
    }

    private static void renderCore(PoseStack poseStack, VertexConsumer vertices, float radius, int lodLevel,
        int shaderData) {
        poseStack.pushPose();
        try {
            poseStack.scale(radius, radius, radius);
            BlackHoleGeometry.sphere(lodLevel).emit(vertices, poseStack.last(), 0.0F, 0.0F, 0.0F, 1.0F,
                shaderData, 0x00F000F0);
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderInfluenceMask(PoseStack poseStack, VertexConsumer vertices, float radius, float alpha) {
        poseStack.pushPose();
        try {
            poseStack.scale(radius, radius, radius);
            BlackHoleGeometry.sphere().emit(vertices, poseStack.last(), 1.0F, 1.0F, 1.0F, alpha,
                OverlayTexture.NO_OVERLAY, 0x00F000F0);
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderDisk(PoseStack poseStack, VertexConsumer vertices, EffectRenderContext context,
        float scale, float alpha, float emissionScale, int shaderData) {
        renderAccretionLayer(poseStack, vertices, BlackHoleGeometry.primaryDisk(), scale,
            context.ageTicks() * 0.018F, alpha, emissionScale, shaderData);
        if (context.quality().atLeast(EffectQualityTier.MEDIUM)) {
            renderAccretionLayer(poseStack, vertices, BlackHoleGeometry.secondaryDisk(), scale,
                -context.ageTicks() * 0.027F, alpha, emissionScale, shaderData);
        }
    }

    private static void renderAccretionLayer(PoseStack poseStack, VertexConsumer vertices,
        EffectMesh layer, float scale, float rotation, float alpha, float emissionScale, int shaderData) {
        poseStack.pushPose();
        try {
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.ZP.rotation(rotation));
            layer.emit(vertices, poseStack.last(), emissionScale, emissionScale, emissionScale,
                alpha, shaderData, 0x00F000F0);
        } finally {
            poseStack.popPose();
        }
    }

    private static float daylightFactor(EffectRenderContext context) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return 0.0F;
        }

        var position = context.transform().position();
        BlockPos blockPosition = BlockPos.containing(position.x(), position.y(), position.z());
        float skyBrightness = level.getBrightness(LightLayer.SKY, blockPosition) / 15.0F;
        float skyDarkening = Math.min(1.0F, level.getSkyDarken() / 15.0F);
        float blockBrightness = level.getBrightness(LightLayer.BLOCK, blockPosition) / 15.0F;
        float ambientBrightness = Math.max(skyBrightness * (1.0F - skyDarkening), blockBrightness * 0.35F);
        float normalized = Math.max(0.0F, Math.min(1.0F, (ambientBrightness - 0.12F) / 0.78F));
        return normalized * normalized * (3.0F - 2.0F * normalized);
    }

    private static float animatedScale(EffectRenderContext context) {
        float progress = context.progress();
        float entrance = EffectTimeline.easeOutCubic(Math.min(1.0F, progress / 0.12F));
        float exitProgress = Math.max(0.0F, (progress - 0.88F) / 0.12F);
        float exit = 1.0F - EffectTimeline.easeOutCubic(exitProgress);
        return context.scale() * entrance * exit;
    }

    private static int shaderData(EffectRenderContext context) {
        int progress = Math.round(context.progress() * 32767.0F);
        int seed = Math.floorMod(Long.hashCode(context.seed()), 32768);
        return OverlayTexture.pack(progress, seed);
    }
}
