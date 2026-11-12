package mafisher.quasar.shader;

import org.lwjgl.util.shaderc.Shaderc;

/** Compiles shipped GLSL headlessly through shaderc; no OpenGL context is created. */
final class ShaderTestCompiler {
    enum Stage {
        VERTEX(Shaderc.shaderc_vertex_shader, ".vsh"),
        FRAGMENT(Shaderc.shaderc_fragment_shader, ".fsh");

        private final int kind;
        private final String extension;

        Stage(int kind, String extension) {
            this.kind = kind;
            this.extension = extension;
        }

        int kind() {
            return this.kind;
        }

        String extension() {
            return this.extension;
        }
    }

    record Result(boolean success, String messages) {
    }

    private ShaderTestCompiler() {
    }

    static Result compile(String source, Stage stage, String label) {
        String versioned = source.replaceFirst("(?m)^#version\\s+150\\b", "#version 330");
        if (!versioned.contains("#version")) {
            versioned = "#version 330\n" + versioned;
        }
        long compiler = Shaderc.shaderc_compiler_initialize();
        if (compiler == 0L) {
            throw new IllegalStateException("shaderc compiler initialization failed");
        }
        long options = Shaderc.shaderc_compile_options_initialize();
        if (options == 0L) {
            Shaderc.shaderc_compiler_release(compiler);
            throw new IllegalStateException("shaderc options initialization failed");
        }
        try {
            Shaderc.shaderc_compile_options_set_target_env(options,
                Shaderc.shaderc_target_env_opengl, 0);
            Shaderc.shaderc_compile_options_set_source_language(options,
                Shaderc.shaderc_source_language_glsl);
            Shaderc.shaderc_compile_options_set_auto_map_locations(options, true);
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
            long result = Shaderc.shaderc_compile_into_spv(
                compiler, versioned, stage.kind(), label, "main", options);
            if (result == 0L) {
                return new Result(false, "shaderc returned no compilation result");
            }
            try {
                int status = Shaderc.shaderc_result_get_compilation_status(result);
                String message = Shaderc.shaderc_result_get_error_message(result);
                return new Result(status == Shaderc.shaderc_compilation_status_success,
                    message == null ? "" : message);
            } finally {
                Shaderc.shaderc_result_release(result);
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }
}
