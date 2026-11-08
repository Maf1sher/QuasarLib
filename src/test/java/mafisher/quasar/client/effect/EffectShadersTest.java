package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EffectShadersTest {
    @Test
    void registersScreenPresent() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_lib", "screen_present"),
            EffectShaders.SCREEN_PRESENT.id());
        assertEquals(DefaultVertexFormat.POSITION, EffectShaders.SCREEN_PRESENT.format());
    }

    @Test
    void shipsScreenPresentAssets() {
        for (String asset : List.of(
            "screen_present.json",
            "screen_present.vsh",
            "screen_present.fsh"
        )) {
            String path = "assets/quasar_lib/shaders/core/" + asset;
            assertNotNull(getClass().getClassLoader().getResource(path), path);
        }
    }

    @Test
    void screenPresentDeclaresSamplerAndPosition() throws IOException {
        String json = readAsset("assets/quasar_lib/shaders/core/screen_present.json");
        assertTrue(json.contains("\"DiffuseSampler\""), json);
        assertTrue(json.contains("\"Position\""), json);
        assertTrue(json.contains("quasar_lib:screen_present"), json);
    }

    @Test
    void screenPresentSamplesDiffuseTexture() throws IOException {
        String shader = readAsset("assets/quasar_lib/shaders/core/screen_present.fsh");
        assertTrue(shader.contains("uniform sampler2D DiffuseSampler;"), shader);
        assertTrue(shader.contains("texture(DiffuseSampler, texCoord)"), shader);
    }

    private static String readAsset(String path) throws IOException {
        try (InputStream stream = EffectShadersTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
