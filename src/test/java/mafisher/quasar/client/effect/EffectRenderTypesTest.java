package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EffectRenderTypesTest {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "test", "textures/effect/test.png"
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
}
