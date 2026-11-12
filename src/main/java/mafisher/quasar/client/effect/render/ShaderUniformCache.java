package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.shaders.AbstractUniform;
import java.util.Objects;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.ApiStatus;

/** Resolves one uniform handle per shader instance and name, avoiding repeated string-map lookups per batch. */
@ApiStatus.Internal
public final class ShaderUniformCache {
    private final IdentityStringCache<ShaderInstance, AbstractUniform> uniforms =
        new IdentityStringCache<>();

    public AbstractUniform get(ShaderInstance shader, String name) {
        Objects.requireNonNull(shader, "shader");
        Objects.requireNonNull(name, "name");
        return this.uniforms.get(shader, name, ShaderInstance::getUniform);
    }

    public void clear() {
        this.uniforms.clear();
    }
}
