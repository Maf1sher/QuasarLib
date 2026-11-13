package mafisher.quasar.content;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import mafisher.quasar.api.client.render.EffectShader;
import net.minecraft.resources.ResourceLocation;

/** Content-owned shader handles. */
public final class ContentShaders {
    public static final EffectShader RUNE_CIRCLE = EffectShader.create(
        id("rune_circle"), DefaultVertexFormat.BLOCK
    );
    public static final EffectShader RUNE_MASK = EffectShader.create(
        id("rune_mask"), DefaultVertexFormat.BLOCK
    );
    public static final EffectShader RUNE_GLOW = EffectShader.create(
        id("rune_glow"), DefaultVertexFormat.POSITION
    );
    public static final EffectShader BLACK_HOLE = EffectShader.create(
        id("black_hole"), DefaultVertexFormat.POSITION
    );
    public static final EffectShader BLACK_HOLE_MASK = EffectShader.create(
        id("black_hole_mask"), DefaultVertexFormat.BLOCK
    );
    public static final EffectShader BLACK_HOLE_MESH = EffectShader.create(
        id("black_hole_mesh"), DefaultVertexFormat.BLOCK
    );
    public static final EffectShader BLACK_HOLE_MESH_MASK = EffectShader.create(
        id("black_hole_mesh_mask"), DefaultVertexFormat.BLOCK
    );

    private ContentShaders() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ContentBootstrap.NAMESPACE, path);
    }
}
