package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ContentSoundsTest {
    private static final List<String> FILES = List.of(
        "dread", "drone", "ignition", "shockwave", "accretion", "pulse",
        "collapse", "implosion", "echo"
    );

    @Test
    void registersEveryBlackHoleSoundEvent() {
        Set<ResourceLocation> ids = ContentSounds.SOUNDS.getEntries().stream()
            .map(holder -> holder.getId())
            .collect(Collectors.toSet());

        assertEquals(9, ids.size());
        assertTrue(ids.contains(id("black_hole_drone")));
        assertTrue(ids.contains(id("black_hole_implosion")));
    }

    @Test
    void shipsTheSoundJsonAndEveryAsset() throws IOException {
        String json = readAsset("assets/quasar_content/sounds.json");
        for (String name : FILES) {
            assertTrue(json.contains("\"black_hole_" + name + "\""), name);
            assertTrue(json.contains("\"quasar_content:black_hole/" + name + "\""), name);
            assertNotNull(getClass().getClassLoader().getResource(
                "assets/quasar_content/sounds/black_hole/" + name + ".ogg"), name);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ContentBootstrap.NAMESPACE, path);
    }

    private static String readAsset(String path) throws IOException {
        try (InputStream stream = ContentSoundsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
