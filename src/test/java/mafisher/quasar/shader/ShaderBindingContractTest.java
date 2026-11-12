package mafisher.quasar.shader;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Checks the JSON binding contract against the declarations in the shipped GLSL. */
class ShaderBindingContractTest {
    private static final Set<String> GL_MANAGED_UNIFORMS =
        Set.of("Instances", "InstanceColors", "InstanceData");

    @Test
    void samplersUniformsAndAttributesMatchTheJson() {
        List<String> failures = new ArrayList<>();
        for (ShaderTestResources.ShaderProgramAsset program : ShaderTestResources.programs()) {
            ShaderDeclarationScanner.Declarations vertex = scan(program.vertexSourceId());
            ShaderDeclarationScanner.Declarations fragment = scan(program.fragmentSourceId());
            Set<String> glslSamplers = union(
                ShaderDeclarationScanner.Declarations.names(vertex.samplers()),
                ShaderDeclarationScanner.Declarations.names(fragment.samplers()));
            Set<String> glslUniforms = union(
                nonSamplers(vertex), nonSamplers(fragment));
            Set<String> glslAttributes =
                ShaderDeclarationScanner.Declarations.names(vertex.inputs());

            compare(program, "samplers", Set.copyOf(program.samplers()), glslSamplers, failures);
            compareJsonUniforms(program, Set.copyOf(program.uniforms()), glslUniforms, failures);
            compare(program, "attributes", Set.copyOf(program.attributes()), glslAttributes, failures);
        }
        assertTrue(failures.isEmpty(), () -> "Shader binding contract failed:\n\n"
            + String.join("\n\n", failures));
    }

    @Test
    void fragmentInputsHaveMatchingVertexOutputs() {
        List<String> failures = new ArrayList<>();
        for (ShaderTestResources.ShaderProgramAsset program : ShaderTestResources.programs()) {
            Map<String, ShaderDeclarationScanner.Declaration> outputs = byName(
                scan(program.vertexSourceId()).outputs());
            for (ShaderDeclarationScanner.Declaration input : scan(program.fragmentSourceId()).inputs()) {
                ShaderDeclarationScanner.Declaration output = outputs.get(input.name());
                if (output == null) {
                    failures.add(program.programId() + ": fragment input '" + input.name()
                        + "' has no vertex output");
                } else if (!output.type().equals(input.type())
                    || output.flat() != input.flat()) {
                    failures.add(program.programId() + ": fragment input '" + input.name()
                        + "' is " + describe(input) + " but vertex output is " + describe(output));
                }
            }
        }
        assertTrue(failures.isEmpty(), () -> "Shader varying contract failed:\n\n"
            + String.join("\n\n", failures));
    }

    private static ShaderDeclarationScanner.Declarations scan(String resourceId) {
        return ShaderDeclarationScanner.scan(ShaderTestResources.preprocessedShader(resourceId));
    }

    private static Set<String> nonSamplers(ShaderDeclarationScanner.Declarations declarations) {
        Set<String> samplers = ShaderDeclarationScanner.Declarations.names(declarations.samplers());
        Set<String> uniforms = new LinkedHashSet<>(
            ShaderDeclarationScanner.Declarations.names(declarations.uniforms()));
        uniforms.removeAll(samplers);
        return uniforms;
    }

    private static void compare(ShaderTestResources.ShaderProgramAsset program, String kind,
        Set<String> jsonNames, Set<String> glslNames, List<String> failures) {
        for (String name : jsonNames) {
            if (!glslNames.contains(name)) {
                failures.add(program.programId() + ": JSON " + kind + " '" + name
                    + "' is not declared in GLSL");
            }
        }
        for (String name : glslNames) {
            if (!jsonNames.contains(name)) {
                failures.add(program.programId() + ": GLSL " + kind + " '" + name
                    + "' is missing from the JSON");
            }
        }
    }

    private static void compareJsonUniforms(ShaderTestResources.ShaderProgramAsset program,
        Set<String> jsonNames, Set<String> glslNames, List<String> failures) {
        for (String name : jsonNames) {
            if (!glslNames.contains(name)) {
                failures.add(program.programId() + ": JSON uniform '" + name
                    + "' is not declared in GLSL");
            }
        }
        for (String name : glslNames) {
            if (!jsonNames.contains(name) && !GL_MANAGED_UNIFORMS.contains(name)) {
                failures.add(program.programId() + ": GLSL uniform '" + name
                    + "' is missing from the JSON");
            }
        }
    }

    private static Map<String, ShaderDeclarationScanner.Declaration> byName(
        List<ShaderDeclarationScanner.Declaration> declarations) {
        return declarations.stream()
            .collect(Collectors.toMap(ShaderDeclarationScanner.Declaration::name,
                Function.identity(), (first, second) -> first));
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> union = new LinkedHashSet<>(first);
        union.addAll(second);
        return union;
    }

    private static String describe(ShaderDeclarationScanner.Declaration declaration) {
        return (declaration.flat() ? "flat " : "") + declaration.type();
    }
}
