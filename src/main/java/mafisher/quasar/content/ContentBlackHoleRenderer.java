package mafisher.quasar.content;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import mafisher.quasar.client.effect.EffectLodProfile;
import mafisher.quasar.client.effect.EffectMesh;
import mafisher.quasar.client.effect.EffectMeshes;
import mafisher.quasar.client.effect.EffectRenderContext;
import mafisher.quasar.client.effect.EffectRenderPass;
import mafisher.quasar.client.effect.EffectRenderTypeOptions;
import mafisher.quasar.client.effect.QuasarRenderTypes;
import mafisher.quasar.client.effect.ScreenEffectMask;
import mafisher.quasar.client.effect.ScreenEffectRenderer;
import mafisher.quasar.client.effect.ScreenEffectSettings;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Screen-space raymarched black hole. The world pass is deliberately empty: the screen operation produces the
 * shadow, photon rings, accretion disk, lensed arcs, and background lensing, and its own coverage mask spans the
 * full viewport. The shader bounds the visible effect with its radial fade and a coarse foreground test.
 */
final class ContentBlackHoleRenderer implements ScreenEffectRenderer {
    private static final ContentBlackHoleSettings SETTINGS = ContentBlackHoleSettings.DEFAULT;
    private static final ResourceLocation MASK_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    // Screen-space coverage; the mask must never be a depth-tested world proxy that clips the effect.
    private static final EffectRenderTypeOptions MASK_OPTIONS = new EffectRenderTypeOptions(
        EffectRenderTypeOptions.Transparency.TRANSLUCENT,
        false,
        true,
        false,
        false,
        false,
        true
    );
    private static final RenderType MASK_RENDER_TYPE = QuasarRenderTypes.screenEffectMask(
        ContentShaders.BLACK_HOLE_MASK, MASK_TEXTURE, MASK_OPTIONS
    );
    private static final EffectMesh MASK_QUAD = EffectMeshes.verticalPlane();
    private static final EffectLodProfile LOD_PROFILE = EffectLodProfile.of(1.5, 4.0, 9.0);
    private static final int FULLBRIGHT_LIGHT = 0x00F000F0;
    private static final float MASK_STRENGTH = 1.0F;

    @Override
    public List<EffectRenderPass> renderPasses(EffectRenderContext context) {
        return List.of();
    }

    @Override
    public EffectLodProfile lodProfile() {
        return LOD_PROFILE;
    }

    @Override
    public double spatialRadius(EffectRenderContext context) {
        return context.scale() * SETTINGS.maskRadiusFactor();
    }

    @Override
    public AABB cullingBounds(EffectRenderContext context) {
        double radius = spatialRadius(context);
        var position = context.transform().position();
        return new AABB(
            position.x() - radius, position.y() - radius, position.z() - radius,
            position.x() + radius, position.y() + radius, position.z() + radius
        );
    }

    @Override
    public ScreenEffectSettings screenEffectSettings(EffectRenderContext context) {
        return ScreenEffectSettings.custom(ContentScreenOperations.BLACK_HOLE, SETTINGS);
    }

    @Override
    public ScreenEffectMask screenEffectMask(EffectRenderContext context) {
        return new ScreenEffectMask(1.0F, 1.0F, 1.0F, MASK_STRENGTH);
    }

    @Override
    public RenderType screenMaskRenderType(EffectRenderContext context) {
        return MASK_RENDER_TYPE;
    }

    @Override
    public void renderScreenMask(EffectRenderContext context, RenderType maskRenderType,
        PoseStack poseStack, MultiBufferSource bufferSource) {
        // The mask vertex shader emits a screen-space quad from UV0, so the pose is irrelevant.
        MASK_QUAD.emit(
            bufferSource.getBuffer(maskRenderType),
            poseStack.last(),
            1.0F,
            1.0F,
            1.0F,
            1.0F,
            OverlayTexture.NO_OVERLAY,
            FULLBRIGHT_LIGHT
        );
    }
}
