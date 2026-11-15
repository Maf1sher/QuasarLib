package mafisher.quasar.content.orbital;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectLodProfile;
import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectMeshes;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderPass;
import mafisher.quasar.api.client.render.EffectRenderTypeOptions;
import mafisher.quasar.api.client.render.InstancedVisualEffectRenderer;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import mafisher.quasar.api.client.screen.ScreenEffectMask;
import mafisher.quasar.api.client.screen.ScreenEffectMasks;
import mafisher.quasar.api.client.screen.ScreenEffectRenderer;
import mafisher.quasar.api.client.screen.ScreenEffectSettings;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectSpace;
import mafisher.quasar.content.ContentBootstrap;
import mafisher.quasar.content.ContentScreenOperations;
import mafisher.quasar.content.ContentShaders;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Content-owned orbital-laser visual: an instanced target marker and a full-screen beam operation.
 *
 * <p>The marker locks and spins through {@link ContentOrbitalLaserSettings#TARGET_TICKS}, then fades
 * under the descending beam head. From descent onward the screen operation draws the volumetric
 * beam, impact flash, shock ring, and ground contact glow. The marker world pass is empty once the
 * handoff finishes so no invisible geometry is submitted.</p>
 */
public final class ContentOrbitalLaserRenderer
    implements InstancedVisualEffectRenderer, ScreenEffectRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "textures/effect/orbital_laser_marker.png"
    );
    private static final EffectMesh MARKER_MESH = EffectMeshes.disk(2, 96);
    private static final RenderType MARKER_RENDER_TYPE = QuasarRenderTypes.instancedWorldVisual(
        ContentShaders.ORBITAL_LASER_MARKER,
        TEXTURE,
        new EffectRenderTypeOptions(
            EffectRenderTypeOptions.Transparency.TRANSLUCENT,
            true,
            true,
            false,
            false,
            false,
            true
        )
    );
    private static final ContentOrbitalLaserBeamSettings BEAM_SETTINGS =
        ContentOrbitalLaserBeamSettings.DEFAULT;
    private static final EffectLodProfile LOD_PROFILE = EffectLodProfile.of(1.0, 3.0, 8.0);
    private static final String MARKER_PASS = "marker";
    private static final List<EffectRenderPass> RENDER_PASSES = List.of();

    private static final float MARKER_RED = 1.0F;
    private static final float MARKER_GREEN = 0.16F;
    private static final float MARKER_BLUE = 0.10F;
    private static final float HANDOFF_GREEN = 0.48F;
    private static final float HANDOFF_BLUE = 0.26F;

    @Override
    public List<EffectRenderPass> renderPasses(EffectRenderContext context) {
        return RENDER_PASSES;
    }

    @Override
    public List<EffectInstancedPass> instancedPasses(EffectRenderContext context) {
        float markerAlpha = ContentOrbitalLaserCinematics.markerAlpha(context.progress());
        if (markerAlpha <= 0.001F) {
            return List.of();
        }

        float age = context.ageTicks();
        boolean locking = age < ContentOrbitalLaserSettings.TARGET_TICKS;
        float pulse = locking
            ? 1.0F + 0.04F * (float)Math.sin(age * 0.55F)
            : 1.0F;
        float spin = locking
            ? (float)Math.toRadians(age * (360.0F / ContentOrbitalLaserSettings.TARGET_TICKS))
            : 0.0F;
        float lockAlpha = locking
            ? 0.62F + 0.28F * (0.5F + 0.5F * (float)Math.sin(age * 0.5F))
            : 0.86F;
        float alpha = lockAlpha * markerAlpha;
        float lockProgress = Math.min(1.0F, age / ContentOrbitalLaserSettings.TARGET_TICKS);
        float handoff = 1.0F - markerAlpha;
        float seed = (Long.hashCode(context.seed()) & 0xFFFF) / 65535.0F;
        Matrix4f localTransform = new Matrix4f()
            .rotateY(spin)
            .scale(context.scale() * pulse);
        EffectInstancedPass marker = new EffectInstancedPass(
            MARKER_PASS,
            MARKER_RENDER_TYPE,
            MARKER_MESH,
            localTransform,
            MARKER_RED,
            mix(MARKER_GREEN, HANDOFF_GREEN, handoff),
            mix(MARKER_BLUE, HANDOFF_BLUE, handoff),
            alpha,
            lockProgress,
            seed
        );
        return List.of(marker);
    }

    @Override
    public EffectLodProfile lodProfile() {
        return LOD_PROFILE;
    }

    @Override
    public ScreenEffectSettings screenEffectSettings(EffectRenderContext context) {
        return ScreenEffectSettings.custom(
            ContentScreenOperations.ORBITAL_LASER_BEAM, BEAM_SETTINGS
        );
    }

    @Override
    public ScreenEffectMask screenEffectMask(EffectRenderContext context) {
        float strength = ContentOrbitalLaserCinematics.screenVisible(context.progress())
            ? 1.0F
            : 0.0F;
        return new ScreenEffectMask(1.0F, 1.0F, 1.0F, strength);
    }

    @Override
    public RenderType screenMaskRenderType(EffectRenderContext context) {
        return ScreenEffectMasks.fullscreen();
    }

    @Override
    public void renderScreenMask(EffectRenderContext context, RenderType maskRenderType,
        PoseStack poseStack, MultiBufferSource bufferSource) {
        ScreenEffectMasks.drawFullscreen(maskRenderType, poseStack, bufferSource);
    }

    @Override
    public AABB cullingBounds(EffectRenderContext context) {
        EffectPoint base = context.transform().position();
        Vector3f up = EffectSpace.from(
            context.transform(), context.scale(), EffectPoint.ZERO
        ).basis().up();
        double topX = base.x() + up.x() * BEAM_SETTINGS.topHeight();
        double topY = base.y() + up.y() * BEAM_SETTINGS.topHeight();
        double topZ = base.z() + up.z() * BEAM_SETTINGS.topHeight();
        float tipDepth = ContentOrbitalLaserCinematics.tipDepth(
            context.progress(), context.parameters()
        );
        double bottomX = base.x() - up.x() * tipDepth;
        double bottomY = base.y() - up.y() * tipDepth;
        double bottomZ = base.z() - up.z() * tipDepth;
        double footprint = ContentOrbitalLaserBeamGeometry.maximumFootprint(
            BEAM_SETTINGS, context.scale()
        );
        return new AABB(
            Math.min(base.x(), Math.min(topX, bottomX)) - footprint,
            Math.min(base.y(), Math.min(topY, bottomY)) - footprint,
            Math.min(base.z(), Math.min(topZ, bottomZ)) - footprint,
            Math.max(base.x(), Math.max(topX, bottomX)) + footprint,
            Math.max(base.y(), Math.max(topY, bottomY)) + footprint,
            Math.max(base.z(), Math.max(topZ, bottomZ)) + footprint
        );
    }

    @Override
    public double spatialRadius(EffectRenderContext context) {
        return ContentOrbitalLaserBeamGeometry.spatialRadius(
            BEAM_SETTINGS, context.scale()
        );
    }

    private static float mix(float from, float to, float factor) {
        return from + (to - from) * Math.max(0.0F, Math.min(1.0F, factor));
    }
}
