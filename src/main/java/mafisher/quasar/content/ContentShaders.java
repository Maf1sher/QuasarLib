package mafisher.quasar.content;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mafisher.quasar.api.client.EffectShader;
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
    static final EffectShader BLACK_HOLE = EffectShader.create(
        id("black_hole"), DefaultVertexFormat.POSITION
    );
    static final EffectShader BLACK_HOLE_MASK = EffectShader.create(
        id("black_hole_mask"), DefaultVertexFormat.BLOCK
    );

    private ContentShaders() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ContentBootstrap.NAMESPACE, path);
    }
}
