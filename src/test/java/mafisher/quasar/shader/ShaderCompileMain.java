package mafisher.quasar.shader;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs shaderc in its own JVM. The NeoForge test runtime may already have loaded
 * LWJGL natives through another classloader, so loading shaderc in-process can
 * fail with “already loaded in another classloader”.
 */
public final class ShaderCompileMain {
    private ShaderCompileMain() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();
        List<ShaderTestResources.ShaderProgramAsset> programs = ShaderTestResources.programs();
        for (ShaderTestResources.ShaderProgramAsset program : programs) {
            compile(program, program.vertexSourceId(), "vertex", failures);
            compile(program, program.fragmentSourceId(), "fragment", failures);
        }

        ShaderTestCompiler.Result invalid = ShaderTestCompiler.compile(
            "#version 150\nvoid main() { this is not glsl }\n",
            ShaderTestCompiler.Stage.FRAGMENT,
            "invalid-test-shader");
        if (invalid.success()) {
            failures.add("the compiler accepted invalid GLSL");
        }

        if (!failures.isEmpty()) {
            System.err.println("Shader compilation failed:");
            for (String failure : failures) {
                System.err.println();
                System.err.println(failure);
            }
            System.exit(1);
        }
        System.out.println("Compiled " + programs.size() + " shader programs");
    }

    private static void compile(ShaderTestResources.ShaderProgramAsset program, String resourceId,
        String stageName, List<String> failures) {
        ShaderTestCompiler.Stage stage = "vertex".equals(stageName)
            ? ShaderTestCompiler.Stage.VERTEX : ShaderTestCompiler.Stage.FRAGMENT;
        try {
            String source = ShaderTestResources.preprocessedShader(resourceId);
            ShaderTestCompiler.Result result = ShaderTestCompiler.compile(
                source, stage, program.programId() + " " + stageName);
            if (!result.success()) {
                failures.add(program.programId() + " (" + resourceId + ", " + stageName + ")\n"
                    + result.messages());
            }
        } catch (RuntimeException e) {
            failures.add(program.programId() + " (" + resourceId + ", " + stageName + ")\n"
                + e.getMessage());
        }
    }
}
