package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private static final Set<String> PUBLIC_ENGINE_API = Set.of(
        "mafisher.quasar.client.effect.EffectLod",
        "mafisher.quasar.client.effect.EffectLodProfile",
        "mafisher.quasar.client.effect.EffectMeshes",
        "mafisher.quasar.client.effect.EffectRenderContext",
        "mafisher.quasar.client.effect.EffectRenderPass",
        "mafisher.quasar.client.effect.EffectRenderTypeOptions",
        "mafisher.quasar.client.effect.EffectShader",
        "mafisher.quasar.client.effect.QuasarRenderTypes",
        "mafisher.quasar.client.effect.RegisterScreenEffectOperationsEvent",
        "mafisher.quasar.client.effect.RegisterVisualEffectRenderersEvent",
        "mafisher.quasar.client.effect.ScreenEffectMask",
        "mafisher.quasar.client.effect.ScreenEffectOperation",
        "mafisher.quasar.client.effect.ScreenEffectOperationContext",
        "mafisher.quasar.client.effect.ScreenEffectOperationRenderer",
        "mafisher.quasar.client.effect.ScreenEffectRenderer",
        "mafisher.quasar.client.effect.ScreenEffectSettings",
        "mafisher.quasar.effect.EffectAudience",
        "mafisher.quasar.effect.EffectTimeline",
        "mafisher.quasar.effect.VisualEffectService",
        "mafisher.quasar.effect.VisualEffectSpec",
        "mafisher.quasar.effect.audio.EffectAudioAttenuation",
        "mafisher.quasar.effect.audio.EffectAudioSpec",
        "mafisher.quasar.effect.scale.SegmentedVisualEffectService",
        "mafisher.quasar.effect.scale.SegmentedVisualEffectSpec"
    );

    @Test
    void contentMainUsesOnlyPublicEngineApi() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaSources(CONTENT_SOURCE_ROOT)) {
            for (String reference : references(Files.readString(file))) {
                if (isContentReference(reference) || isPublicApi(reference)) {
                    continue;
                }
                violations.add(file + " -> " + reference);
            }
        }

        assertTrue(violations.isEmpty(),
            () -> "Content must use only allowlisted public engine API: " + violations);
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
    void publicApiAllowlistContainsOnlyPublicTypes() throws ClassNotFoundException {
        ClassLoader loader = ContentBoundaryTest.class.getClassLoader();
        for (String name : PUBLIC_ENGINE_API) {
            Class<?> type = Class.forName(name, false, loader);
            assertTrue(Modifier.isPublic(type.getModifiers()), name + " must be public");
            assertFalse(type.getPackageName().equals(CONTENT_PACKAGE),
                name + " must not belong to the content package");
        }
    }

    @Test
    void scannerFindsFullyQualifiedReferences() {
        assertEquals(List.of("mafisher.quasar.client.effect.EffectRenderTypes"),
            references("import mafisher.quasar.client.effect.EffectRenderTypes;"));
    }

    @Test
    void scannerTreatsOwnPackageAsContent() {
        assertTrue(isContentReference(CONTENT_PACKAGE));
        assertTrue(isContentReference(CONTENT_PACKAGE + ".ContentBootstrap"));
        assertFalse(isContentReference("mafisher.quasar.client.effect.EffectShader"));
    }

    @Test
    void allowlistMatchesNestedTypes() {
        assertTrue(isPublicApi("mafisher.quasar.client.effect.EffectRenderTypeOptions.Transparency"));
        assertFalse(isPublicApi("mafisher.quasar.client.effect.EffectRenderTypes"));
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

    private static boolean isPublicApi(String reference) {
        for (String allowed : PUBLIC_ENGINE_API) {
            if (reference.equals(allowed) || reference.startsWith(allowed + ".")) {
                return true;
            }
        }
        return false;
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
