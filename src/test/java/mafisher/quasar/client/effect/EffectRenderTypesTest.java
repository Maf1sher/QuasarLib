package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mafisher.quasar.api.client.EffectRenderTypeOptions;
import mafisher.quasar.api.client.EffectShader;
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
    void buildsAllBuiltInTypes() {
        assertNotNull(EffectRenderTypes.visual(TEXTURE));
        assertNotNull(EffectRenderTypes.debugVisual(TEXTURE));
        assertNotNull(EffectRenderTypes.blackHoleCore(TEXTURE));
        assertNotNull(EffectRenderTypes.blackHoleDisk(TEXTURE));
        assertNotNull(EffectRenderTypes.instancedWorld(TEXTURE));
        assertNotNull(EffectRenderTypes.screenMask(TEXTURE));
        assertNotNull(EffectRenderTypes.blackHoleScreenMask(TEXTURE));
        assertNotNull(EffectRenderTypes.entityUnsorted(TEXTURE));
    }

    @Test
    void cachesBuiltInShaderTypes() {
        assertSame(EffectRenderTypes.debugVisual(TEXTURE), EffectRenderTypes.debugVisual(TEXTURE));
        assertSame(EffectRenderTypes.blackHoleCore(TEXTURE), EffectRenderTypes.blackHoleCore(TEXTURE));
        assertSame(EffectRenderTypes.blackHoleDisk(TEXTURE), EffectRenderTypes.blackHoleDisk(TEXTURE));
        assertSame(EffectRenderTypes.instancedWorld(TEXTURE), EffectRenderTypes.instancedWorld(TEXTURE));
        assertSame(EffectRenderTypes.screenMask(TEXTURE), EffectRenderTypes.screenMask(TEXTURE));
        assertSame(EffectRenderTypes.blackHoleScreenMask(TEXTURE),
            EffectRenderTypes.blackHoleScreenMask(TEXTURE));
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
