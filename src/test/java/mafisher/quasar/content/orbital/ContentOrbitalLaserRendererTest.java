package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentOrbitalLaserRendererTest {
    private final ContentOrbitalLaserRenderer renderer = new ContentOrbitalLaserRenderer();

    @Test
    void drawsOneMarkerPassWithFiniteState() {
        List<EffectInstancedPass> passes = this.renderer.instancedPasses(context(0.0F));

        assertEquals(1, passes.size());
        EffectInstancedPass pass = passes.get(0);
        assertEquals("marker", pass.id());
        assertTrue(pass.localTransform().isFinite());
        assertTrue(pass.red() > pass.green());
        assertTrue(pass.green() >= pass.blue());
        assertTrue(pass.alpha() >= 0.0F && pass.alpha() <= 1.0F);
        assertEquals(0.0F, pass.effectDataX(), 1.0E-6F);
    }

    @Test
    void locksProgressAndStopsSpinningAfterTheTargetPhase() {
        EffectInstancedPass early = this.renderer.instancedPasses(context(0.0F)).get(0);
        EffectInstancedPass turning = this.renderer.instancedPasses(context(20.0F)).get(0);
        EffectInstancedPass locked = this.renderer.instancedPasses(
            context(ContentOrbitalLaserSettings.TARGET_TICKS + 1.0F)
        ).get(0);
        EffectInstancedPass steady = this.renderer.instancedPasses(
            context(ContentOrbitalLaserSettings.TOTAL_TICKS - 1.0F)
        ).get(0);

        assertNotEquals(early.localTransform(), turning.localTransform());
        assertEquals(1.0F, locked.effectDataX(), 1.0E-6F);
        assertEquals(locked.localTransform(), steady.localTransform());
        assertEquals(locked.alpha(), steady.alpha(), 1.0E-6F);
    }

    @Test
    void cullingBoundsAndSpatialRadiusCoverThePulsingMarker() {
        EffectRenderContext context = context(0.0F);
        AABB bounds = this.renderer.cullingBounds(context);
        double radius = this.renderer.spatialRadius(context);

        assertTrue(Double.isFinite(radius));
        assertTrue(radius > 0.0);
        assertTrue(bounds.minX < context.transform().position().x());
        assertTrue(bounds.maxX > context.transform().position().x());
        assertTrue(bounds.minZ < context.transform().position().z());
        assertTrue(bounds.maxZ > context.transform().position().z());
        assertFalse(this.renderer.instancedPasses(context).isEmpty());
    }

    private static EffectRenderContext context(float ageTicks) {
        StartVisualEffectPayload payload = TestVisualEffects.payload(
            ContentEffectIds.ORBITAL_LASER,
            UUID.randomUUID(),
            new Vec3(10.0, 64.0, -5.0),
            6.0F,
            ContentOrbitalLaserSettings.TOTAL_TICKS,
            1234L
        );
        return TestVisualEffects.context(
            payload, ageTicks, ageTicks / ContentOrbitalLaserSettings.TOTAL_TICKS
        );
    }
}
