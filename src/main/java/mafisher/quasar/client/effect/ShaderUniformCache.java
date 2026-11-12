package mafisher.quasar.client.effect;

import com.mojang.blaze3d.shaders.AbstractUniform;
import java.util.Objects;
import net.minecraft.client.renderer.ShaderInstance;

/** Resolves one uniform handle per shader instance and name, avoiding repeated string-map lookups per batch. */
final class ShaderUniformCache {
    private final IdentityStringCache<ShaderInstance, AbstractUniform> uniforms =
        new IdentityStringCache<>();

    AbstractUniform get(ShaderInstance shader, String name) {
        Objects.requireNonNull(shader, "shader");
        Objects.requireNonNull(name, "name");
        return this.uniforms.get(shader, name, ShaderInstance::getUniform);
    }

    void clear() {
        this.uniforms.clear();
    }
}
