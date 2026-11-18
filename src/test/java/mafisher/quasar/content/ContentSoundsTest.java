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
    private static final List<String> BLACK_HOLE_FILES = List.of(
        "dread", "drone", "ignition", "shockwave", "accretion", "pulse",
        "collapse", "implosion", "echo"
    );
    private static final List<String> ORBITAL_LASER_FILES = List.of(
        "warning", "arrival", "charge", "descent", "fire", "impact", "drone",
        "crackle", "collapse", "aftermath"
    );

    @Test
    void registersEveryContentSoundEvent() {
        Set<ResourceLocation> ids = ContentSounds.SOUNDS.getEntries().stream()
            .map(holder -> holder.getId())
            .collect(Collectors.toSet());

        assertEquals(20, ids.size());
        assertTrue(ids.contains(id("effect_test")));
        assertTrue(ids.contains(id("black_hole_drone")));
        assertTrue(ids.contains(id("black_hole_implosion")));
        assertTrue(ids.contains(id("orbital_laser_warning")));
        assertTrue(ids.contains(id("orbital_laser_arrival")));
        assertTrue(ids.contains(id("orbital_laser_charge")));
        assertTrue(ids.contains(id("orbital_laser_fire")));
        assertTrue(ids.contains(id("orbital_laser_aftermath")));
    }

    @Test
    void shipsTheSoundJsonAndEveryAsset() throws IOException {
        String json = readAsset("assets/quasar_content/sounds.json");
        assertAssets(json, "black_hole", BLACK_HOLE_FILES);
        assertAssets(json, "orbital_laser", ORBITAL_LASER_FILES);
    }

    private void assertAssets(String json, String directory, List<String> names) {
        for (String name : names) {
            String event = directory + "_" + name;
            assertTrue(json.contains("\"" + event + "\""), event);
            assertTrue(json.contains("\"quasar_content:" + directory + "/" + name + "\""), name);
            assertNotNull(getClass().getClassLoader().getResource(
                "assets/quasar_content/sounds/" + directory + "/" + name + ".ogg"), name);
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
