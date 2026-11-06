package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Keeps the gameplay and block layers server-side and independent of the content layer. */
class GameplayBoundaryTest {
    private static final Path GAMEPLAY_ROOT = findSourceRoot().resolve("mafisher/quasar/effect/gameplay");
    private static final Path BLOCK_ROOT = findSourceRoot().resolve("mafisher/quasar/effect/block");

    @Test
    void effectPackagesDoNotImportClientOrContent() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(GAMEPLAY_ROOT, BLOCK_ROOT)) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    if (source.contains("import net.minecraft.client")
                        || source.contains("import mafisher.quasar.client")
                        || source.contains("import mafisher.quasar.content")) {
                        violations.add(file.toString());
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Server effects must stay server-side and content-free: " + violations);
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
}
