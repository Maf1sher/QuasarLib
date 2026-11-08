package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import mafisher.quasar.client.effect.EffectLod;
import mafisher.quasar.client.effect.EffectRenderContext;
import mafisher.quasar.client.effect.ScreenEffectMask;
import mafisher.quasar.client.effect.ScreenEffectSettings;
import mafisher.quasar.client.effect.VisualEffectInstance;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
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

        AABB bounds = renderer.cullingBounds(context());

        assertTrue(Double.isFinite(bounds.minX) && Double.isFinite(bounds.maxY));
        assertEquals(360.0, bounds.maxX - bounds.minX, 0.001);
        assertEquals(360.0, bounds.maxY - bounds.minY, 0.001);
        assertEquals(360.0, bounds.maxZ - bounds.minZ, 0.001);
        assertTrue(renderer.spatialRadius(context()) >= (bounds.maxX - bounds.minX) / 2.0);
    }

    @Test
    void declaresDistanceLodForTheRaymarchBudget() {
        ContentBlackHoleRenderer renderer = new ContentBlackHoleRenderer();

        assertEquals(4, renderer.lodProfile().levelCount());
        assertEquals(0, renderer.lodProfile().select(0.5));
        assertEquals(1, renderer.lodProfile().select(2.0));
        assertEquals(2, renderer.lodProfile().select(6.0));
        assertEquals(3, renderer.lodProfile().select(20.0));
    }

    @Test
    void lodIsCarriedIntoTheRenderContext() {
        EffectRenderContext context = context().withLod(new EffectLod(2, 200.0, 8.0));

        assertEquals(2, context.lod().level());
        assertEquals(8.0, context.lod().gapInRadii(), 0.0001);
    }

    private static EffectRenderContext context() {
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            ContentEffectIds.BLACK_HOLE,
            UUID.randomUUID(),
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
            0.0,
            100.0,
            -160.0,
            0.0F,
            8.0F,
            0.0F,
            12.0F,
            1200,
            0L,
            12_345L
        );
        return new EffectRenderContext(
            new VisualEffectInstance(payload),
            payload.transform(),
            120.0F,
            0.1F,
            0.5F
        );
    }
}
