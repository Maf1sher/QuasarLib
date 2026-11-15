package mafisher.quasar.api.client.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import mafisher.quasar.client.effect.render.EffectShaderCatalog;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jspecify.annotations.Nullable;

/**
 * Stable handle for one shader registered by a consumer mod.
 *
 * <p>Create the handle once, register it from NeoForge's client-only
 * {@link RegisterShadersEvent}, and build render types from it. The handle
 * resolves the current {@link ShaderInstance} after every shader reload, so
 * render types may be cached for the lifetime of the client. Do not cache the
 * instance returned by {@link #instance()}.</p>
 *
 * <p>Shader assets live under {@code assets/<namespace>/shaders/core/<path>.json}
 * for the {@link ResourceLocation} passed to {@link #create(ResourceLocation, VertexFormat)}.
 * The vertex format must match the attributes declared by that shader.</p>
 *
 * <p>The engine records registered handles and validates them before a client
 * resource reload. A failed candidate keeps the previously loaded program.</p>
 */
public final class EffectShader {
    private final ResourceLocation id;
    private final VertexFormat format;
    private final @Nullable EffectShader instancedVariant;
    private volatile ShaderInstance instance;

    private EffectShader(ResourceLocation id, VertexFormat format,
        @Nullable EffectShader instancedVariant) {
        this.id = Objects.requireNonNull(id, "id");
        this.format = Objects.requireNonNull(format, "format");
        this.instancedVariant = instancedVariant;
    }

    /**
     * Creates an unregistered handle. Registration happens through
     * {@link #register(RegisterShadersEvent)}.
     *
     * @param id the shader ID, for example {@code mymod:shockwave}
     * @param format the vertex format declared by the shader
     * @return a new handle
     */
    public static EffectShader create(ResourceLocation id, VertexFormat format) {
        return new EffectShader(id, format, null);
    }

    /**
     * Creates a handle with an instanced variant for automatic static-mesh batching.
     *
     * <p>The normal program is used by unbatched passes. The instanced program must use the
     * engine's instanced vertex contract and the same fragment stage as the normal program;
     * the engine disables batching when the fragment programs differ or the variant is not
     * loaded. Both programs are registered by {@link #register(RegisterShadersEvent)}.</p>
     *
     * @param id the normal shader ID
     * @param instancedId the instanced-variant shader ID
     * @param format the vertex format shared by both programs
     * @return a new handle with an instanced variant
     */
    public static EffectShader createBatched(ResourceLocation id, ResourceLocation instancedId,
        VertexFormat format) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(instancedId, "instancedId");
        Objects.requireNonNull(format, "format");
        if (id.equals(instancedId)) {
            throw new IllegalArgumentException("A batched shader needs a distinct instanced variant ID");
        }
        EffectShader instanced = new EffectShader(instancedId, format, null);
        return new EffectShader(id, format, instanced);
    }

    /**
     * Registers this handle's shader for the current shader load. Call this from
     * a {@link RegisterShadersEvent} handler on the client mod bus. A batched handle
     * registers its instanced variant as well.
     *
     * @param event the NeoForge shader registration event
     * @throws IOException if the shader assets cannot be read
     */
    public void register(RegisterShadersEvent event) throws IOException {
        Objects.requireNonNull(event, "event");
        EffectShaderCatalog.record(this);
        event.registerShader(
            new ShaderInstance(event.getResourceProvider(), this.id, this.format),
            this::accept
        );
        if (this.instancedVariant != null) {
            this.instancedVariant.register(event);
        }
    }

    /** Returns the shader ID used to load {@code shaders/core/<path>.json}. */
    public ResourceLocation id() {
        return this.id;
    }

    /** Returns the vertex format this shader expects. */
    public VertexFormat format() {
        return this.format;
    }

    /**
     * Returns the instanced variant used for static-mesh batching, or an empty optional
     * when this handle has none.
     */
    public Optional<EffectShader> instancedVariant() {
        return Optional.ofNullable(this.instancedVariant);
    }

    /**
     * Returns the currently loaded shader instance, or an empty optional before
     * the first load and during a reload. The returned instance is replaced on
     * every shader reload; never cache it.
     */
    public Optional<ShaderInstance> instance() {
        return Optional.ofNullable(this.instance);
    }

    /** Internal access for render-state suppliers; may return {@code null} before load. */
    public ShaderInstance shaderInstance() {
        return this.instance;
    }

    /** Package-private callback used by {@link #register(RegisterShadersEvent)}. */
    public void accept(ShaderInstance shader) {
        this.instance = Objects.requireNonNull(shader, "shader");
    }
}
