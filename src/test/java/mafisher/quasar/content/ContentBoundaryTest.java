package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Pins the dependency direction between the engine and the content layer. */
class ContentBoundaryTest {
    private static final Path SOURCE_ROOT = findSourceRoot();
    private static final Path COMPOSITION_ROOT = SOURCE_ROOT.resolve("mafisher/quasar/QuasarLib.java");
    private static final String CONTENT_PACKAGE = "mafisher.quasar.content";
    private static final List<String> ENGINE_INTERNAL_IMPORTS = List.of(
        "mafisher.quasar.client.effect.EffectRenderTypes",
        "mafisher.quasar.client.effect.EffectShaders",
        "mafisher.quasar.client.effect.BuiltInScreenEffectOperations",
        "mafisher.quasar.client.effect.ClientScreenCompositor",
        "mafisher.quasar.client.effect.ScreenEffectOperationRegistry",
        "mafisher.quasar.client.effect.ScreenEffectScratchTargetPool",
        "mafisher.quasar.client.effect.VisualEffectRendererRegistry",
        "mafisher.quasar.client.effect.ClientEffectRuntime",
        "mafisher.quasar.client.effect.ClientVisualEffectManager",
        "mafisher.quasar.client.effect.RuneCircleRenderer",
        "mafisher.quasar.client.effect.WaveDistortionRenderer",
        "mafisher.quasar.client.effect.SceneBlurRenderer",
        "mafisher.quasar.client.effect.BloomRenderer",
        "mafisher.quasar.client.effect.BlackHoleRenderer",
        "mafisher.quasar.effect.EffectVisualIds"
    );

    @Test
    void enginePackagesDoNotImportContent() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources()) {
            if (file.equals(COMPOSITION_ROOT)) {
                continue;
            }
            String source = Files.readString(file);
            if (!isContentPackage(packageName(source)) && source.contains("import " + CONTENT_PACKAGE)) {
                violations.add(file.toString());
            }
        }

        assertTrue(violations.isEmpty(), () -> "Only the composition root may import content: " + violations);
    }

    @Test
    void contentDoesNotImportEngineInternals() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources()) {
            String source = Files.readString(file);
            if (!isContentPackage(packageName(source))) {
                continue;
            }
            for (String internal : ENGINE_INTERNAL_IMPORTS) {
                if (source.contains("import " + internal + ";")) {
                    violations.add(file + " imports " + internal);
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Content must use public engine contracts only: " + violations);
    }

    private static Path findSourceRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path direct = current.resolve("src/main/java");
            if (Files.isDirectory(direct)) {
                return direct;
            }
            Path nested = current.resolve("quasar-lib/src/main/java");
            if (Files.isDirectory(nested)) {
                return nested;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate src/main/java from " + Path.of("").toAbsolutePath());
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .toList();
        }
    }

    private static boolean isContentPackage(String packageName) {
        return packageName.equals(CONTENT_PACKAGE) || packageName.startsWith(CONTENT_PACKAGE + ".");
    }

    private static String packageName(String source) {
        for (String line : source.lines().toList()) {
            if (line.startsWith("package ")) {
                return line.substring("package ".length(), line.indexOf(';'));
            }
        }
        return "";
    }
}
