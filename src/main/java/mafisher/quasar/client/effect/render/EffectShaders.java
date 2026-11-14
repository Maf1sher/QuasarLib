package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.client.render.EffectShader;
import mafisher.quasar.api.client.screen.EmissiveComposite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.ApiStatus;

/** Engine-owned shader handles, registered once per shader load. */
@ApiStatus.Internal
public final class EffectShaders {
    static final EffectShader INSTANCED_VISUAL = EffectShader.create(
        id("instanced_visual"), DefaultVertexFormat.BLOCK
    );
    public static final EffectShader SCREEN_MASK = EffectShader.create(
        id("screen_mask"), DefaultVertexFormat.BLOCK
    );
    public static final EffectShader SCREEN_FULLSCREEN_MASK = EffectShader.create(
        id("screen_fullscreen_mask"), DefaultVertexFormat.BLOCK
    );
    public static final EffectShader SCREEN_COMPOSITE = EffectShader.create(
        id("screen_composite"), DefaultVertexFormat.POSITION
    );
    public static final EffectShader SCREEN_BLOOM = EffectShader.create(
        id("screen_bloom"), DefaultVertexFormat.POSITION
    );
    public static final EffectShader SCREEN_FLASH = EffectShader.create(
        id("screen_flash"), DefaultVertexFormat.POSITION
    );
    public static final EffectShader SCREEN_PRESENT = EffectShader.create(
        id("screen_present"), DefaultVertexFormat.POSITION
    );

    private EffectShaders() {
    }

    public static void registerAll(RegisterShadersEvent event) throws IOException {
        INSTANCED_VISUAL.register(event);
        SCREEN_MASK.register(event);
        SCREEN_FULLSCREEN_MASK.register(event);
        SCREEN_COMPOSITE.register(event);
        SCREEN_BLOOM.register(event);
        SCREEN_FLASH.register(event);
        SCREEN_PRESENT.register(event);
        EmissiveComposite.STANDARD_SHADER.register(event);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, path);
    }
}
