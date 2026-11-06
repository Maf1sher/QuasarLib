package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EffectShaderTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "shader");

    @Test
    void createsHandleWithoutLoading() {
        EffectShader shader = EffectShader.create(ID, DefaultVertexFormat.BLOCK);

        assertEquals(ID, shader.id());
        assertEquals(DefaultVertexFormat.BLOCK, shader.format());
        assertTrue(shader.instance().isEmpty());
    }

    @Test
    void acceptsLoadedInstance() {
        EffectShader shader = EffectShader.create(ID, DefaultVertexFormat.BLOCK);
        ShaderInstance instance = mock(ShaderInstance.class);

        shader.accept(instance);

        assertSame(instance, shader.instance().orElseThrow());
        assertSame(instance, shader.shaderInstance());
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class,
            () -> EffectShader.create(null, DefaultVertexFormat.BLOCK));
        assertThrows(NullPointerException.class,
            () -> EffectShader.create(ID, null));

        EffectShader shader = EffectShader.create(ID, DefaultVertexFormat.BLOCK);
        assertThrows(NullPointerException.class, () -> shader.accept(null));
    }
}
