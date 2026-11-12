package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import mafisher.quasar.apicheck.CompiledReferenceScanner;
import mafisher.quasar.apicheck.fixtures.ContentFixture;
import mafisher.quasar.apicheck.fixtures.EngineFixture;
import org.junit.jupiter.api.Test;

/** Pins the dependency direction with compiled class references instead of a source-text allowlist. */
class ContentBoundaryTest {
    private static final Path CLASSES_DIR = Path.of(
        System.getProperty("quasar.api.classesDir", "build/classes/java/main")
    );
    private static final Path CONTENT_ROOT = CLASSES_DIR.resolve("mafisher/quasar/content");
    private static final String API_PREFIX = "mafisher.quasar.api.";
    private static final String CONTENT_PREFIX = "mafisher.quasar.content";
    private static final String QUASAR_PREFIX = "mafisher.quasar.";

    @Test
    void contentMainUsesOnlyPublicEngineApi() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path classFile : classFiles(CONTENT_ROOT)) {
            CompiledReferenceScanner.ScanResult result = CompiledReferenceScanner.scan(classFile);
            for (String reference : result.types()) {
                if (isForbiddenContentReference(reference)) {
                    violations.add(classFile + " type -> " + reference);
                }
            }
            for (String reference : result.annotations()) {
                if (isForbiddenContentReference(reference)) {
                    violations.add(classFile + " annotation -> " + reference);
                }
            }
            for (String reference : result.strings()) {
                if (isForbiddenContentReference(reference)) {
                    violations.add(classFile + " string -> " + reference);
                }
            }
        }

        assertTrue(violations.isEmpty(),
            () -> "Content must use only mafisher.quasar.api contracts: " + violations);
    }

    @Test
    void engineMainDoesNotReferenceContent() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path classFile : classFiles(CLASSES_DIR.resolve("mafisher/quasar"))) {
            if (classFile.startsWith(CONTENT_ROOT)) {
                continue;
            }
            CompiledReferenceScanner.ScanResult result = CompiledReferenceScanner.scan(classFile);
            result.types().stream()
                .filter(reference -> reference.startsWith(CONTENT_PREFIX))
                .forEach(reference -> violations.add(classFile + " type -> " + reference));
            result.annotations().stream()
                .filter(reference -> reference.startsWith(CONTENT_PREFIX))
                .forEach(reference -> violations.add(classFile + " annotation -> " + reference));
            result.strings().stream()
                .filter(reference -> reference.startsWith(CONTENT_PREFIX))
                .forEach(reference -> violations.add(classFile + " string -> " + reference));
        }

        assertTrue(violations.isEmpty(),
            () -> "Only content may reference content: " + violations);
    }

    @Test
    void contentHasNoEventBusSubscriberAnnotations() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path classFile : classFiles(CONTENT_ROOT)) {
            CompiledReferenceScanner.ScanResult result = CompiledReferenceScanner.scan(classFile);
            if (result.annotations().contains("net.neoforged.fml.common.EventBusSubscriber")) {
                violations.add(classFile.toString());
            }
        }

        assertTrue(violations.isEmpty(),
            () -> "Content must register listeners through its entry point: " + violations);
    }

    @Test
    void scannerFindsCompiledEngineReferences() throws IOException, URISyntaxException {
        CompiledReferenceScanner.ScanResult result = CompiledReferenceScanner.scan(
            classFile(ContentFixture.class)
        );

        assertTrue(result.types().contains("mafisher.quasar.api.client.EffectMeshes"));
        assertTrue(result.types().contains("mafisher.quasar.client.effect.render.EffectRenderTypes"));
        assertTrue(isForbiddenContentReference("mafisher.quasar.client.effect.render.EffectRenderTypes"));
        assertFalse(isForbiddenContentReference("mafisher.quasar.api.client.EffectMeshes"));
    }

    @Test
    void scannerFindsEngineContentReferences() throws IOException, URISyntaxException {
        CompiledReferenceScanner.ScanResult result = CompiledReferenceScanner.scan(
            classFile(EngineFixture.class)
        );

        assertTrue(result.types().contains("mafisher.quasar.content.ContentEffectIds"));
    }

    @Test
    void scannerAllowsApiReferences() {
        assertFalse(isForbiddenContentReference("mafisher.quasar.api.client.EffectMeshes"));
    }

    private static boolean isForbiddenContentReference(String reference) {
        return reference.startsWith(QUASAR_PREFIX)
            && !reference.startsWith(CONTENT_PREFIX)
            && !reference.startsWith(API_PREFIX);
    }

    private static List<Path> classFiles(Path root) throws IOException {
        assertTrue(Files.isDirectory(root), () -> "Missing compiled classes: " + root);
        try (Stream<Path> files = Files.walk(root)) {
            return files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".class"))
                .filter(path -> !path.getFileName().toString().equals("package-info.class"))
                .toList();
        }
    }

    private static Path classFile(Class<?> type) throws URISyntaxException {
        Path root = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        return root.resolve(type.getName().replace('.', '/') + ".class");
    }
}
