package mafisher.quasar.content.demo;

import java.util.List;
import mafisher.quasar.api.client.particle.EffectParticleAppearance;
import mafisher.quasar.api.client.particle.EffectParticleField;
import mafisher.quasar.api.client.particle.EffectParticleMotion;
import mafisher.quasar.api.client.particle.EffectParticlePass;
import mafisher.quasar.api.client.particle.EffectParticleShape;
import mafisher.quasar.api.client.particle.ParticleVisualEffectRenderer;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.content.ContentBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Content-owned acceptance demo for stateless GPU micro-particles.
 *
 * <p>Three cached fields exercise the mushroom, tear, and laser archetypes: ground
 * embers, line motes, and an impact fountain. The renderer owns no per-particle
 * state; every particle is derived by the engine shader from the payload seed, the
 * effect age, and the particle instance index.</p>
 */
public final class ContentParticleRenderer implements ParticleVisualEffectRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "textures/effect/particle_dot.png"
    );
    private static final EffectParticlePass EMBERS = new EffectParticlePass("embers",
        field(512, 40, EffectParticleField.Mode.STEADY,
            new EffectParticleShape.Disk(new Vec3(0.0, 0.05, 0.0), new Vec3(0.0, 1.0, 0.0),
                0.5, 3.0),
            new EffectParticleMotion(EffectParticleMotion.VelocityMode.DIRECTIONAL,
                new Vec3(0.0, 1.0, 0.0), new EffectParticleMotion.SpeedRange(0.02, 0.08),
                0.35, 0.002, 0.1, 0.01),
            appearance(0.12F, 0.03F, 0xFFFFB060, 0x00101010, 0.5),
            0x9E3779B97F4A7C15L));
    private static final EffectParticlePass MOTES = new EffectParticlePass("motes",
        field(256, 60, EffectParticleField.Mode.STEADY,
            new EffectParticleShape.Line(new Vec3(-2.0, 0.2, 0.0), new Vec3(2.0, 0.2, 0.0), 0.4),
            new EffectParticleMotion(EffectParticleMotion.VelocityMode.DIRECTIONAL,
                new Vec3(0.2, 1.0, 0.0), new EffectParticleMotion.SpeedRange(0.01, 0.04),
                0.8, 0.0, 0.15, 0.03),
            appearance(0.08F, 0.02F, 0xFF80E0FF, 0x00001820, 0.8),
            0xC2B2AE3D27D4EB4FL));
    private static final EffectParticlePass SPARKS = new EffectParticlePass("sparks",
        field(256, 25, EffectParticleField.Mode.STEADY,
            new EffectParticleShape.Sphere(new Vec3(0.0, 0.3, 0.0), 0.0, 0.2, true),
            new EffectParticleMotion(EffectParticleMotion.VelocityMode.OUTWARD, Vec3.ZERO,
                new EffectParticleMotion.SpeedRange(0.05, 0.2), 0.2, 0.005, 0.2, 0.005),
            appearance(0.06F, 0.01F, 0xFFFFF0C0, 0x00201000, 0.4),
            0x94D049BB133111EBL));
    private static final List<EffectParticlePass> PASSES = List.of(EMBERS, MOTES, SPARKS);
    private static final double LOCAL_RADIUS = PASSES.stream()
        .mapToDouble(pass -> pass.field().spatialRadius())
        .max()
        .orElse(0.0);

    @Override
    public List<EffectParticlePass> particlePasses(EffectRenderContext context) {
        return PASSES;
    }

    @Override
    public double spatialRadius(EffectRenderContext context) {
        return context.scale() * LOCAL_RADIUS;
    }

    @Override
    public AABB cullingBounds(EffectRenderContext context) {
        double radius = spatialRadius(context);
        double x = context.transform().position().x();
        double y = context.transform().position().y();
        double z = context.transform().position().z();
        return new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    private static EffectParticleField field(int maxParticles, int lifetimeTicks,
        EffectParticleField.Mode mode, EffectParticleShape shape, EffectParticleMotion motion,
        EffectParticleAppearance appearance, long salt) {
        return EffectParticleField.builder()
            .maxParticles(maxParticles)
            .lifetimeTicks(lifetimeTicks)
            .mode(mode)
            .shape(shape)
            .motion(motion)
            .appearance(appearance)
            .salt(salt)
            .build();
    }

    private static EffectParticleAppearance appearance(float sizeStart, float sizeEnd,
        int colorStart, int colorEnd, double softness) {
        return new EffectParticleAppearance(TEXTURE, EffectParticleAppearance.Blend.ADDITIVE,
            new EffectParticleAppearance.SizeRange(sizeStart, sizeEnd), colorStart, colorEnd,
            softness);
    }
}
