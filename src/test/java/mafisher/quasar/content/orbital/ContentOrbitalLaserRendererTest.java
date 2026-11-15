package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectLod;
import mafisher.quasar.api.client.render.EffectLodProfile;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.screen.ScreenEffectMasks;
import mafisher.quasar.api.client.screen.ScreenEffectSettings;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.effect.EffectParameterState;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.content.ContentScreenOperations;
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
            context(ContentOrbitalLaserSettings.TARGET_TICKS + 10.0F)
        ).get(0);

        assertNotEquals(early.localTransform(), turning.localTransform());
        assertEquals(1.0F, locked.effectDataX(), 1.0E-6F);
        assertEquals(locked.localTransform(), steady.localTransform());
        assertEquals(locked.alpha(), steady.alpha(), 1.0E-6F);
    }

    @Test
    void handsOffMarkerDuringDescentAndStopsBeforeImpact() {
        EffectInstancedPass locked = this.renderer.instancedPasses(context(40.0F)).get(0);
        EffectInstancedPass fading = this.renderer.instancedPasses(context(56.0F)).get(0);

        assertTrue(fading.alpha() < locked.alpha());
        assertTrue(fading.green() > locked.green());
        assertTrue(this.renderer.instancedPasses(context(60.0F)).isEmpty());
    }

    @Test
    void exposesBeamOperationOnlyFromDescentOnward() {
        ScreenEffectSettings settings = this.renderer.screenEffectSettings(context(0.0F));
        assertSame(ContentScreenOperations.ORBITAL_LASER_BEAM, settings.operation());
        assertSame(ContentOrbitalLaserBeamSettings.DEFAULT,
            settings.settings(ContentOrbitalLaserBeamSettings.class));

        assertEquals(0.0F, this.renderer.screenEffectMask(context(0.0F)).strength(), 1.0E-6F);
        assertEquals(1.0F, this.renderer.screenEffectMask(context(40.0F)).strength(), 1.0E-6F);
        assertSame(ScreenEffectMasks.fullscreen(),
            this.renderer.screenMaskRenderType(context(40.0F)));
    }

    @Test
    void cullingBoundsCoverTheBeamTopAndImpactFootprint() {
        EffectRenderContext context = context(60.0F);
        AABB bounds = this.renderer.cullingBounds(context);
        double footprint = ContentOrbitalLaserBeamGeometry.maximumFootprint(
            ContentOrbitalLaserBeamSettings.DEFAULT, context.scale()
        );
        mafisher.quasar.api.effect.EffectPoint base = context.transform().position();

        assertTrue(bounds.maxY >= base.y() + ContentOrbitalLaserSettings.BEAM_TOP_HEIGHT);
        assertTrue(bounds.minX <= base.x() - footprint);
        assertTrue(bounds.maxX >= base.x() + footprint);
        assertTrue(bounds.minZ <= base.z() - footprint);
        assertTrue(bounds.maxZ >= base.z() + footprint);
        assertTrue(this.renderer.spatialRadius(context) >= bounds.maxY - base.y());
    }

    @Test
    void cullingBoundsExtendBelowTheSurfaceForTheBurnFront() {
        EffectParameterState parameters = EffectParameterState.builder()
            .putFloat(ContentOrbitalLaserCinematics.TIP_DEPTH, 30.0F)
            .build();
        EffectRenderContext context = context(120.0F, parameters);
        AABB bounds = this.renderer.cullingBounds(context);
        double footprint = ContentOrbitalLaserBeamGeometry.maximumFootprint(
            ContentOrbitalLaserBeamSettings.DEFAULT, context.scale()
        );

        assertTrue(bounds.minY
            <= context.transform().position().y() - 30.0 - footprint + 1.0E-6);
    }

    @Test
    void declaresDistanceLodWithoutChangingTheFootprint() {
        EffectLodProfile profile = this.renderer.lodProfile();

        int levels = profile.levelCount();
        assertTrue(levels > 1 && levels <= EffectLodProfile.MAX_THRESHOLDS + 1);
        assertEquals(0, profile.select(0.0));
        for (int index = 0; index < levels - 1; index++) {
            double threshold = profile.threshold(index);
            assertEquals(index, profile.select(threshold));
            assertEquals(index + 1, profile.select(Math.nextUp(threshold)));
        }
        assertEquals(levels - 1, profile.select(Double.MAX_VALUE));
    }

    @Test
    void lodAndEffectiveQualityDoNotChangeTheVisibleBounds() {
        EffectRenderContext full = context(120.0F);
        EffectRenderContext reduced = context(120.0F)
            .withLod(new EffectLod(3, 400.0, 12.0))
            .withEffectiveQuality(EffectQualityTier.LOW);

        assertEquals(this.renderer.spatialRadius(full), this.renderer.spatialRadius(reduced), 0.0);
        assertEquals(this.renderer.cullingBounds(full), this.renderer.cullingBounds(reduced));
        assertFalse(this.renderer.renderPasses(reduced).iterator().hasNext());
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

    private static EffectRenderContext context(
        float ageTicks,
        EffectParameterState parameters
    ) {
        EffectRenderContext base = context(ageTicks);
        return new EffectRenderContext(
            base.effect().withParameters(parameters),
            base.transform(),
            base.ageTicks(),
            base.progress(),
            base.partialTick()
        );
    }
}
