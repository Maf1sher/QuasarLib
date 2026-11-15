package mafisher.quasar.content.orbital;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import mafisher.quasar.api.client.particle.EffectParticleAppearance;
import mafisher.quasar.api.client.particle.EffectParticleField;
import mafisher.quasar.api.client.particle.EffectParticleMotion;
import mafisher.quasar.api.client.particle.EffectParticlePass;
import mafisher.quasar.api.client.particle.EffectParticleShape;
import mafisher.quasar.api.client.particle.ParticleVisualEffectRenderer;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Content-owned orbital-laser visual: an instanced target marker, a full-screen beam operation,
 * and phase-gated micro-particles.
 *
 * <p>The marker locks and spins through {@link ContentOrbitalLaserSettings#TARGET_TICKS}, then fades
 * under the descending beam head. From descent onward the screen operation draws the volumetric
 * beam, impact flash, shock ring, ground contact glow, collapse flash, and aftermath rim glow. The
 * particle fields add sparks and smoke during the burn, embers through the collapse, and smoke in
 * the aftermath. The marker world pass is empty once the handoff finishes so no invisible geometry
 * is submitted.</p>
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class ContentOrbitalLaserRenderer
    implements InstancedVisualEffectRenderer, ScreenEffectRenderer, ParticleVisualEffectRenderer {
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
    private static final ResourceLocation PARTICLE_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(
            ContentBootstrap.NAMESPACE, "textures/effect/particle_dot.png"
        );
    private static final int SUSTAIN_START_TICK = ContentOrbitalLaserSettings.phasePlan()
        .phaseStartTick(ContentOrbitalLaserSettings.PHASE_SUSTAIN);
    private static final int COLLAPSE_START_TICK = ContentOrbitalLaserSettings.phasePlan()
        .phaseStartTick(ContentOrbitalLaserSettings.PHASE_COLLAPSE);
    private static final int AFTERMATH_START_TICK = ContentOrbitalLaserSettings.phasePlan()
        .phaseStartTick(ContentOrbitalLaserSettings.PHASE_AFTERMATH);
    private static final EffectParticleField SPARKS_FIELD = particleField(
        192,
        26,
        new EffectParticleShape.Disk(
            new Vec3(0.0, 0.02, 0.0), new Vec3(0.0, 1.0, 0.0), 0.55, 1.0
        ),
        new EffectParticleMotion(
            EffectParticleMotion.VelocityMode.DIRECTIONAL,
            new Vec3(0.0, 1.0, 0.0),
            new EffectParticleMotion.SpeedRange(0.02, 0.06),
            0.55,
            0.002,
            0.10,
            0.02
        ),
        particleAppearance(0.025F, 0.06F, 0xFFFFE0A0, 0x00100800, 0.08,
            EffectParticleAppearance.Blend.ADDITIVE),
        0x9E3779B97F4A7C15L
    );
    private static final EffectParticleField EMBERS_FIELD = particleField(
        320,
        48,
        new EffectParticleShape.Disk(
            new Vec3(0.0, 0.03, 0.0), new Vec3(0.0, 1.0, 0.0), 0.70, 1.08
        ),
        new EffectParticleMotion(
            EffectParticleMotion.VelocityMode.DIRECTIONAL,
            new Vec3(0.0, 1.0, 0.0),
            new EffectParticleMotion.SpeedRange(0.006, 0.024),
            0.60,
            -0.0002,
            0.08,
            0.015
        ),
        particleAppearance(0.03F, 0.08F, 0xFFFF7A20, 0x00100800, 0.12,
            EffectParticleAppearance.Blend.ADDITIVE),
        0xC2B2AE3D27D4EB4FL
    );
    private static final EffectParticleField SMOKE_FIELD = particleField(
        256,
        60,
        new EffectParticleShape.Disk(
            new Vec3(0.0, 0.03, 0.0), new Vec3(0.0, 1.0, 0.0), 0.55, 1.0
        ),
        new EffectParticleMotion(
            EffectParticleMotion.VelocityMode.DIRECTIONAL,
            new Vec3(0.0, 1.0, 0.0),
            new EffectParticleMotion.SpeedRange(0.004, 0.018),
            0.70,
            -0.0001,
            0.05,
            0.02
        ),
        particleAppearance(0.12F, 0.24F, 0x502A211C, 0x00101010, 0.35,
            EffectParticleAppearance.Blend.ALPHA),
        0x94D049BB133111EBL
    );
    private static final List<EffectParticlePass> SUSTAIN_PASSES = List.of(
        new EffectParticlePass("sustain_sparks", SPARKS_FIELD),
        new EffectParticlePass("sustain_smoke", SMOKE_FIELD)
    );
    private static final List<EffectParticlePass> COLLAPSE_PASSES = List.of(
        new EffectParticlePass("collapse_sparks", SPARKS_FIELD),
        new EffectParticlePass("collapse_embers", EMBERS_FIELD),
        new EffectParticlePass("collapse_smoke", SMOKE_FIELD)
    );
    private static final List<EffectParticlePass> AFTERMATH_PASSES = List.of(
        new EffectParticlePass("aftermath_embers", EMBERS_FIELD),
        new EffectParticlePass("aftermath_smoke", SMOKE_FIELD)
    );
    private static final double PARTICLE_RADIUS = Math.max(
        Math.max(SPARKS_FIELD.spatialRadius(), EMBERS_FIELD.spatialRadius()),
        SMOKE_FIELD.spatialRadius()
    );
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
    public List<EffectParticlePass> particlePasses(EffectRenderContext context) {
        float age = context.ageTicks();
        if (age < SUSTAIN_START_TICK) {
            return List.of();
        }
        if (age < COLLAPSE_START_TICK) {
            return SUSTAIN_PASSES;
        }
        if (age < AFTERMATH_START_TICK) {
            return COLLAPSE_PASSES;
        }
        return AFTERMATH_PASSES;
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
        ContentOrbitalLaserImpulses.tick(context);
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
        double footprint = Math.max(
            ContentOrbitalLaserBeamGeometry.maximumFootprint(BEAM_SETTINGS, context.scale()),
            particleFootprint(context.scale())
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
        double beamRadius = ContentOrbitalLaserBeamGeometry.spatialRadius(
            BEAM_SETTINGS, context.scale()
        );
        double particleRadius = BEAM_SETTINGS.topHeight()
            + particleFootprint(context.scale()) + 0.01;
        return Math.max(beamRadius, particleRadius);
    }

    private static double particleFootprint(float scale) {
        return PARTICLE_RADIUS * Math.max(0.0, scale);
    }

    private static float mix(float from, float to, float factor) {
        return from + (to - from) * Math.max(0.0F, Math.min(1.0F, factor));
    }

    private static EffectParticleField particleField(
        int maxParticles,
        int lifetimeTicks,
        EffectParticleShape shape,
        EffectParticleMotion motion,
        EffectParticleAppearance appearance,
        long salt
    ) {
        return EffectParticleField.builder()
            .maxParticles(maxParticles)
            .lifetimeTicks(lifetimeTicks)
            .mode(EffectParticleField.Mode.STEADY)
            .shape(shape)
            .motion(motion)
            .appearance(appearance)
            .salt(salt)
            .build();
    }

    private static EffectParticleAppearance particleAppearance(
        float sizeStart,
        float sizeEnd,
        int colorStart,
        int colorEnd,
        double softness,
        EffectParticleAppearance.Blend blend
    ) {
        return new EffectParticleAppearance(
            PARTICLE_TEXTURE,
            blend,
            new EffectParticleAppearance.SizeRange(sizeStart, sizeEnd),
            colorStart,
            colorEnd,
            softness
        );
    }
}
