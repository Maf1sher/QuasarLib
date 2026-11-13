package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import mafisher.quasar.api.client.render.EffectCameraSnapshot;
import mafisher.quasar.api.client.render.EffectLod;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class EffectRenderContextTest {
    @Test
    void liveCameraBuildsTheSameCameraRelativeSpace() {
        EffectCameraSnapshot camera = new EffectCameraSnapshot(
            new Vec3(10.0, 20.0, 30.0), new Matrix4f(), new Matrix4f(), 1920, 1080);
        EffectRenderContext context = TestVisualEffects.context(
                TestVisualEffects.EFFECT_ID, UUID.randomUUID(), new Vec3(14.0, 25.0, 6.0),
                2.0F, 100, 0.5F)
            .withLod(EffectLod.FULL)
            .withQuality(EffectQualityTier.HIGH);

        EffectRenderContext withCamera = new EffectRenderContext(
            context.effect(), context.transform(), context.ageTicks(), context.progress(),
            context.partialTick(), context.lod(), context.quality(), context.environment(),
            camera, 1.5F);

        assertTrue(withCamera.hasCamera());
        assertEquals(4.0, withCamera.effectSpace().cameraRelativeOrigin().x(), 0.0);
        assertEquals(5.0, withCamera.effectSpace().cameraRelativeOrigin().y(), 0.0);
        assertEquals(-24.0, withCamera.effectSpace().cameraRelativeOrigin().z(), 0.0);
        assertEquals(1.0F, withCamera.daylight());
        assertEquals(1.0F, withCamera.withLod(new EffectLod(2, 3.0, 4.0)).daylight());
        assertEquals(camera, withCamera.withQuality(EffectQualityTier.LOW).camera());
    }

    @Test
    void legacyConstructorHasNoCameraAndRejectsEffectSpace() {
        EffectRenderContext context = TestVisualEffects.context(
            TestVisualEffects.EFFECT_ID, UUID.randomUUID(), Vec3.ZERO, 1.0F, 100, 0.5F);

        assertFalse(context.hasCamera());
        assertThrows(IllegalStateException.class, context::effectSpace);
    }
}
