package mafisher.quasar.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Compiles every shipped shader program in an isolated JVM. */
class ShaderCompilationTest {
    private static final long TIMEOUT_SECONDS = 120L;

    @Test
    void compilesEveryShippedProgram() throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        String sourceRoot = System.getProperty("quasar.shader.sourceRoot");
        if (sourceRoot != null && !sourceRoot.isBlank()) {
            command.add("-Dquasar.shader.sourceRoot=" + sourceRoot);
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ShaderCompileMain.class.getName());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }

        assertTrue(finished, () -> "Shader compilation timed out:\n" + output);
        assertEquals(0, process.exitValue(), () -> "Shader compilation failed:\n" + output);
        int expectedPrograms = ShaderTestResources.programs().size();
        assertTrue(output.contains("Compiled " + expectedPrograms + " shader programs"),
            () -> "Shader compilation did not report every program:\n" + output);
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT)
            .contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }
}
