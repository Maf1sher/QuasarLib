package mafisher.quasar.client.effect;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import mafisher.quasar.QuasarLib;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Engine-owned shader handles, registered once per shader load. */
final class EffectShaders {
    static final EffectShader EFFECT_VISUAL = EffectShader.create(
        id("effect_visual"), DefaultVertexFormat.BLOCK
    );
    static final EffectShader BLACK_HOLE_VISUAL = EffectShader.create(
        id("black_hole_visual"), DefaultVertexFormat.BLOCK
    );
    static final EffectShader INSTANCED_VISUAL = EffectShader.create(
        id("instanced_visual"), DefaultVertexFormat.BLOCK
    );
    static final EffectShader EFFECT_DEBRIS = EffectShader.create(
        id("effect_debris"), DefaultVertexFormat.BLOCK
    );
    static final EffectShader SCREEN_MASK = EffectShader.create(
        id("screen_mask"), DefaultVertexFormat.BLOCK
    );
    static final EffectShader BLACK_HOLE_SCREEN_MASK = EffectShader.create(
        id("black_hole_screen_mask"), DefaultVertexFormat.BLOCK
    );
    static final EffectShader SCREEN_COMPOSITE = EffectShader.create(
        id("screen_composite"), DefaultVertexFormat.POSITION
    );
    static final EffectShader SCREEN_BLOOM = EffectShader.create(
        id("screen_bloom"), DefaultVertexFormat.POSITION
    );
    static final EffectShader SCREEN_FLASH = EffectShader.create(
        id("screen_flash"), DefaultVertexFormat.POSITION
    );

    private EffectShaders() {
    }

    static void registerAll(RegisterShadersEvent event) throws IOException {
        EFFECT_VISUAL.register(event);
        BLACK_HOLE_VISUAL.register(event);
        INSTANCED_VISUAL.register(event);
        EFFECT_DEBRIS.register(event);
        SCREEN_MASK.register(event);
        BLACK_HOLE_SCREEN_MASK.register(event);
        SCREEN_COMPOSITE.register(event);
        SCREEN_BLOOM.register(event);
        SCREEN_FLASH.register(event);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, path);
    }
}
