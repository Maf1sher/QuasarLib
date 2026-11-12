package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import mafisher.quasar.api.client.EffectLod;
import mafisher.quasar.api.client.EffectLodProfile;
import mafisher.quasar.api.client.EffectRenderContext;
import mafisher.quasar.api.client.ScreenEffectInputs;
import mafisher.quasar.api.client.ScreenEffectMask;
import mafisher.quasar.api.client.ScreenEffectSettings;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentBlackHoleRendererTest {
    @Test
    void usesOnlyTheScreenOperationAndDepthMask() {
        ContentBlackHoleRenderer renderer = new ContentBlackHoleRenderer();

        assertTrue(renderer.renderPasses(context()).isEmpty());
        ScreenEffectSettings settings = renderer.screenEffectSettings(context());
        assertSame(ContentScreenOperations.BLACK_HOLE, settings.operation());
        assertSame(ContentBlackHoleSettings.DEFAULT,
            settings.settings(ContentBlackHoleSettings.class));
        ScreenEffectMask mask = renderer.screenEffectMask(context());
        assertEquals(1.0F, mask.red(), 0.0001F);
        assertEquals(1.0F, mask.green(), 0.0001F);
        assertEquals(1.0F, mask.blue(), 0.0001F);
        assertTrue(mask.strength() > 0.9F);
        assertNotNull(renderer.screenMaskRenderType(context()));
    }

    @Test
    void boundsCoverTheWholeMaskedRaymarchVolume() {
        ContentBlackHoleRenderer renderer = new ContentBlackHoleRenderer();
        EffectRenderContext context = context();

        AABB bounds = renderer.cullingBounds(context);
        double radius = renderer.spatialRadius(context);
        double centerX = context.transform().position().x();
        double centerY = context.transform().position().y();
        double centerZ = context.transform().position().z();

        assertTrue(Double.isFinite(bounds.minX) && Double.isFinite(bounds.maxY));
        assertTrue(Double.isFinite(radius) && radius > 0.0);
        assertEquals(2.0 * radius, bounds.maxX - bounds.minX, 0.001);
        assertEquals(2.0 * radius, bounds.maxY - bounds.minY, 0.001);
        assertEquals(2.0 * radius, bounds.maxZ - bounds.minZ, 0.001);
        assertEquals(centerX, (bounds.minX + bounds.maxX) / 2.0, 0.001);
        assertEquals(centerY, (bounds.minY + bounds.maxY) / 2.0, 0.001);
        assertEquals(centerZ, (bounds.minZ + bounds.maxZ) / 2.0, 0.001);
    }

    @Test
    void declaresDistanceLodForTheRaymarchBudget() {
        ContentBlackHoleRenderer renderer = new ContentBlackHoleRenderer();
        EffectLodProfile profile = renderer.lodProfile();

        int levels = profile.levelCount();
        assertTrue(levels > 1 && levels <= EffectLodProfile.MAX_THRESHOLDS + 1);
        assertEquals(0, profile.select(0.0));
        for (int index = 0; index < levels - 1; index++) {
            double threshold = profile.threshold(index);
            assertEquals(index, profile.select(threshold));
            assertEquals(index + 1, profile.select(Math.nextUp(threshold)));
        }
        assertEquals(levels - 1, profile.select(Double.MAX_VALUE));

        int previousLevel = 0;
        for (double gap = 0.0; gap < 32.0; gap += 0.25) {
            int level = profile.select(gap);
            assertTrue(level >= previousLevel, "gap " + gap);
            previousLevel = level;
        }
    }

    @Test
    void lodIsCarriedIntoTheRenderContext() {
        EffectRenderContext context = context().withLod(new EffectLod(2, 200.0, 8.0));

        assertEquals(2, context.lod().level());
        assertEquals(8.0, context.lod().gapInRadii(), 0.0001);
    }

    @Test
    void requestsFullResolutionCompositionAndBothOptionalCaptures() {
        ContentBlackHoleOperation operation = new ContentBlackHoleOperation();

        assertEquals(1.0F, operation.compositionScaleFloor(), 0.0001F);
        assertSame(ScreenEffectInputs.ALL, operation.requiredInputs());
    }

    private static EffectRenderContext context() {
        StartVisualEffectPayload payload = TestVisualEffects.payload(
            ContentEffectIds.BLACK_HOLE,
            UUID.randomUUID(),
            new Vec3(0.0, 100.0, -160.0),
            0.0F,
            8.0F,
            0.0F,
            12.0F,
            1200,
            12_345L
        );
        return TestVisualEffects.context(payload, 120.0F, 0.1F);
    }
}
