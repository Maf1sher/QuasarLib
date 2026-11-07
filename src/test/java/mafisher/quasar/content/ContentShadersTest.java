package mafisher.quasar.content;

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

class ContentShadersTest {
    private static final List<String> SHADER_ASSETS = List.of(
        "shaders/core/rune_circle.json",
        "shaders/core/rune_circle.vsh",
        "shaders/core/rune_circle.fsh",
        "shaders/core/rune_mask.json",
        "shaders/core/rune_mask.vsh",
        "shaders/core/rune_mask.fsh",
        "shaders/core/rune_glow.json",
        "shaders/core/rune_glow.vsh",
        "shaders/core/rune_glow.fsh"
    );

    @Test
    void exposesContentOwnedShaderIds() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "rune_circle"),
            ContentShaders.RUNE_CIRCLE.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "rune_mask"),
            ContentShaders.RUNE_MASK.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "rune_glow"),
            ContentShaders.RUNE_GLOW.id());
        assertEquals(DefaultVertexFormat.BLOCK, ContentShaders.RUNE_CIRCLE.format());
        assertEquals(DefaultVertexFormat.BLOCK, ContentShaders.RUNE_MASK.format());
        assertEquals(DefaultVertexFormat.POSITION, ContentShaders.RUNE_GLOW.format());
    }

    @Test
    void shipsAllShaderAssets() throws IOException {
        for (String asset : SHADER_ASSETS) {
            String path = "assets/quasar_content/shaders/core/" + asset.substring("shaders/core/".length());
            assertNotNull(getClass().getClassLoader().getResource(path), path);
        }
    }

    @Test
    void worldShaderDeclaresInstanceArrays() throws IOException {
        String path = "assets/quasar_content/shaders/core/rune_circle.vsh";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(source.contains("Instances[128]"), source);
            assertTrue(source.contains("InstanceColors[128]"), source);
            assertTrue(source.contains("InstanceData[128]"), source);
        }
    }
}
