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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Screen-space raymarched black hole. The world pass is deliberately empty: the only world geometry is a large,
 * depth-tested sphere that writes coverage into the compositor mask. Shadow, photon rings, accretion disk, lensed
 * arcs, and background lensing are all produced by the content-owned screen operation.
 */
final class ContentBlackHoleRenderer implements ScreenEffectRenderer {
    private static final ContentBlackHoleSettings SETTINGS = ContentBlackHoleSettings.DEFAULT;
    private static final ResourceLocation MASK_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    // The operation shader resolves scene depth per pixel; a depth-tested flat proxy would clip
    // the screen-space lens against terrain and near planes.
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
        float radius = (float)spatialRadius(context);
        Vector3f cameraLook = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
        Vector3f look = new Vector3f(cameraLook);
        if (look.lengthSquared() < 1.0E-6F) {
            look.set(0.0F, 0.0F, 1.0F);
        } else {
            look.normalize();
        }
        // A camera-facing billboard is perpendicular to the ray to the effect,
        // not to the image plane. For an off-centre black hole its projection is
        // a keystone and the far edge can cut through the effect, which looks
        // like a smaller screen inside the frame. Keep the mask parallel to the
        // image plane instead so it projects as a screen-space rectangle.
        Matrix4f inverseEffectRotation = ContentBlackHoleGeometry.rotation(
            context.transform().yaw(),
            context.transform().pitch(),
            context.transform().roll()
        ).invert();
        Vector3f localLook = inverseEffectRotation.transformDirection(look).normalize();
        Quaternionf facing = new Quaternionf().rotationTo(
            new Vector3f(0.0F, 0.0F, 1.0F), localLook
        );

        poseStack.pushPose();
        try {
            poseStack.mulPose(facing);
            poseStack.scale(radius * 2.0F, radius * 2.0F, 1.0F);
            poseStack.translate(0.0F, -0.5F, 0.0F);
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
        } finally {
            poseStack.popPose();
        }
    }
}
