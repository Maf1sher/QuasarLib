package mafisher.quasar.content.blackhole;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import mafisher.quasar.api.client.render.EffectLodProfile;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderPass;
import mafisher.quasar.api.client.screen.ScreenEffectMask;
import mafisher.quasar.api.client.screen.ScreenEffectMasks;
import mafisher.quasar.api.client.screen.ScreenEffectRenderer;
import mafisher.quasar.api.client.screen.ScreenEffectSettings;
import mafisher.quasar.content.ContentScreenOperations;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;

/**
 * Screen-space raymarched black hole. The world pass is deliberately empty: the screen operation produces the
 * shadow, photon rings, accretion disk, lensed arcs, and background lensing, and its own coverage mask spans the
 * full viewport. The shader bounds the visible effect with its lens-region fade and a per-pixel scene-depth test.
 */
public final class ContentBlackHoleRenderer implements ScreenEffectRenderer {
    private static final ContentBlackHoleSettings SETTINGS = ContentBlackHoleSettings.DEFAULT;
    private static final EffectLodProfile LOD_PROFILE = EffectLodProfile.of(1.5, 4.0, 9.0);
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
        ContentBlackHoleImpulses.tick(context);
        return ScreenEffectSettings.custom(ContentScreenOperations.BLACK_HOLE, SETTINGS);
    }

    @Override
    public ScreenEffectMask screenEffectMask(EffectRenderContext context) {
        return new ScreenEffectMask(1.0F, 1.0F, 1.0F, MASK_STRENGTH);
    }

    @Override
    public RenderType screenMaskRenderType(EffectRenderContext context) {
        return ScreenEffectMasks.fullscreen();
    }

    @Override
    public void renderScreenMask(EffectRenderContext context, RenderType maskRenderType,
        PoseStack poseStack, MultiBufferSource bufferSource) {
        // Coverage-only screen-space quad; the full-screen helper owns the
        // no-depth-test render type and the quad emission.
        ScreenEffectMasks.drawFullscreen(maskRenderType, poseStack, bufferSource);
    }
}
