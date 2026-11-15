package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.api.client.particle.EffectParticleAppearance;
import mafisher.quasar.api.client.particle.EffectParticleField;
import mafisher.quasar.api.client.particle.EffectParticleMotion;
import mafisher.quasar.api.client.particle.EffectParticleShape;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ParticleFieldMathTest {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "test", "textures/effect/particle.png"
    );

    @Test
    void sameInputsProduceTheSameSample() {
        EffectParticleField field = field(new EffectParticleShape.Point(),
            EffectParticleMotion.outward(0.1, 0.5, 0.4), 20);
        ParticleFieldMath.Sample first = ParticleFieldMath.sample(field, 1234L, 7.5, 42);
        ParticleFieldMath.Sample second = ParticleFieldMath.sample(field, 1234L, 7.5, 42);

        assertEquals(first.position(), second.position());
        assertEquals(first.size(), second.size(), 0.0);
        assertEquals(first.color(), second.color());
        assertEquals(first.visible(), second.visible());
    }

    @Test
    void differentSeedsAndIndicesDecorrelation() {
        EffectParticleField field = field(new EffectParticleShape.Sphere(Vec3.ZERO, 0.0, 2.0, false),
            EffectParticleMotion.stationary(), 20);
        ParticleFieldMath.Sample first = ParticleFieldMath.sample(field, 1L, 3.0, 10);
        ParticleFieldMath.Sample otherSeed = ParticleFieldMath.sample(field, 2L, 3.0, 10);
        ParticleFieldMath.Sample otherIndex = ParticleFieldMath.sample(field, 1L, 3.0, 11);

        assertNotEquals(first.position(), otherSeed.position());
        assertNotEquals(first.position(), otherIndex.position());
    }

    @Test
    void sphereSpawnStaysInsideTheAuthoredShell() {
        Vec3 center = new Vec3(1.0, 2.0, 3.0);
        EffectParticleField field = field(new EffectParticleShape.Sphere(center, 2.0, 4.0, false),
            EffectParticleMotion.stationary(), 20);
        for (int index = 0; index < 500; index++) {
            double distance = ParticleFieldMath.sample(field, 5L, 0.0, index).position()
                .distanceTo(center);
            assertTrue(distance >= 2.0 - 1.0E-6 && distance <= 4.0 + 1.0E-6,
                () -> "Sphere sample outside shell: " + distance);
        }
    }

    @Test
    void boxDiskAndLineSpawnsStayInsideTheirShapes() {
        EffectParticleField box = field(new EffectParticleShape.Box(
            new Vec3(-1.0, -2.0, -3.0), new Vec3(1.0, 2.0, 3.0)), EffectParticleMotion.stationary(), 20);
        EffectParticleField disk = field(new EffectParticleShape.Disk(
            new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 1.0, 0.0), 1.0, 2.0),
            EffectParticleMotion.stationary(), 20);
        EffectParticleField line = field(new EffectParticleShape.Line(
            Vec3.ZERO, new Vec3(0.0, 0.0, 10.0), 0.5), EffectParticleMotion.stationary(), 20);
        for (int index = 0; index < 300; index++) {
            Vec3 boxPoint = ParticleFieldMath.sample(box, 8L, 0.0, index).position();
            assertTrue(Math.abs(boxPoint.x) <= 1.0 + 1.0E-6
                && Math.abs(boxPoint.y) <= 2.0 + 1.0E-6
                && Math.abs(boxPoint.z) <= 3.0 + 1.0E-6);
            Vec3 diskPoint = ParticleFieldMath.sample(disk, 8L, 0.0, index).position();
            assertEquals(1.0, diskPoint.y, 1.0E-6);
            double radius = Math.hypot(diskPoint.x, diskPoint.z);
            assertTrue(radius >= 1.0 - 1.0E-6 && radius <= 2.0 + 1.0E-6);
            Vec3 linePoint = ParticleFieldMath.sample(line, 8L, 0.0, index).position();
            assertTrue(linePoint.z >= -1.0E-6 && linePoint.z <= 10.0 + 1.0E-6);
            assertTrue(Math.hypot(linePoint.x, linePoint.y) <= 0.5 + 1.0E-6);
        }
    }

    @Test
    void steadyFieldIsSeededAcrossTheLifetime() {
        EffectParticleField field = field(new EffectParticleShape.Point(),
            EffectParticleMotion.stationary(), 40);
        double minimum = Double.MAX_VALUE;
        double maximum = -Double.MAX_VALUE;
        for (int index = 0; index < 200; index++) {
            double age = ParticleFieldMath.particleAge(field, 0, 0, index, 0.0);
            minimum = Math.min(minimum, age);
            maximum = Math.max(maximum, age);
        }
        assertTrue(minimum >= 0.0);
        assertTrue(maximum < 40.0);
        assertTrue(maximum - minimum > 20.0);
    }

    @Test
    void burstFieldHidesAfterItsLifetime() {
        EffectParticleField field = EffectParticleField.builder()
            .maxParticles(10)
            .lifetimeTicks(20)
            .mode(EffectParticleField.Mode.BURST)
            .shape(new EffectParticleShape.Point())
            .motion(EffectParticleMotion.stationary())
            .appearance(appearance())
            .build();

        assertTrue(ParticleFieldMath.sample(field, 1L, 19.9, 0).visible());
        assertFalse(ParticleFieldMath.sample(field, 1L, 20.0, 0).visible());
        assertFalse(ParticleFieldMath.sample(field, 1L, 21.0, 0).visible());
    }

    @Test
    void positionsDoNotDependOnTheRequestedCount() {
        EffectParticleField small = EffectParticleField.builder()
            .maxParticles(50)
            .lifetimeTicks(20)
            .shape(new EffectParticleShape.Sphere(Vec3.ZERO, 1.0, 2.0, false))
            .motion(EffectParticleMotion.outward(0.1, 0.2, 0.3))
            .appearance(appearance())
            .build();
        EffectParticleField large = EffectParticleField.builder()
            .maxParticles(500)
            .lifetimeTicks(20)
            .shape(new EffectParticleShape.Sphere(Vec3.ZERO, 1.0, 2.0, false))
            .motion(EffectParticleMotion.outward(0.1, 0.2, 0.3))
            .appearance(appearance())
            .build();

        assertEquals(
            ParticleFieldMath.sample(small, 9L, 5.0, 7).position(),
            ParticleFieldMath.sample(large, 9L, 5.0, 7).position()
        );
    }

    @Test
    void spatialRadiusEnclosesEverySampledParticle() {
        EffectParticleField field = EffectParticleField.builder()
            .maxParticles(64)
            .lifetimeTicks(30)
            .shape(new EffectParticleShape.Line(Vec3.ZERO, new Vec3(0.0, 0.0, 4.0), 0.5))
            .motion(new EffectParticleMotion(EffectParticleMotion.VelocityMode.OUTWARD,
                Vec3.ZERO, new EffectParticleMotion.SpeedRange(0.1, 1.0), 0.5, 0.2, 0.3, 0.4))
            .appearance(appearance())
            .build();

        for (int index = 0; index < 400; index++) {
            for (int tick = 0; tick < 30; tick++) {
                double length = ParticleFieldMath.sample(field, 77L, tick, index).position().length();
                assertTrue(length <= field.spatialRadius() + 1.0E-6,
                    () -> "Particle escaped the declared radius: " + length);
            }
        }
    }

    @Test
    void travelDistanceUsesTheClosedFormDragIntegral() {
        assertEquals(2.0, ParticleFieldMath.travelDistance(0.0, 2.0), 0.0);
        assertEquals((1.0 - Math.exp(-1.0)) / 0.5, ParticleFieldMath.travelDistance(0.5, 2.0),
            1.0E-12);
    }

    @Test
    void colorInterpolationHitsBothEndpoints() {
        assertEquals(0x11223344, ParticleFieldMath.lerpColor(0x11223344, 0xFFEEDDCC, 0.0));
        assertEquals(0xFFEEDDCC, ParticleFieldMath.lerpColor(0x11223344, 0xFFEEDDCC, 1.0));
    }

    private static EffectParticleField field(EffectParticleShape shape, EffectParticleMotion motion,
        int lifetimeTicks) {
        return EffectParticleField.builder()
            .maxParticles(64)
            .lifetimeTicks(lifetimeTicks)
            .shape(shape)
            .motion(motion)
            .appearance(appearance())
            .build();
    }

    private static EffectParticleAppearance appearance() {
        return new EffectParticleAppearance(TEXTURE, EffectParticleAppearance.Blend.ALPHA,
            new EffectParticleAppearance.SizeRange(1.0, 0.5), 0xFFFFFFFF, 0x00000000, 0.5);
    }
}
