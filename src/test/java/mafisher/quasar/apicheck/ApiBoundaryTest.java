package mafisher.quasar.apicheck;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Freezes the consumer API boundary.
 *
 * <p>The source scan keeps accidental public types out of implementation
 * packages. The reflection pass proves that public API signatures never expose
 * an implementation type. The manifest makes every API addition or removal an
 * explicit review event.</p>
 */
class ApiBoundaryTest {
    private static final Path SOURCE_ROOT = Path.of(
        System.getProperty("quasar.api.sourceRoot", "src/main/java")
    );
    private static final Path CLASSES_DIR = Path.of(
        System.getProperty("quasar.api.classesDir", "build/classes/java/main")
    );
    private static final String API_PREFIX = "mafisher.quasar.api.";
    private static final String CONTENT_PREFIX = "mafisher.quasar.content";
    private static final Set<String> ROOT_TYPES = Set.of(
        "mafisher.quasar.QuasarLib",
        "mafisher.quasar.QuasarConfig",
        "mafisher.quasar.QuasarClientConfig"
    );
    private static final Pattern PACKAGE = Pattern.compile(
        "^package\\s+([A-Za-z0-9_.]+);", Pattern.MULTILINE
    );
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
        "^public\\s+(?:final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+|static\\s+)*"
            + "(?:class|interface|enum|record)\\s+([A-Za-z0-9_]+)",
        Pattern.MULTILINE
    );

    @Test
    void publicEngineTypesLiveInApiOrAreMarkedInternal() throws IOException {
        List<String> violations = new ArrayList<>();
        for (SourceType type : sourceTypes()) {
            if (type.fqcn().equals("mafisher.quasar.QuasarLib")
                || type.fqcn().equals("mafisher.quasar.QuasarConfig")
                || type.fqcn().equals("mafisher.quasar.QuasarClientConfig")) {
                continue;
            }
            if (type.packageName().startsWith(CONTENT_PREFIX)) {
                continue;
            }
            if (type.packageName().startsWith("mafisher.quasar.api")) {
                continue;
            }
            if (!type.internal()) {
                violations.add(type.fqcn());
            }
        }

        assertTrue(violations.isEmpty(),
            () -> "Public engine types outside mafisher.quasar.api must be @ApiStatus.Internal: "
                + violations);
    }

    @Test
    void apiPackagesAreNullMarked() throws IOException {
        Path apiRoot = SOURCE_ROOT.resolve("mafisher/quasar/api");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> directories = Files.walk(apiRoot)) {
            directories.filter(Files::isDirectory).forEach(directory -> {
                boolean hasJava = false;
                try (Stream<Path> files = Files.list(directory)) {
                    hasJava = files.anyMatch(path -> path.toString().endsWith(".java")
                        && !path.getFileName().toString().equals("package-info.java"));
                } catch (IOException exception) {
                    violations.add(directory + ": " + exception.getMessage());
                    return;
                }
                if (!hasJava) {
                    return;
                }
                Path info = directory.resolve("package-info.java");
                if (!Files.isRegularFile(info)) {
                    violations.add(directory + ": missing package-info.java");
                    return;
                }
                try {
                    String text = Files.readString(info);
                    if (!text.contains("@NullMarked")) {
                        violations.add(directory + ": package-info is not @NullMarked");
                    }
                } catch (IOException exception) {
                    violations.add(directory + ": " + exception.getMessage());
                }
            });
        }
        assertTrue(violations.isEmpty(), () -> "API package annotations: " + violations);
    }

    @Test
    void apiSurfaceMatchesManifest() throws IOException {
        Set<String> expected = manifest();
        Set<String> actual = new LinkedHashSet<>();
        for (SourceType type : sourceTypes()) {
            if (type.internal()) {
                continue;
            }
            if (type.packageName().startsWith("mafisher.quasar.api")
                || type.fqcn().equals("mafisher.quasar.QuasarConfig")
                || type.fqcn().equals("mafisher.quasar.QuasarClientConfig")) {
                actual.add(type.fqcn());
            }
        }

        Set<String> added = new LinkedHashSet<>(actual);
        added.removeAll(expected);
        Set<String> removed = new LinkedHashSet<>(expected);
        removed.removeAll(actual);
        assertTrue(added.isEmpty() && removed.isEmpty(),
            () -> "API surface changed. Update src/test/resources/api-surface.txt deliberately."
                + "\nAdded: " + added + "\nRemoved: " + removed);
    }

    @Test
    void apiSignaturesExposeOnlyApiTypes() throws IOException {
        Set<String> apiTypes = manifest();
        List<String> violations = new ArrayList<>();
        Path apiClasses = CLASSES_DIR.resolve("mafisher/quasar/api");
        assertTrue(Files.isDirectory(apiClasses), () -> "Missing compiled API classes: " + apiClasses);

        try (Stream<Path> files = Files.walk(apiClasses)) {
            files.filter(path -> path.toString().endsWith(".class"))
                .filter(path -> !path.getFileName().toString().contains("$"))
                .filter(path -> !path.getFileName().toString().equals("package-info.class"))
                .forEach(path -> {
                    String name = CLASSES_DIR.relativize(path).toString()
                        .replace(java.io.File.separatorChar, '.')
                        .replaceAll("\\.class$", "");
                    if (!apiTypes.contains(name)) {
                        return;
                    }
                    try {
                        Class<?> type = Class.forName(name, false, ApiBoundaryTest.class.getClassLoader());
                        inspect(type, violations);
                    } catch (Throwable throwable) {
                        violations.add(name + ": cannot inspect: " + throwable);
                    }
                });
        }

        assertTrue(violations.isEmpty(),
            () -> "Public API signatures expose implementation types: " + violations);
    }

    private static void inspect(Class<?> type, List<String> violations) {
        Set<Class<?>> visited = new HashSet<>();
        checkType(type, violations, visited);
        checkType(type.getGenericSuperclass(), violations, visited);
        for (Type interfaceType : type.getGenericInterfaces()) {
            checkType(interfaceType, violations, visited);
        }
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                checkType(component.getType(), violations, visited);
                checkType(component.getGenericType(), violations, visited);
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (!isVisible(field.getModifiers())) {
                continue;
            }
            checkType(field.getType(), violations, visited);
            checkType(field.getGenericType(), violations, visited);
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!isVisible(constructor.getModifiers())) {
                continue;
            }
            for (Class<?> parameter : constructor.getParameterTypes()) {
                checkType(parameter, violations, visited);
            }
            for (Type parameter : constructor.getGenericParameterTypes()) {
                checkType(parameter, violations, visited);
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (!isVisible(method.getModifiers()) || method.isBridge() || method.isSynthetic()) {
                continue;
            }
            checkType(method.getReturnType(), violations, visited);
            checkType(method.getGenericReturnType(), violations, visited);
            for (Class<?> parameter : method.getParameterTypes()) {
                checkType(parameter, violations, visited);
            }
            for (Type parameter : method.getGenericParameterTypes()) {
                checkType(parameter, violations, visited);
            }
        }
    }

    private static void checkType(Type type, List<String> violations, Set<Class<?>> visited) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                checkType(clazz.getComponentType(), violations, visited);
                return;
            }
            if (clazz.isPrimitive() || clazz == Void.TYPE || !visited.add(clazz)) {
                return;
            }
            String name = clazz.getName();
            if (name.startsWith("mafisher.quasar.")
                && !name.startsWith(API_PREFIX)
                && !ROOT_TYPES.contains(name)) {
                violations.add(name);
            }
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            checkType(parameterized.getRawType(), violations, visited);
            for (Type argument : parameterized.getActualTypeArguments()) {
                checkType(argument, violations, visited);
            }
            return;
        }
        if (type instanceof GenericArrayType array) {
            checkType(array.getGenericComponentType(), violations, visited);
            return;
        }
        if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                checkType(bound, violations, visited);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                checkType(bound, violations, visited);
            }
            return;
        }
        if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                checkType(bound, violations, visited);
            }
        }
    }

    private static boolean isVisible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static Set<String> manifest() throws IOException {
        try (InputStream stream = ApiBoundaryTest.class.getClassLoader()
            .getResourceAsStream("api-surface.txt")) {
            assertTrue(stream != null, "Missing src/test/resources/api-surface.txt");
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Set<String> entries = new LinkedHashSet<>();
            for (String line : text.split("\\R")) {
                String value = line.trim();
                if (!value.isEmpty() && !value.startsWith("#")) {
                    entries.add(value);
                }
            }
            return entries;
        }
    }

    private static List<SourceType> sourceTypes() throws IOException {
        List<SourceType> result = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            files.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .forEach(path -> {
                    try {
                        String text = Files.readString(path);
                        Matcher packageMatcher = PACKAGE.matcher(text);
                        Matcher typeMatcher = PUBLIC_TYPE.matcher(text);
                        if (!packageMatcher.find() || !typeMatcher.find()) {
                            return;
                        }
                        int declarationStart = typeMatcher.start();
                        int annotationStart = Math.max(0, declarationStart - 400);
                        boolean internal = text.substring(annotationStart, declarationStart)
                            .contains("ApiStatus.Internal");
                        result.add(new SourceType(
                            packageMatcher.group(1) + "." + typeMatcher.group(1),
                            packageMatcher.group(1),
                            internal
                        ));
                    } catch (IOException exception) {
                        throw new IllegalStateException("Cannot read " + path, exception);
                    }
                });
        }
        return result;
    }

    private record SourceType(String fqcn, String packageName, boolean internal) {
    }
}
