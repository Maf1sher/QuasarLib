package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class QuasarRenderTypesTest {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "test", "textures/effect/test.png"
    );

    @Test
    void cachesWorldVisualPerTexture() {
        assertSame(QuasarRenderTypes.worldVisual(TEXTURE), QuasarRenderTypes.worldVisual(TEXTURE));
    }

    @Test
    void cachesScreenEffectMaskPerTexture() {
        assertSame(QuasarRenderTypes.screenEffectMask(TEXTURE),
            QuasarRenderTypes.screenEffectMask(TEXTURE));
    }
}
