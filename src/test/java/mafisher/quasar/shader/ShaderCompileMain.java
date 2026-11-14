package mafisher.quasar.shader;

import java.nio.file.Path;
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
        int moduleCount = compileModules(failures);

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
        System.out.println("Compiled " + moduleCount + " shader modules");
    }

    private static int compileModules(List<String> failures) {
        List<Path> modules = ShaderTestResources.includeSources();
        for (Path path : modules) {
            String resourceId = ShaderTestResources.resourceFor(path).id();
            String source = "#version 150\n"
                + ShaderTestResources.readShaderSource(resourceId)
                + "\nvoid main() {}\n";
            compile(source, ShaderTestCompiler.Stage.FRAGMENT,
                resourceId, failures);
        }
        return modules.size();
    }

    private static void compile(ShaderTestResources.ShaderProgramAsset program, String resourceId,
        String stageName, List<String> failures) {
        ShaderTestCompiler.Stage stage = "vertex".equals(stageName)
            ? ShaderTestCompiler.Stage.VERTEX : ShaderTestCompiler.Stage.FRAGMENT;
        compile(ShaderTestResources.preprocessedShader(resourceId), stage,
            program.programId() + " (" + resourceId + ", " + stageName + ")", failures);
    }

    private static void compile(String source, ShaderTestCompiler.Stage stage, String label,
        List<String> failures) {
        try {
            ShaderTestCompiler.Result result = ShaderTestCompiler.compile(
                source, stage, label);
            if (!result.success()) {
                failures.add(label + "\n" + result.messages());
            }
        } catch (RuntimeException e) {
            failures.add(label + "\n" + e.getMessage());
        }
    }
}
