package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import mafisher.quasar.api.client.particle.EffectParticleAppearance;
import mafisher.quasar.api.client.render.EffectRenderTypeOptions;
import mafisher.quasar.api.client.render.EffectShader;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Built-in render types and the shared shader-backed render-type factory. */
@org.jetbrains.annotations.ApiStatus.Internal
@SuppressWarnings("PMD.TooManyMethods")
public final class EffectRenderTypes {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectRenderTypes.class);
    private static final RenderStateShard.OutputStateShard SCREEN_MASK_TARGET =
        new RenderStateShard.OutputStateShard("quasar_lib_screen_mask_target", () -> { }, () -> { });
    private static final Function<ShaderTypeKey, RenderType> SHADER_TYPES = Util.memoize(
        EffectRenderTypes::createShaderType
    );
    private static final Function<ResourceLocation, RenderType> VISUAL = Util.memoize(
        EffectRenderTypes::createVisual
    );
    private static final Function<ResourceLocation, RenderType> INSTANCED_VISUAL = Util.memoize(
        EffectRenderTypes::createInstancedVisual
    );
    private static final Map<RenderType, StaticBatchMaterial> STATIC_BATCH_MATERIALS =
        new ConcurrentHashMap<>();
    private static final Set<RenderType> WARNED_BATCH_MATERIALS = ConcurrentHashMap.newKeySet();

    private EffectRenderTypes() {
    }

    public static RenderType visual(ResourceLocation texture) {
        RenderType normal = VISUAL.apply(texture);
        STATIC_BATCH_MATERIALS.putIfAbsent(normal, new StaticBatchMaterial(
            INSTANCED_VISUAL.apply(texture), EffectShaders.INSTANCED_TRANSLUCENT, null
        ));
        return normal;
    }

    public static RenderType instancedWorld(ResourceLocation texture) {
        return shaderWorld(EffectShaders.INSTANCED_VISUAL, texture, EffectRenderTypeOptions.WORLD_DEFAULT);
    }

    /** Returns the cached engine particle render type for one texture and blend mode. */
    public static RenderType particleWorld(ResourceLocation texture,
        EffectParticleAppearance.Blend blend) {
        EffectRenderTypeOptions options = blend == EffectParticleAppearance.Blend.ADDITIVE
            ? EffectRenderTypeOptions.PARTICLE_ADDITIVE
            : EffectRenderTypeOptions.PARTICLE_ALPHA;
        return shaderWorld(EffectShaders.MICRO_PARTICLES, texture, options);
    }

    public static RenderType screenMask(ResourceLocation texture) {
        return shaderMask(EffectShaders.SCREEN_MASK, texture, EffectRenderTypeOptions.MASK_DEFAULT);
    }

    public static RenderType shaderWorld(EffectShader shader, ResourceLocation texture,
        EffectRenderTypeOptions options) {
        ShaderTypeKey key = new ShaderTypeKey(shader, texture, options, false);
        RenderType normal = SHADER_TYPES.apply(key);
        shader.instancedVariant().ifPresent(instanced -> STATIC_BATCH_MATERIALS.putIfAbsent(
            normal,
            new StaticBatchMaterial(
                SHADER_TYPES.apply(new ShaderTypeKey(instanced, texture, options, false)),
                instanced,
                shader
            )
        ));
        return normal;
    }

    /**
     * Returns the instanced twin for a world render type that supports automatic static-mesh
     * batching, or {@code null} when the material has no variant or the variant is not ready.
     */
    static @Nullable RenderType staticBatchType(RenderType normal) {
        Objects.requireNonNull(normal, "normal");
        StaticBatchMaterial material = STATIC_BATCH_MATERIALS.get(normal);
        if (material == null) {
            return null;
        }
        ShaderInstance batch = material.batchShader().shaderInstance();
        if (batch == null) {
            return null;
        }
        if (material.normalShader() != null) {
            ShaderInstance base = material.normalShader().shaderInstance();
            if (base == null) {
                return null;
            }
            if (!base.getFragmentProgram().getName().equals(batch.getFragmentProgram().getName())) {
                warnBatch(normal, "Static batch material " + normal
                    + " uses a different fragment stage; automatic batching is disabled");
                return null;
            }
        }
        return material.renderType();
    }

    /** Package-private view used by tests to inspect the declared material pairing. */
    static @Nullable StaticBatchMaterial staticBatchMaterial(RenderType normal) {
        return STATIC_BATCH_MATERIALS.get(normal);
    }

    /** Clears one-shot diagnostics so a shader reload can retry the pairing. */
    static void onResourceReload() {
        WARNED_BATCH_MATERIALS.clear();
    }

    private static void warnBatch(RenderType normal, String message) {
        if (WARNED_BATCH_MATERIALS.add(normal)) {
            LOGGER.warn(message);
        }
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
        return RenderType.create(
            "quasar_lib_translucent_visual_effect",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2_097_152,
            true,
            true,
            visualState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER, texture)
        );
    }

    private static RenderType createInstancedVisual(ResourceLocation texture) {
        RenderStateShard.ShaderStateShard shader = new RenderStateShard.ShaderStateShard(
            EffectShaders.INSTANCED_TRANSLUCENT::shaderInstance
        );
        return RenderType.create(
            "quasar_lib_instance_translucent_visual_effect",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2_097_152,
            true,
            true,
            visualState(shader, texture)
        );
    }

    private static RenderType.CompositeState visualState(RenderStateShard.ShaderStateShard shader,
        ResourceLocation texture) {
        return RenderType.CompositeState.builder()
            .setShaderState(shader)
            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, true))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOutputState(RenderStateShard.PARTICLES_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(true);
    }

    record StaticBatchMaterial(RenderType renderType, EffectShader batchShader,
        @Nullable EffectShader normalShader) {
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
