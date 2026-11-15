package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.particle.EffectParticleAppearance;
import mafisher.quasar.api.client.particle.EffectParticleField;
import mafisher.quasar.api.client.particle.EffectParticleMotion;
import mafisher.quasar.api.client.particle.EffectParticlePass;
import mafisher.quasar.api.client.particle.EffectParticleShape;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ParticleBatchBuilderTest {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "test", "textures/effect/particle.png"
    );

    @Test
    void drawsOnePassPerField() {
        ParticleBatchBuilder builder = new ParticleBatchBuilder();
        builder.reset(1_000, 0.0, 0.0, 0.0);

        builder.add(context(0.0, 0.0, 0.0), List.of(pass("outer", field(100))));

        List<ParticleBatchBuilder.Draw> draws = builder.draws();
        assertEquals(1, draws.size());
        assertEquals(100, draws.getFirst().count());
        assertEquals("outer", draws.getFirst().id());
    }

    @Test
    void scalesByThePerEffectQualityTier() {
        ParticleBatchBuilder builder = new ParticleBatchBuilder();
        builder.reset(1_000, 0.0, 0.0, 0.0);

        builder.add(context(0.0, 0.0, 0.0).withQuality(EffectQualityTier.LOW),
            List.of(pass("low", field(100))));

        assertEquals(25, builder.draws().getFirst().count());

        ParticleBatchBuilder effective = new ParticleBatchBuilder();
        effective.reset(1_000, 0.0, 0.0, 0.0);
        effective.add(context(0.0, 0.0, 0.0).withQuality(EffectQualityTier.HIGH)
            .withEffectiveQuality(EffectQualityTier.LOW), List.of(pass("low", field(100))));
        assertEquals(25, effective.draws().getFirst().count());

        assertEquals(100, ParticleBatchBuilder.scaledCount(100, EffectQualityTier.HIGH));
        assertEquals(50, ParticleBatchBuilder.scaledCount(100, EffectQualityTier.MEDIUM));
        assertEquals(1, ParticleBatchBuilder.scaledCount(1, EffectQualityTier.LOW));
        assertEquals(0, ParticleBatchBuilder.scaledCount(0, EffectQualityTier.HIGH));
    }

    @Test
    void sharesTheGlobalBudgetWithAFloorPerField() {
        ParticleBatchBuilder builder = new ParticleBatchBuilder();
        builder.reset(50, 0.0, 0.0, 0.0);

        builder.add(context(1.0, 0.0, 0.0), List.of(pass("a", field(100))));
        builder.add(context(2.0, 0.0, 0.0), List.of(pass("b", field(100))));

        List<ParticleBatchBuilder.Draw> draws = builder.draws();
        int total = draws.stream().mapToInt(ParticleBatchBuilder.Draw::count).sum();
        assertEquals(2, draws.size());
        assertEquals(50, total);
        assertTrue(draws.stream().allMatch(draw -> draw.count() >= 12));
        assertTrue(draws.stream().allMatch(draw -> draw.count() <= 100));
    }

    @Test
    void zeroBudgetDrawsNothing() {
        ParticleBatchBuilder builder = new ParticleBatchBuilder();
        builder.reset(0, 0.0, 0.0, 0.0);

        builder.add(context(0.0, 0.0, 0.0), List.of(pass("p", field(100))));

        assertTrue(builder.draws().isEmpty());
        assertTrue(builder.isEmpty() == false);
    }

    @Test
    void rejectsDuplicatePassIdsWithinOneContext() {
        ParticleBatchBuilder builder = new ParticleBatchBuilder();
        builder.reset(1_000, 0.0, 0.0, 0.0);

        assertThrows(IllegalStateException.class,
            () -> builder.add(context(0.0, 0.0, 0.0),
                List.of(pass("duplicate", field(10)), pass("duplicate", field(10)))));
    }

    @Test
    void composesTheModelMatrixRelativeToTheCamera() {
        ParticleBatchBuilder builder = new ParticleBatchBuilder();
        builder.reset(1_000, 4.0, 0.0, 0.0);

        builder.add(context(10.0, 0.0, 0.0), List.of(pass("p", field(10))));

        assertEquals(6.0F, builder.draws().getFirst().model().m30(), 1.0E-6F);
        assertEquals(2.0F, builder.draws().getFirst().model().m00(), 1.0E-6F);
    }

    @Test
    void sortsDrawsBackToFront() {
        ParticleBatchBuilder builder = new ParticleBatchBuilder();
        builder.reset(1_000, 0.0, 0.0, 0.0);

        builder.add(context(1.0, 0.0, 0.0), List.of(pass("near", field(10))));
        builder.add(context(5.0, 0.0, 0.0), List.of(pass("far", field(10))));

        List<ParticleBatchBuilder.Draw> draws = builder.draws();
        assertEquals("far", draws.get(0).id());
        assertEquals("near", draws.get(1).id());
    }

    @Test
    void resetClearsThePreviousFrame() {
        ParticleBatchBuilder builder = new ParticleBatchBuilder();
        builder.reset(1_000, 0.0, 0.0, 0.0);
        builder.add(context(0.0, 0.0, 0.0), List.of(pass("p", field(10))));

        builder.reset(1_000, 0.0, 0.0, 0.0);

        assertTrue(builder.isEmpty());
        assertTrue(builder.draws().isEmpty());
    }

    @Test
    void rejectsInvalidBudgetsAndCameraPositions() {
        ParticleBatchBuilder builder = new ParticleBatchBuilder();

        assertThrows(IllegalArgumentException.class,
            () -> builder.reset(-1, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> builder.reset(1, Double.NaN, 0.0, 0.0));
    }

    private static EffectParticlePass pass(String id, EffectParticleField field) {
        return new EffectParticlePass(id, field);
    }

    private static EffectParticleField field(int maxParticles) {
        return EffectParticleField.builder()
            .maxParticles(maxParticles)
            .lifetimeTicks(20)
            .shape(new EffectParticleShape.Sphere(Vec3.ZERO, 0.0, 1.0, false))
            .motion(EffectParticleMotion.outward(0.1, 0.2, 0.3))
            .appearance(new EffectParticleAppearance(TEXTURE,
                EffectParticleAppearance.Blend.ALPHA,
                new EffectParticleAppearance.SizeRange(1.0, 0.5), 0xFFFFFFFF, 0x00000000, 0.5))
            .build();
    }

    private static EffectRenderContext context(double x, double y, double z) {
        return TestVisualEffects.context(
            TestVisualEffects.payload(
                TestVisualEffects.EFFECT_ID, UUID.randomUUID(), new Vec3(x, y, z), 2.0F, 100, 123L
            ),
            0.0F,
            0.0F
        );
    }
}
