package mafisher.quasar.client.effect;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Built-in render types and the shared shader-backed render-type factory. */
final class EffectRenderTypes {
    private static final RenderStateShard.OutputStateShard SCREEN_MASK_TARGET =
        new RenderStateShard.OutputStateShard("quasar_lib_screen_mask_target", () -> { }, () -> { });
    private static final Function<ShaderTypeKey, RenderType> SHADER_TYPES = Util.memoize(
        EffectRenderTypes::createShaderType
    );
    private static final Function<ResourceLocation, RenderType> VISUAL = Util.memoize(
        EffectRenderTypes::createVisual
    );
    private static final Function<ResourceLocation, RenderType> ENTITY_UNSORTED = Util.memoize(
        EffectRenderTypes::createEntity
    );
    private static final EffectRenderTypeOptions BLACK_HOLE_DISK_OPTIONS = new EffectRenderTypeOptions(
        EffectRenderTypeOptions.Transparency.TRANSLUCENT, true, true, false, false, true, true
    );
    private static final EffectRenderTypeOptions BLACK_HOLE_MASK_OPTIONS = new EffectRenderTypeOptions(
        EffectRenderTypeOptions.Transparency.TRANSLUCENT, true, true, false, false, true, true
    );

    private EffectRenderTypes() {
    }

    static RenderType visual(ResourceLocation texture) {
        return VISUAL.apply(texture);
    }

    static RenderType debugVisual(ResourceLocation texture) {
        return shaderWorld(EffectShaders.EFFECT_VISUAL, texture, EffectRenderTypeOptions.WORLD_DEFAULT);
    }

    static RenderType blackHoleCore(ResourceLocation texture) {
        return shaderWorld(EffectShaders.BLACK_HOLE_VISUAL, texture,
            EffectRenderTypeOptions.WORLD_DEPTH_WRITING);
    }

    static RenderType blackHoleDisk(ResourceLocation texture) {
        return shaderWorld(EffectShaders.BLACK_HOLE_VISUAL, texture, BLACK_HOLE_DISK_OPTIONS);
    }

    static RenderType instancedWorld(ResourceLocation texture) {
        return shaderWorld(EffectShaders.INSTANCED_VISUAL, texture, EffectRenderTypeOptions.WORLD_DEFAULT);
    }

    static RenderType screenMask(ResourceLocation texture) {
        return shaderMask(EffectShaders.SCREEN_MASK, texture, EffectRenderTypeOptions.MASK_DEFAULT);
    }

    static RenderType blackHoleScreenMask(ResourceLocation texture) {
        return shaderMask(EffectShaders.BLACK_HOLE_SCREEN_MASK, texture, BLACK_HOLE_MASK_OPTIONS);
    }

    static RenderType entityUnsorted(ResourceLocation texture) {
        return ENTITY_UNSORTED.apply(texture);
    }

    static RenderType shaderWorld(EffectShader shader, ResourceLocation texture,
        EffectRenderTypeOptions options) {
        return SHADER_TYPES.apply(new ShaderTypeKey(shader, texture, options, false));
    }

    static RenderType shaderMask(EffectShader shader, ResourceLocation texture,
        EffectRenderTypeOptions options) {
        return SHADER_TYPES.apply(new ShaderTypeKey(shader, texture, options, true));
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

    private static RenderType createEntity(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, true))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .setOutputState(RenderStateShard.MAIN_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(true);
        return RenderType.create(
            "quasar_lib_translucent_entity_effect",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            false,
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
            String shaderName = this.shader.id().toString().replace(':', '_').replace('/', '_');
            String textureName = this.texture.toString().replace(':', '_').replace('/', '_');
            return "quasar_shader_" + shaderName + "_" + textureName
                + (this.mask ? "_mask" : "") + "_" + Integer.toHexString(this.options.hashCode());
        }
    }
}
