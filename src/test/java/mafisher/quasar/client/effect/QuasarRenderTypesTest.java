package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mafisher.quasar.api.client.EffectRenderTypeOptions;
import mafisher.quasar.api.client.EffectShader;
import mafisher.quasar.api.client.QuasarRenderTypes;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class QuasarRenderTypesTest {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "test", "textures/effect/test.png"
    );
    private static final EffectShader SHADER = EffectShader.create(
        ResourceLocation.fromNamespaceAndPath("test", "shader"), DefaultVertexFormat.BLOCK
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

    @Test
    void cachesShaderWorldPerInputs() {
        assertSame(QuasarRenderTypes.worldVisual(SHADER, TEXTURE),
            QuasarRenderTypes.worldVisual(SHADER, TEXTURE));
        assertSame(QuasarRenderTypes.screenEffectMask(SHADER, TEXTURE),
            QuasarRenderTypes.screenEffectMask(SHADER, TEXTURE));
    }

    @Test
    void distinguishesShaderWorldOptions() {
        assertNotSame(QuasarRenderTypes.worldVisual(SHADER, TEXTURE),
            QuasarRenderTypes.worldVisual(SHADER, TEXTURE,
                EffectRenderTypeOptions.WORLD_DEPTH_WRITING));
    }

    @Test
    void distinguishesMaskFromWorld() {
        assertNotSame(QuasarRenderTypes.worldVisual(SHADER, TEXTURE),
            QuasarRenderTypes.screenEffectMask(SHADER, TEXTURE));
    }

    @Test
    void distinguishesShaderHandles() {
        EffectShader other = EffectShader.create(
            ResourceLocation.fromNamespaceAndPath("test", "other_shader"), DefaultVertexFormat.BLOCK
        );

        assertNotSame(QuasarRenderTypes.worldVisual(SHADER, TEXTURE),
            QuasarRenderTypes.worldVisual(other, TEXTURE));
    }

    @Test
    void rejectsNullShaderInputs() {
        assertThrows(NullPointerException.class,
            () -> QuasarRenderTypes.worldVisual((EffectShader)null, TEXTURE));
        assertThrows(NullPointerException.class,
            () -> QuasarRenderTypes.worldVisual(SHADER, null));
        assertThrows(NullPointerException.class,
            () -> QuasarRenderTypes.worldVisual(SHADER, TEXTURE, null));
    }
}
