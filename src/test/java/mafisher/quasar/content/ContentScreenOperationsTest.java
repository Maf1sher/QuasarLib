package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mafisher.quasar.client.effect.ScreenEffectMedium;
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
}
