package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Objects;
import java.util.function.Function;
import mafisher.quasar.api.client.EffectRenderTypeOptions;
import mafisher.quasar.api.client.EffectShader;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Built-in render types and the shared shader-backed render-type factory. */
@org.jetbrains.annotations.ApiStatus.Internal
public final class EffectRenderTypes {
    private static final RenderStateShard.OutputStateShard SCREEN_MASK_TARGET =
        new RenderStateShard.OutputStateShard("quasar_lib_screen_mask_target", () -> { }, () -> { });
    private static final Function<ShaderTypeKey, RenderType> SHADER_TYPES = Util.memoize(
        EffectRenderTypes::createShaderType
    );
    private static final Function<ResourceLocation, RenderType> VISUAL = Util.memoize(
        EffectRenderTypes::createVisual
    );

    private EffectRenderTypes() {
    }

    public static RenderType visual(ResourceLocation texture) {
        return VISUAL.apply(texture);
    }

    public static RenderType instancedWorld(ResourceLocation texture) {
        return shaderWorld(EffectShaders.INSTANCED_VISUAL, texture, EffectRenderTypeOptions.WORLD_DEFAULT);
    }

    public static RenderType screenMask(ResourceLocation texture) {
        return shaderMask(EffectShaders.SCREEN_MASK, texture, EffectRenderTypeOptions.MASK_DEFAULT);
    }

    public static RenderType shaderWorld(EffectShader shader, ResourceLocation texture,
        EffectRenderTypeOptions options) {
        return SHADER_TYPES.apply(new ShaderTypeKey(shader, texture, options, false));
    }

    public static RenderType shaderMask(EffectShader shader, ResourceLocation texture,
        EffectRenderTypeOptions options) {
        return SHADER_TYPES.apply(new ShaderTypeKey(shader, texture, options, true));
    }

    /** Stable render-type name used by the shader-type cache and diagnostics. */
    static String renderTypeName(EffectShader shader, ResourceLocation texture,
        EffectRenderTypeOptions options, boolean mask) {
        Objects.requireNonNull(shader, "shader");
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(options, "options");
        String shaderName = shader.id().toString().replace(':', '_').replace('/', '_');
        String textureName = texture.toString().replace(':', '_').replace('/', '_');
        return "quasar_shader_" + shaderName + "_" + textureName
            + (mask ? "_mask" : "") + "_" + options.cacheKey();
    }

    private static RenderType createShaderType(ShaderTypeKey key) {
        EffectRenderTypeOptions options = key.options();
        RenderType.CompositeState state = RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(key.shader()::shaderInstance))
            .setTextureState(new RenderStateShard.TextureStateShard(key.texture(), false, true))
            .setTransparencyState(transparencyState(options.transparency()))
            .setDepthTestState(options.depthTest()
                ? RenderStateShard.LEQUAL_DEPTH_TEST
                : RenderStateShard.NO_DEPTH_TEST)
            .setCullState(options.cull() ? RenderStateShard.CULL : RenderStateShard.NO_CULL)
            .setLightmapState(options.lightmap() ? RenderStateShard.LIGHTMAP : RenderStateShard.NO_LIGHTMAP)
            .setOutputState(key.mask() ? SCREEN_MASK_TARGET : RenderStateShard.PARTICLES_TARGET)
            .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(
                options.writeColor(), options.writeDepth()))
            .createCompositeState(true);
        return RenderType.create(
            key.renderTypeName(),
            key.shader().format(),
            VertexFormat.Mode.QUADS,
            2_097_152,
            true,
            options.sort(),
            state
        );
    }

    private static RenderStateShard.TransparencyStateShard transparencyState(
        EffectRenderTypeOptions.Transparency transparency) {
        return switch (transparency) {
            case NONE -> RenderStateShard.NO_TRANSPARENCY;
            case TRANSLUCENT -> RenderStateShard.TRANSLUCENT_TRANSPARENCY;
            case ADDITIVE -> RenderStateShard.ADDITIVE_TRANSPARENCY;
        };
    }

    private static RenderType createVisual(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, true))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOutputState(RenderStateShard.PARTICLES_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(true);
        return RenderType.create(
            "quasar_lib_translucent_visual_effect",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2_097_152,
            true,
            true,
            state
        );
    }

    private record ShaderTypeKey(EffectShader shader, ResourceLocation texture,
        EffectRenderTypeOptions options, boolean mask) {
        private ShaderTypeKey {
            Objects.requireNonNull(shader, "shader");
            Objects.requireNonNull(texture, "texture");
            Objects.requireNonNull(options, "options");
        }

        private String renderTypeName() {
            return EffectRenderTypes.renderTypeName(this.shader, this.texture, this.options, this.mask);
        }
    }
}
