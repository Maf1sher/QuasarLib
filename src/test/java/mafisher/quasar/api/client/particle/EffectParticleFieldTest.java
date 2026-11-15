package mafisher.quasar.api.client.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectParticleFieldTest {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "test", "textures/effect/particle.png"
    );

    @Test
    void buildsAValidFieldAndExposesValues() {
        EffectParticleAppearance appearance = appearance();
        EffectParticleField field = EffectParticleField.builder()
            .maxParticles(128)
            .lifetimeTicks(20)
            .mode(EffectParticleField.Mode.BURST)
            .shape(new EffectParticleShape.Sphere(Vec3.ZERO, 1.0, 2.0, true))
            .motion(EffectParticleMotion.outward(0.1, 0.4, 0.5))
            .appearance(appearance)
            .salt(42L)
            .build();

        assertEquals(128, field.maxParticles());
        assertEquals(20, field.lifetimeTicks());
        assertEquals(EffectParticleField.Mode.BURST, field.mode());
        assertEquals(42L, field.salt());
        assertSame(appearance, field.appearance());
        assertEquals(0.4, field.motion().speed().max(), 1.0E-6);
        assertTrue(field.spatialRadius() > 2.0);
    }

    @Test
    void requiresAnAppearance() {
        assertThrows(IllegalStateException.class, () -> EffectParticleField.builder().build());
    }

    @Test
    void validatesCountsAndLifetimes() {
        assertThrows(IllegalArgumentException.class,
            () -> EffectParticleField.builder().maxParticles(0));
        assertThrows(IllegalArgumentException.class,
            () -> EffectParticleField.builder().maxParticles(EffectParticleField.MAX_PARTICLES + 1));
        assertThrows(IllegalArgumentException.class,
            () -> EffectParticleField.builder().lifetimeTicks(0));
        assertThrows(IllegalArgumentException.class,
            () -> EffectParticleField.builder().lifetimeTicks(
                EffectParticleField.MAX_LIFETIME_TICKS + 1));
    }

    @Test
    void validatesShapes() {
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleShape.Sphere(Vec3.ZERO, 2.0, 1.0, false));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleShape.Sphere(Vec3.ZERO, -1.0, 1.0, false));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleShape.Sphere(Vec3.ZERO, 0.0,
                EffectParticleShape.MAX_COORDINATE + 1.0, false));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleShape.Box(new Vec3(1.0, 0.0, 0.0), Vec3.ZERO));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleShape.Disk(Vec3.ZERO, Vec3.ZERO, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleShape.Line(Vec3.ZERO, Vec3.ZERO, -1.0));
    }

    @Test
    void validatesMotion() {
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleMotion(EffectParticleMotion.VelocityMode.DIRECTIONAL,
                Vec3.ZERO, new EffectParticleMotion.SpeedRange(2.0, 1.0), 0.0, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleMotion(EffectParticleMotion.VelocityMode.DIRECTIONAL,
                Vec3.ZERO, new EffectParticleMotion.SpeedRange(0.0, 1.0), 1.1, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleMotion(EffectParticleMotion.VelocityMode.DIRECTIONAL,
                Vec3.ZERO, new EffectParticleMotion.SpeedRange(0.0, 1.0), 0.0, 2.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleMotion(EffectParticleMotion.VelocityMode.DIRECTIONAL,
                Vec3.ZERO, new EffectParticleMotion.SpeedRange(0.0, 1.0), 0.0, 0.0, 1.1, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleMotion(EffectParticleMotion.VelocityMode.DIRECTIONAL,
                new Vec3(Double.NaN, 0.0, 0.0),
                new EffectParticleMotion.SpeedRange(0.0, 1.0), 0.0, 0.0, 0.0, 0.0));
    }

    @Test
    void validatesAppearance() {
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleAppearance(TEXTURE, EffectParticleAppearance.Blend.ALPHA,
                new EffectParticleAppearance.SizeRange(0.0, 1.0), 0xFFFFFFFF, 0x00000000, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleAppearance(TEXTURE, EffectParticleAppearance.Blend.ALPHA,
                new EffectParticleAppearance.SizeRange(1.0, 1.0), 0xFFFFFFFF, 0x00000000, -1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticleAppearance(TEXTURE, EffectParticleAppearance.Blend.ALPHA,
                new EffectParticleAppearance.SizeRange(1.0, 1.0), 0xFFFFFFFF, 0x00000000,
                EffectParticleAppearance.MAX_SOFTNESS + 1.0));
    }

    @Test
    void computesAConservativeSpatialRadius() {
        EffectParticleField field = EffectParticleField.builder()
            .maxParticles(16)
            .lifetimeTicks(10)
            .shape(new EffectParticleShape.Point())
            .motion(new EffectParticleMotion(EffectParticleMotion.VelocityMode.DIRECTIONAL,
                Vec3.ZERO, new EffectParticleMotion.SpeedRange(1.0, 1.0), 0.0, 0.0, 0.0, 0.0))
            .appearance(new EffectParticleAppearance(TEXTURE, EffectParticleAppearance.Blend.ALPHA,
                new EffectParticleAppearance.SizeRange(1.0, 1.0), 0xFFFFFFFF, 0x00000000, 0.0))
            .build();

        assertEquals(11.0, field.spatialRadius(), 1.0E-9);
    }

    @Test
    void validatesPassIds() {
        EffectParticleField field = field();
        assertThrows(IllegalArgumentException.class,
            () -> new EffectParticlePass(" ", field));
        assertThrows(NullPointerException.class,
            () -> new EffectParticlePass("pass", null));
    }

    @Test
    void particleOnlyRendererDeclaresNoRegularPasses() {
        ParticleVisualEffectRenderer renderer = new ParticleVisualEffectRenderer() {
        };
        EffectRenderContext context = TestVisualEffects.context(
            TestVisualEffects.EFFECT_ID, UUID.randomUUID(), Vec3.ZERO, 1.0F, 100, 0.5F
        );
        assertEquals(List.of(), renderer.particlePasses(context));
        assertEquals(List.of(), renderer.renderPasses(context));
    }

    private static EffectParticleField field() {
        return EffectParticleField.builder().appearance(appearance()).build();
    }

    private static EffectParticleAppearance appearance() {
        return new EffectParticleAppearance(TEXTURE, EffectParticleAppearance.Blend.ALPHA,
            new EffectParticleAppearance.SizeRange(1.0, 0.5), 0xFFFFFFFF, 0x00000000, 0.5);
    }
}
