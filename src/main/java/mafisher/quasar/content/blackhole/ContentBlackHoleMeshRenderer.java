package mafisher.quasar.content.blackhole;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import mafisher.quasar.api.client.screen.BlackHoleScreenSettings;
import mafisher.quasar.api.client.render.EffectLodProfile;
import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderPass;
import mafisher.quasar.api.client.render.EffectRenderTypeOptions;
import mafisher.quasar.api.client.render.EffectStaticMeshPass;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import mafisher.quasar.api.client.screen.ScreenEffectMask;
import mafisher.quasar.api.client.screen.ScreenEffectRenderer;
import mafisher.quasar.api.client.screen.ScreenEffectSettings;
import mafisher.quasar.api.client.render.StaticMeshVisualEffectRenderer;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.content.ContentBootstrap;
import mafisher.quasar.content.ContentShaders;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/** Content-owned, static-mesh black hole used as the mesh-renderer acceptance demo. */
public final class ContentBlackHoleMeshRenderer implements ScreenEffectRenderer, StaticMeshVisualEffectRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "textures/effect/rune_circle.png"
    );
    private static final EffectRenderTypeOptions DISK_OPTIONS = new EffectRenderTypeOptions(
        EffectRenderTypeOptions.Transparency.TRANSLUCENT, true, true, false, false, true, true
    );
    private static final RenderType CORE_RENDER_TYPE = QuasarRenderTypes.worldVisual(
        ContentShaders.BLACK_HOLE_MESH, TEXTURE, EffectRenderTypeOptions.WORLD_DEPTH_WRITING
    );
    private static final RenderType DISK_RENDER_TYPE = QuasarRenderTypes.worldVisual(
        ContentShaders.BLACK_HOLE_MESH, TEXTURE, DISK_OPTIONS
    );
    private static final RenderType SCREEN_MASK_RENDER_TYPE = QuasarRenderTypes.screenEffectMask(
        ContentShaders.BLACK_HOLE_MESH_MASK, TEXTURE, DISK_OPTIONS
    );
    private static final String CORE_PASS = "core";
    private static final String DISK_PASS = "disk";
    private static final float CORE_RADIUS_FACTOR = 0.38F;
    private static final BlackHoleScreenSettings SCREEN_SETTINGS = BlackHoleScreenSettings.CINEMATIC_DEFAULT;
    private static final List<EffectRenderPass> RENDER_PASSES = List.of(
        new EffectRenderPass(DISK_PASS, DISK_RENDER_TYPE)
    );
    private static final EffectLodProfile LOD_PROFILE = EffectLodProfile.of(2.0, 8.0);

    @Override
    public List<EffectRenderPass> renderPasses(EffectRenderContext context) {
        return RENDER_PASSES;
    }

    @Override
    public List<EffectStaticMeshPass> staticMeshPasses(EffectRenderContext context) {
        float scale = animatedScale(context);
        if (scale <= 0.0F) {
            return List.of();
        }
        int sphereLod = sphereLod(context);
        float radius = scale * CORE_RADIUS_FACTOR;
        Matrix4f localTransform = new Matrix4f().scale(radius, radius, radius);
        return List.of(new EffectStaticMeshPass(CORE_PASS, CORE_RENDER_TYPE,
            ContentBlackHoleMeshGeometry.sphere(sphereLod), localTransform, 0.0F, 0.0F, 0.0F, 1.0F,
            OverlayTexture.NO_OVERLAY, EffectStaticMeshPass.FULLBRIGHT_LIGHT));
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
        if (DISK_PASS.equals(pass.id())) {
            renderDisk(poseStack, bufferSource.getBuffer(DISK_RENDER_TYPE), context,
                scale, EffectTimeline.fadeEnvelope(context.progress(), 0.08F, 0.12F),
                ContentBlackHoleLighting.emissionScale(context.daylight()), shaderData);
        } else {
            throw new IllegalArgumentException("Unknown black-hole render pass: " + pass.id());
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
        return ScreenEffectSettings.blackHole(SCREEN_SETTINGS);
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

    private static int sphereLod(EffectRenderContext context) {
        int sphereLod = context.lod().level();
        if (context.quality() == EffectQualityTier.LOW) {
            sphereLod = Math.max(sphereLod, 2);
        } else if (context.quality() == EffectQualityTier.MEDIUM) {
            sphereLod = Math.max(sphereLod, 1);
        }
        return sphereLod;
    }

    private static void renderInfluenceMask(PoseStack poseStack, VertexConsumer vertices, float radius, float alpha) {
        poseStack.pushPose();
        try {
            poseStack.scale(radius, radius, radius);
            ContentBlackHoleMeshGeometry.sphere().emit(vertices, poseStack.last(), 1.0F, 1.0F, 1.0F, alpha,
                OverlayTexture.NO_OVERLAY, 0x00F000F0);
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderDisk(PoseStack poseStack, VertexConsumer vertices, EffectRenderContext context,
        float scale, float alpha, float emissionScale, int shaderData) {
        renderAccretionLayer(poseStack, vertices, ContentBlackHoleMeshGeometry.primaryDisk(), scale,
            context.ageTicks() * 0.018F, alpha, emissionScale, shaderData);
        if (context.quality().atLeast(EffectQualityTier.MEDIUM)) {
            renderAccretionLayer(poseStack, vertices, ContentBlackHoleMeshGeometry.secondaryDisk(), scale,
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
