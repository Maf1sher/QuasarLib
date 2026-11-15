package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import mafisher.quasar.api.client.particle.EffectParticleAppearance;
import mafisher.quasar.api.client.render.EffectRenderTypeOptions;
import mafisher.quasar.api.client.render.EffectShader;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EffectRenderTypesTest {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "test", "textures/effect/test.png"
    );
    private static final EffectShader SHADER = EffectShader.create(
        ResourceLocation.fromNamespaceAndPath("test", "shader/visual"), DefaultVertexFormat.BLOCK
    );

    @Test
    void buildsEngineOwnedTypes() {
        assertNotNull(EffectRenderTypes.visual(TEXTURE));
        assertNotNull(EffectRenderTypes.instancedWorld(TEXTURE));
        assertNotNull(EffectRenderTypes.screenMask(TEXTURE));
    }

    @Test
    void cachesEngineOwnedShaderTypes() {
        assertSame(EffectRenderTypes.instancedWorld(TEXTURE), EffectRenderTypes.instancedWorld(TEXTURE));
        assertSame(EffectRenderTypes.screenMask(TEXTURE), EffectRenderTypes.screenMask(TEXTURE));
    }

    @Test
    void buildsAndCachesParticleTypes() {
        var alpha = EffectRenderTypes.particleWorld(TEXTURE,
            EffectParticleAppearance.Blend.ALPHA);
        var additive = EffectRenderTypes.particleWorld(TEXTURE,
            EffectParticleAppearance.Blend.ADDITIVE);

        assertSame(alpha, EffectRenderTypes.particleWorld(TEXTURE,
            EffectParticleAppearance.Blend.ALPHA));
        assertNotEquals(alpha, additive);
        assertEquals(DefaultVertexFormat.BLOCK, alpha.format());
        assertEquals(VertexFormat.Mode.QUADS, alpha.mode());
        assertNull(EffectRenderTypes.staticBatchMaterial(alpha));
    }

    @Test
    void pairsPlainVisualTypesWithInstancedTranslucency() {
        var normal = EffectRenderTypes.visual(TEXTURE);
        var material = EffectRenderTypes.staticBatchMaterial(normal);

        assertNotNull(material);
        assertNotEquals(normal, material.renderType());
        assertEquals(DefaultVertexFormat.BLOCK, material.renderType().format());
        assertEquals(normal.mode(), material.renderType().mode());
        assertNull(EffectRenderTypes.staticBatchType(normal));
    }

    @Test
    void pairsShaderWorldTypesOnlyWhenAnInstancedVariantExists() {
        ResourceLocation instancedId = ResourceLocation.fromNamespaceAndPath("test", "shader/visual_instanced");
        EffectShader batched = EffectShader.createBatched(SHADER.id(), instancedId,
            DefaultVertexFormat.BLOCK);
        var paired = EffectRenderTypes.shaderWorld(batched, TEXTURE,
            EffectRenderTypeOptions.WORLD_DEFAULT);
        var material = EffectRenderTypes.staticBatchMaterial(paired);

        assertNotNull(material);
        assertNotEquals(paired, material.renderType());
        assertNull(EffectRenderTypes.staticBatchMaterial(EffectRenderTypes.shaderWorld(
            SHADER, TEXTURE, EffectRenderTypeOptions.WORLD_DEPTH_WRITING)));
        assertNull(EffectRenderTypes.staticBatchMaterial(EffectRenderTypes.shaderMask(
            SHADER, TEXTURE, EffectRenderTypeOptions.MASK_DEFAULT)));
    }

    @Test
    void disablesBatchingWhenTheVariantFragmentDiffers() {
        ResourceLocation instancedId = ResourceLocation.fromNamespaceAndPath("test", "shader/visual_instanced");
        EffectShader shader = EffectShader.createBatched(SHADER.id(), instancedId,
            DefaultVertexFormat.BLOCK);
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("test", "textures/effect/pair.png");
        var paired = EffectRenderTypes.shaderWorld(shader, texture,
            EffectRenderTypeOptions.WORLD_DEFAULT);
        EffectShader instanced = shader.instancedVariant().orElseThrow();

        Program sharedFragment = mock(Program.class);
        when(sharedFragment.getName()).thenReturn("test:shader/fragment");
        ShaderInstance normalInstance = mock(ShaderInstance.class);
        ShaderInstance batchInstance = mock(ShaderInstance.class);
        when(normalInstance.getFragmentProgram()).thenReturn(sharedFragment);
        when(batchInstance.getFragmentProgram()).thenReturn(sharedFragment);
        shader.accept(normalInstance);
        instanced.accept(batchInstance);

        assertNotNull(EffectRenderTypes.staticBatchType(paired));

        Program otherFragment = mock(Program.class);
        when(otherFragment.getName()).thenReturn("test:shader/other_fragment");
        ShaderInstance otherBatch = mock(ShaderInstance.class);
        when(otherBatch.getFragmentProgram()).thenReturn(otherFragment);
        instanced.accept(otherBatch);

        assertNull(EffectRenderTypes.staticBatchType(paired));
    }

    @Test
    void dynamicInstancedTypesHaveNoStaticTwin() {
        assertNull(EffectRenderTypes.staticBatchMaterial(
            QuasarRenderTypes.instancedWorldVisual(TEXTURE)));
    }

    @Test
    void buildsStableRenderTypeNamesFromOptions() {
        String world = EffectRenderTypes.renderTypeName(
            SHADER, TEXTURE, EffectRenderTypeOptions.WORLD_DEFAULT, false
        );
        String mask = EffectRenderTypes.renderTypeName(
            SHADER, TEXTURE, EffectRenderTypeOptions.WORLD_DEFAULT, true
        );
        String depthWriting = EffectRenderTypes.renderTypeName(
            SHADER, TEXTURE, EffectRenderTypeOptions.WORLD_DEPTH_WRITING, false
        );

        assertEquals(world, EffectRenderTypes.renderTypeName(
            SHADER, TEXTURE, EffectRenderTypeOptions.WORLD_DEFAULT, false
        ));
        assertNotEquals(world, mask);
        assertNotEquals(world, depthWriting);
        assertEquals(
            "TRANSLUCENT_depth_color_nowrite_cull_lightmap_sort",
            EffectRenderTypeOptions.WORLD_DEFAULT.cacheKey()
        );
    }
}
