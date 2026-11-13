package mafisher.quasar.api.client.render;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

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
 */
public final class EffectShader {
    private final ResourceLocation id;
    private final VertexFormat format;
    private volatile ShaderInstance instance;

    private EffectShader(ResourceLocation id, VertexFormat format) {
        this.id = Objects.requireNonNull(id, "id");
        this.format = Objects.requireNonNull(format, "format");
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
        return new EffectShader(id, format);
    }

    /**
     * Registers this handle's shader for the current shader load. Call this from
     * a {@link RegisterShadersEvent} handler on the client mod bus.
     *
     * @param event the NeoForge shader registration event
     * @throws IOException if the shader assets cannot be read
     */
    public void register(RegisterShadersEvent event) throws IOException {
        Objects.requireNonNull(event, "event");
        event.registerShader(
            new ShaderInstance(event.getResourceProvider(), this.id, this.format),
            this::accept
        );
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
