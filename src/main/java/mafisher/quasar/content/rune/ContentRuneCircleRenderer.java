package mafisher.quasar.content.rune;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectLodProfile;
import mafisher.quasar.api.client.render.EffectMeshes;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderPass;
import mafisher.quasar.api.client.render.EffectRenderTypeOptions;
import mafisher.quasar.api.client.render.InstancedVisualEffectRenderer;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import mafisher.quasar.api.client.screen.ScreenEffectMask;
import mafisher.quasar.api.client.screen.ScreenEffectRenderer;
import mafisher.quasar.api.client.screen.ScreenEffectSettings;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.content.ContentBootstrap;
import mafisher.quasar.content.ContentScreenOperations;
import mafisher.quasar.content.ContentShaders;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/** Content-owned rune circle, custom shaders, and masked screen glow. */
public final class ContentRuneCircleRenderer implements ScreenEffectRenderer, InstancedVisualEffectRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "textures/effect/rune_circle.png"
    );
    private static final RenderType RENDER_TYPE = QuasarRenderTypes.instancedWorldVisual(
        ContentShaders.RUNE_CIRCLE, TEXTURE, EffectRenderTypeOptions.WORLD_DEFAULT
    );
    private static final RenderType SCREEN_MASK_RENDER_TYPE = QuasarRenderTypes.screenEffectMask(
        ContentShaders.RUNE_MASK, TEXTURE, EffectRenderTypeOptions.MASK_DEFAULT
    );
    private static final ContentRuneCircleSettings SETTINGS = ContentRuneCircleSettings.DEFAULT;
    private static final ContentRuneGlowSettings GLOW_SETTINGS = ContentRuneGlowSettings.DEFAULT;
    private static final String OUTER_PASS = "outer";
    private static final String MIDDLE_PASS = "middle";
    private static final String INNER_PASS = "inner";
    private static final EffectLodProfile LOD_PROFILE = EffectLodProfile.of(4.0);

    @Override
    public List<EffectRenderPass> renderPasses(EffectRenderContext context) {
        return List.of();
    }

    @Override
    public List<EffectInstancedPass> instancedPasses(EffectRenderContext context) {
        float entrance = EffectTimeline.easeOutCubic(Math.min(1.0F, context.progress() / 0.15F));
        float alpha = EffectTimeline.fadeEnvelope(context.progress(), 0.10F, 0.25F);
        float worldScale = context.scale() * entrance;
        float seedRotation = seedRotationDegrees(context.seed());
        float seedData = (Long.hashCode(context.seed()) & 0xFFFF) / 65535.0F;

        int fullLayers = fullLayerCount(context);
        int selectedLayers = reduced(context) ? 1 : fullLayers;
        List<EffectInstancedPass> passes = new ArrayList<>(selectedLayers * 2);
        if (context.lod().isTransitioning()) {
            int previousLayers = context.lod().previousLevel() > 0 ? 1 : fullLayers;
            if (previousLayers != selectedLayers) {
                float blend = (float)context.lod().blend();
                addLayers(passes, previousLayers, alpha * (1.0F - blend), worldScale, seedRotation,
                    context, seedData, "prev_");
                addLayers(passes, selectedLayers, alpha * blend, worldScale, seedRotation,
                    context, seedData, "next_");
                return List.copyOf(passes);
            }
        }
        addLayers(passes, selectedLayers, alpha, worldScale, seedRotation, context, seedData, "");
        return List.copyOf(passes);
    }

    private static void addLayers(List<EffectInstancedPass> passes, int layers, float alpha,
        float worldScale, float seedRotation, EffectRenderContext context, float seedData, String idSuffix) {
        passes.add(instancedLayer(idSuffix + OUTER_PASS, SETTINGS.outer(), worldScale,
            seedRotation + context.ageTicks() * SETTINGS.outer().rotationSpeed(), alpha,
            context.progress(), seedData));
        if (layers >= 2) {
            passes.add(instancedLayer(idSuffix + MIDDLE_PASS, SETTINGS.middle(), worldScale,
                seedRotation + context.ageTicks() * SETTINGS.middle().rotationSpeed(), alpha,
                context.progress(), seedData));
        }
        if (layers >= 3) {
            passes.add(instancedLayer(idSuffix + INNER_PASS, SETTINGS.inner(), worldScale,
                seedRotation + context.ageTicks() * SETTINGS.inner().rotationSpeed(), alpha,
                context.progress(), seedData));
        }
    }

    @Override
    public EffectLodProfile lodProfile() {
        return LOD_PROFILE;
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
        double maximumHeight = Math.max(SETTINGS.outer().height(),
            Math.max(SETTINGS.middle().height(), SETTINGS.inner().height()));
        return Math.sqrt(2.0 * context.scale() * context.scale() + maximumHeight * maximumHeight);
    }

    @Override
    public ScreenEffectSettings screenEffectSettings(EffectRenderContext context) {
        return ScreenEffectSettings.custom(ContentScreenOperations.RUNE_GLOW, GLOW_SETTINGS);
    }

    @Override
    public ScreenEffectMask screenEffectMask(EffectRenderContext context) {
        float strength = 0.85F * EffectTimeline.fadeEnvelope(context.progress(), 0.10F, 0.25F);
        return new ScreenEffectMask(GLOW_SETTINGS.red(), GLOW_SETTINGS.green(), GLOW_SETTINGS.blue(),
            strength);
    }

    @Override
    public RenderType screenMaskRenderType(EffectRenderContext context) {
        return SCREEN_MASK_RENDER_TYPE;
    }

    @Override
    public void renderScreenMask(EffectRenderContext context, RenderType maskRenderType,
        PoseStack poseStack, MultiBufferSource bufferSource) {
        float entrance = EffectTimeline.easeOutCubic(Math.min(1.0F, context.progress() / 0.15F));
        float alpha = EffectTimeline.fadeEnvelope(context.progress(), 0.10F, 0.25F);
        float worldScale = context.scale() * entrance;
        float seedRotation = seedRotationDegrees(context.seed());
        VertexConsumer vertices = bufferSource.getBuffer(maskRenderType);

        renderLayer(poseStack, vertices, worldScale, SETTINGS.outer(),
            seedRotation + context.ageTicks() * SETTINGS.outer().rotationSpeed(), alpha,
            OverlayTexture.NO_OVERLAY);
        if (layerCount(context) >= 2) {
            renderLayer(poseStack, vertices, worldScale, SETTINGS.middle(),
                seedRotation + context.ageTicks() * SETTINGS.middle().rotationSpeed(), alpha,
                OverlayTexture.NO_OVERLAY);
        }
        if (layerCount(context) >= 3) {
            renderLayer(poseStack, vertices, worldScale, SETTINGS.inner(),
                seedRotation + context.ageTicks() * SETTINGS.inner().rotationSpeed(), alpha,
                OverlayTexture.NO_OVERLAY);
        }
    }

    private static EffectInstancedPass instancedLayer(
        String id,
        ContentRuneCircleSettings.ContentRuneLayer layer,
        float worldScale,
        float rotationDegrees,
        float alpha,
        float progress,
        float seedData
    ) {
        Matrix4f localTransform = new Matrix4f()
            .translate(0.0F, layer.height(), 0.0F)
            .rotateY((float)Math.toRadians(rotationDegrees))
            .scale(worldScale * layer.scale(), 1.0F, worldScale * layer.scale());
        return new EffectInstancedPass(id, RENDER_TYPE, EffectMeshes.horizontalQuad(), localTransform,
            layer.red(), layer.green(), layer.blue(), alpha * layer.alpha(), progress, seedData);
    }

    private static int layerCount(EffectRenderContext context) {
        return reduced(context) ? 1 : fullLayerCount(context);
    }

    private static int fullLayerCount(EffectRenderContext context) {
        if (context.quality() == EffectQualityTier.LOW) {
            return 1;
        }
        return context.quality() == EffectQualityTier.MEDIUM ? 2 : 3;
    }

    private static boolean reduced(EffectRenderContext context) {
        return context.lod().level() > 0;
    }

    private static void renderLayer(
        PoseStack poseStack,
        VertexConsumer vertices,
        float worldScale,
        ContentRuneCircleSettings.ContentRuneLayer layer,
        float rotationDegrees,
        float alpha,
        int overlay
    ) {
        poseStack.pushPose();
        poseStack.translate(0.0, layer.height(), 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationDegrees));
        poseStack.scale(worldScale * layer.scale(), 1.0F, worldScale * layer.scale());
        EffectMeshes.horizontalQuad().emit(vertices, poseStack.last(), layer.red(), layer.green(),
            layer.blue(), alpha * layer.alpha(), overlay, 0x00F000F0);
        poseStack.popPose();
    }

    /** Settings rotations are degrees per tick; both world and mask paths convert them to radians. */
    private static float seedRotationDegrees(long seed) {
        return (Long.hashCode(seed) & 0xFFFF) / 65535.0F * 360.0F;
    }
}
