package mafisher.quasar;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModMetadataTest {
    @Test
    void declaresEngineAndContentMods() throws IOException {
        String metadata = readMetadata();

        assertTrue(metadata.contains("modId=\"quasar_lib\""), metadata);
        assertTrue(metadata.contains("modId=\"quasar_content\""), metadata);
        assertTrue(metadata.contains("[[dependencies.quasar_content]]"), metadata);
        assertTrue(metadata.contains("versionRange=\"[0.1.0,)\""), metadata);
        assertTrue(metadata.contains("ordering=\"AFTER\""), metadata);
    }

    private static String readMetadata() throws IOException {
        String configuredPath = System.getProperty("quasar.modMetadata");
        if (configuredPath != null) {
            return Files.readString(Path.of(configuredPath), StandardCharsets.UTF_8);
        }
        try (InputStream stream = ModMetadataTest.class.getClassLoader()
            .getResourceAsStream("META-INF/neoforge.mods.toml")) {
            assertNotNull(stream, "Missing generated neoforge.mods.toml");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
