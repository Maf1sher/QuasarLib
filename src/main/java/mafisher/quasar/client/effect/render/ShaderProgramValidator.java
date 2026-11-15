package mafisher.quasar.client.effect.render;

import java.util.List;
import mafisher.quasar.api.client.render.EffectShader;
import net.minecraft.server.packs.resources.ResourceProvider;

/** Loads one registered shader and runs the GLSL/JSON preflight checks. */
final class ShaderProgramValidator {
    private ShaderProgramValidator() {
    }

    static void validate(EffectShader shader, ResourceProvider resources,
        List<ShaderFailure> failures) {
        ShaderProgramSource source;
        try {
            source = ShaderProgramLoader.load(resources, shader.id());
        } catch (ShaderProgramLoader.LoadException exception) {
            failures.add(new ShaderFailure(shader.id(), exception.stage(),
                exception.resource().toString(), exception.pack(), List.of(),
                exception.getMessage(), exception.detail()));
            return;
        }
        ShaderProgramDiagnostics.validate(source, resources, shader.format(), failures);
    }
}
