package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import mafisher.quasar.client.effect.ScreenEffectMedium;
import mafisher.quasar.client.effect.ScreenEffectMediumCrossing;
import mafisher.quasar.client.effect.ScreenEffectMediumPath;
import mafisher.quasar.client.effect.ScreenEffectMediumSpan;
import mafisher.quasar.client.effect.ScreenEffectMediumState;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ContentScreenOperationsTest {
    @Test
    void exposesContentOwnedOperationId() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "rune_glow"),
            ContentScreenOperations.RUNE_GLOW.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "black_hole"),
            ContentScreenOperations.BLACK_HOLE.id());
    }

    @Test
    void suppressesAnAboveWaterEffectWhenTheCameraIsUnderwater() {
        assertTrue(ContentBlackHoleOperation.suppressAboveWater(new ScreenEffectMediumState(
            ScreenEffectMedium.WATER, ScreenEffectMedium.NONE, 4.0F, Float.NaN)));
        assertTrue(ContentBlackHoleOperation.suppressAboveWater(new ScreenEffectMediumState(
            ScreenEffectMedium.WATER, ScreenEffectMedium.LAVA, 4.0F, Float.NaN)));
        assertFalse(ContentBlackHoleOperation.suppressAboveWater(new ScreenEffectMediumState(
            ScreenEffectMedium.WATER, ScreenEffectMedium.WATER, 4.0F, 2.0F)));
        assertFalse(ContentBlackHoleOperation.suppressAboveWater(new ScreenEffectMediumState(
            ScreenEffectMedium.NONE, ScreenEffectMedium.NONE, Float.NaN, Float.NaN)));
        assertFalse(ContentBlackHoleOperation.suppressAboveWater(new ScreenEffectMediumState(
            ScreenEffectMedium.NONE, ScreenEffectMedium.WATER, Float.NaN, 2.0F)));
    }

    @Test
    void selectsTheNearestWaterCrossingAsTheRefractionPlane() {
        ScreenEffectMediumPath path = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 10.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 10.0, 20.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 20.0, 40.0)
        ), List.of(
            new ScreenEffectMediumCrossing(10.0, ScreenEffectMedium.NONE,
                ScreenEffectMedium.WATER, 62.0F),
            new ScreenEffectMediumCrossing(20.0, ScreenEffectMedium.WATER,
                ScreenEffectMedium.NONE, 62.0F)
        ));

        var surface = ContentBlackHoleOperation.mediumSurface(path, 64.0, false);

        assertTrue(surface.active());
        assertEquals(-2.0F, surface.surfaceY(), 1.0E-6F);
        assertFalse(ContentBlackHoleOperation.mediumSurface(path, 64.0, true).active());
        assertFalse(ContentBlackHoleOperation.mediumSurface(
            ScreenEffectMediumPath.EMPTY, 64.0, false).active());
    }

    @Test
    void ignoresACrossingWithoutAWaterSurface() {
        ScreenEffectMediumPath lava = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 5.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.LAVA, 5.0, 10.0)
        ), List.of(new ScreenEffectMediumCrossing(5.0, ScreenEffectMedium.NONE,
            ScreenEffectMedium.LAVA, Float.NaN)));

        assertFalse(ContentBlackHoleOperation.mediumSurface(lava, 64.0, false).active());
    }
}
