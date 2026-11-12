package mafisher.quasar.shader;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import java.util.HashSet;
import java.util.Set;

/** Expands {@code #moj_import} exactly like the client shader loader, from the test classpath. */
final class ShaderTestPreprocessor extends GlslPreprocessor {
    private final Set<String> imported = new HashSet<>();

    String processSource(String source) {
        return String.join("\n", process(source));
    }

    @Override
    public String applyImport(boolean useFullPath, String directory) {
        if (useFullPath) {
            throw new UnsupportedOperationException(
                "Relative shader imports are not supported by the test harness: " + directory);
        }
        ShaderTestResources.ShaderResource resource =
            ShaderTestResources.resolveImport("minecraft", "", directory, false);
        String id = resource.id();
        if (!this.imported.add(id)) {
            throw new IllegalArgumentException("Duplicate or cyclic shader import: " + id);
        }
        return ShaderTestResources.readShaderSource(id);
    }
}
