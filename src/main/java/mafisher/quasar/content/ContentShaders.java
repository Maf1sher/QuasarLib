package mafisher.quasar.content;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mafisher.quasar.client.effect.EffectShader;
import net.minecraft.resources.ResourceLocation;

/** Content-owned shader handles. */
final class ContentShaders {
    static final EffectShader RUNE_CIRCLE = EffectShader.create(
        id("rune_circle"), DefaultVertexFormat.BLOCK
    );
    static final EffectShader RUNE_MASK = EffectShader.create(
        id("rune_mask"), DefaultVertexFormat.BLOCK
    );
    static final EffectShader RUNE_GLOW = EffectShader.create(
        id("rune_glow"), DefaultVertexFormat.POSITION
    );

    private ContentShaders() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ContentBootstrap.NAMESPACE, path);
    }
}
