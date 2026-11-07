package mafisher.quasar.content;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import mafisher.quasar.client.effect.EffectInstancedPass;
import mafisher.quasar.client.effect.EffectLodProfile;
import mafisher.quasar.client.effect.EffectMeshes;
import mafisher.quasar.client.effect.EffectRenderContext;
import mafisher.quasar.client.effect.EffectRenderPass;
import mafisher.quasar.client.effect.EffectRenderTypeOptions;
import mafisher.quasar.client.effect.InstancedVisualEffectRenderer;
import mafisher.quasar.client.effect.QuasarRenderTypes;
import mafisher.quasar.client.effect.ScreenEffectMask;
import mafisher.quasar.client.effect.ScreenEffectRenderer;
import mafisher.quasar.client.effect.ScreenEffectSettings;
import mafisher.quasar.effect.EffectQualityTier;
import mafisher.quasar.effect.EffectTimeline;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/** Content-owned rune circle, custom shaders, and masked screen glow. */
final class ContentRuneCircleRenderer implements ScreenEffectRenderer, InstancedVisualEffectRenderer {
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
        float seedRotation = seedRotation(context.seed());
        float seedData = (Long.hashCode(context.seed()) & 0xFFFF) / 65535.0F;

        List<EffectInstancedPass> passes = new ArrayList<>(layerCount(context));
        passes.add(instancedLayer(OUTER_PASS, SETTINGS.outer(), worldScale,
            seedRotation + context.ageTicks() * SETTINGS.outer().rotationSpeed(), alpha,
            context.progress(), seedData));
        if (layerCount(context) >= 2) {
            passes.add(instancedLayer(MIDDLE_PASS, SETTINGS.middle(), worldScale,
                seedRotation + context.ageTicks() * SETTINGS.middle().rotationSpeed(), alpha,
                context.progress(), seedData));
        }
        if (layerCount(context) >= 3) {
            passes.add(instancedLayer(INNER_PASS, SETTINGS.inner(), worldScale,
                seedRotation + context.ageTicks() * SETTINGS.inner().rotationSpeed(), alpha,
                context.progress(), seedData));
        }
        return List.copyOf(passes);
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
        float seedRotation = seedRotation(context.seed());
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
        float rotation,
        float alpha,
        float progress,
        float seedData
    ) {
        Matrix4f localTransform = new Matrix4f()
            .translate(0.0F, layer.height(), 0.0F)
            .rotateY((float)Math.toRadians(rotation))
            .scale(worldScale * layer.scale(), 1.0F, worldScale * layer.scale());
        return new EffectInstancedPass(id, RENDER_TYPE, EffectMeshes.horizontalQuad(), localTransform,
            layer.red(), layer.green(), layer.blue(), alpha * layer.alpha(), progress, seedData);
    }

    private static int layerCount(EffectRenderContext context) {
        if (reduced(context) || context.quality() == EffectQualityTier.LOW) {
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
        float rotation,
        float alpha,
        int overlay
    ) {
        poseStack.pushPose();
        poseStack.translate(0.0, layer.height(), 0.0);
        poseStack.mulPose(Axis.YP.rotation(rotation));
        poseStack.scale(worldScale * layer.scale(), 1.0F, worldScale * layer.scale());
        EffectMeshes.horizontalQuad().emit(vertices, poseStack.last(), layer.red(), layer.green(),
            layer.blue(), alpha * layer.alpha(), overlay, 0x00F000F0);
        poseStack.popPose();
    }

    private static float seedRotation(long seed) {
        return (Long.hashCode(seed) & 0xFFFF) / 65535.0F * 360.0F;
    }
}
