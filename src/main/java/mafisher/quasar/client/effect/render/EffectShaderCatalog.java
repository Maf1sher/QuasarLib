package mafisher.quasar.client.effect.render;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import mafisher.quasar.api.client.render.EffectShader;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registry of every client shader handle registered through
 * {@link EffectShader#register(net.neoforged.neoforge.client.event.RegisterShadersEvent)}.
 * The preflight pass uses it to validate the current assets before a resource reload.
 */
@ApiStatus.Internal
public final class EffectShaderCatalog {
    private static final Set<EffectShader> HANDLES = new CopyOnWriteArraySet<>();

    private EffectShaderCatalog() {
    }

    /** Records one handle and its instanced variant for later validation. */
    public static void record(EffectShader shader) {
        Objects.requireNonNull(shader, "shader");
        HANDLES.add(shader);
        shader.instancedVariant().ifPresent(HANDLES::add);
    }

    /** Returns handles in registration order. */
    public static List<EffectShader> handles() {
        return List.copyOf(HANDLES);
    }

    static void clearForTests() {
        HANDLES.clear();
    }
}
