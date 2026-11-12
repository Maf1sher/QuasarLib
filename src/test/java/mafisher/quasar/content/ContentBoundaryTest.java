package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Pins the dependency direction and the public API surface used by shipped demo content. */
class ContentBoundaryTest {
    private static final Path MAIN_SOURCE_ROOT = findSourceRoot();
    private static final Path CONTENT_SOURCE_ROOT = MAIN_SOURCE_ROOT.resolve("mafisher/quasar/content");
    private static final Path COMPOSITION_ROOT = MAIN_SOURCE_ROOT.resolve("mafisher/quasar/QuasarLib.java");
    private static final String CONTENT_PACKAGE = "mafisher.quasar.content";
    private static final Pattern QUASAR_REFERENCE = Pattern.compile(
        "mafisher\\.quasar\\.[A-Za-z0-9_$.]+"
    );
    @Test
    void contentMainUsesOnlyPublicEngineApi() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources(CONTENT_SOURCE_ROOT)) {
            for (String reference : references(Files.readString(file))) {
                if (isContentReference(reference) || isEngineApi(reference)) {
                    continue;
                }
                violations.add(file + " -> " + reference);
            }
        }

        assertTrue(violations.isEmpty(),
            () -> "Content must use only mafisher.quasar.api contracts: " + violations);
    }

    @Test
    void contentMainDoesNotUseWildcardEngineImports() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources(CONTENT_SOURCE_ROOT)) {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.strip();
                if (trimmed.startsWith("import ") && trimmed.contains("mafisher.quasar.")
                    && trimmed.endsWith(".*;")) {
                    violations.add(file + " -> " + trimmed);
                }
            }
        }

        assertTrue(violations.isEmpty(),
            () -> "Content must not use engine wildcard imports: " + violations);
    }

    @Test
    void engineMainDoesNotReferenceContent() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources(MAIN_SOURCE_ROOT)) {
            if (file.equals(COMPOSITION_ROOT)) {
                continue;
            }
            String source = Files.readString(file);
            if (isContentPackage(packageName(source))) {
                continue;
            }
            if (references(source).stream().anyMatch(ContentBoundaryTest::isContentReference)) {
                violations.add(file.toString());
            }
        }

        assertTrue(violations.isEmpty(),
            () -> "Only the composition root may reference content: " + violations);
    }

    @Test
    void compositionRootOnlyReferencesContentBootstrap() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String reference : references(Files.readString(COMPOSITION_ROOT))) {
            if (isContentReference(reference)
                && !reference.equals(CONTENT_PACKAGE + ".ContentBootstrap")) {
                violations.add(reference);
            }
        }

        assertTrue(violations.isEmpty(),
            () -> "The composition root may reference only ContentBootstrap: " + violations);
    }

    @Test
    void scannerFindsFullyQualifiedReferences() {
        assertEquals(List.of("mafisher.quasar.api.client.EffectShader"),
            references("import mafisher.quasar.api.client.EffectShader;"));
    }

    @Test
    void scannerTreatsOwnPackageAsContent() {
        assertTrue(isContentReference(CONTENT_PACKAGE));
        assertTrue(isContentReference(CONTENT_PACKAGE + ".ContentBootstrap"));
        assertFalse(isContentReference("mafisher.quasar.api.client.EffectShader"));
    }

    @Test
    void engineApiMatchesPackagesAndNestedTypes() {
        assertTrue(isEngineApi("mafisher.quasar.api.client.EffectRenderTypeOptions.Transparency"));
        assertTrue(isEngineApi("mafisher.quasar.QuasarClientConfig"));
        assertFalse(isEngineApi("mafisher.quasar.client.effect.EffectRenderTypes"));
    }

    private static List<String> references(String source) {
        List<String> references = new ArrayList<>();
        Matcher matcher = QUASAR_REFERENCE.matcher(source);
        while (matcher.find()) {
            references.add(matcher.group());
        }
        return references;
    }

    private static boolean isContentReference(String reference) {
        return reference.equals(CONTENT_PACKAGE) || reference.startsWith(CONTENT_PACKAGE + ".");
    }

    private static boolean isEngineApi(String reference) {
        return reference.startsWith("mafisher.quasar.api")
            || reference.equals("mafisher.quasar.QuasarLib")
            || reference.equals("mafisher.quasar.QuasarConfig")
            || reference.equals("mafisher.quasar.QuasarClientConfig");
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

    private static List<Path> javaSources(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
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
