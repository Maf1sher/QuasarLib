package mafisher.quasar.shader;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Catches Java call sites that bind a uniform or sampler missing from the JSON contract. */
class ShaderUniformUsageTest {
    private static final Pattern SET_UNIFORM = Pattern.compile(
        "setUniform\\s*\\(\\s*shader\\s*,\\s*\"([^\"]+)\"");
    private static final Pattern SET_SAMPLER = Pattern.compile(
        "setSampler\\s*\\(\\s*shader\\s*,\\s*\"([^\"]+)\"");

    @Test
    void blackHoleOperationOnlyBindsDeclaredUniformsAndSamplers() throws IOException {
        ShaderTestResources.ShaderProgramAsset program = ShaderTestResources.programs().stream()
            .filter(candidate -> candidate.programId().equals("quasar_content:black_hole"))
            .findFirst()
            .orElseThrow();
        String source = Files.readString(sourceRoot().resolve(
            "mafisher/quasar/content/blackhole/ContentBlackHoleOperation.java"));

        List<String> failures = new ArrayList<>();
        compare("uniform", names(source, SET_UNIFORM), Set.copyOf(program.uniforms()), failures);
        compare("sampler", names(source, SET_SAMPLER), Set.copyOf(program.samplers()), failures);

        assertTrue(failures.isEmpty(), () -> "ContentBlackHoleOperation binds undeclared inputs:\n"
            + String.join("\n", failures));
    }

    private static Path sourceRoot() {
        String configured = System.getProperty("quasar.api.sourceRoot");
        return configured == null ? Path.of("src/main/java") : Path.of(configured);
    }

    private static Set<String> names(String source, Pattern pattern) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static void compare(String kind, Set<String> used, Set<String> declared,
        List<String> failures) {
        for (String name : used) {
            if (!declared.contains(name)) {
                failures.add("set " + kind + " '" + name + "' is not declared in the JSON");
            }
        }
    }
}
