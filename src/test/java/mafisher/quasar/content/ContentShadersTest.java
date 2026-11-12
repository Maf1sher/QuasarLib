package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ContentShadersTest {
    @Test
    void exposesContentOwnedShaderIds() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "rune_circle"),
            ContentShaders.RUNE_CIRCLE.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "rune_mask"),
            ContentShaders.RUNE_MASK.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "rune_glow"),
            ContentShaders.RUNE_GLOW.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "black_hole"),
            ContentShaders.BLACK_HOLE.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "black_hole_mask"),
            ContentShaders.BLACK_HOLE_MASK.id());
        assertEquals(DefaultVertexFormat.BLOCK, ContentShaders.RUNE_CIRCLE.format());
        assertEquals(DefaultVertexFormat.BLOCK, ContentShaders.RUNE_MASK.format());
        assertEquals(DefaultVertexFormat.POSITION, ContentShaders.RUNE_GLOW.format());
        assertEquals(DefaultVertexFormat.POSITION, ContentShaders.BLACK_HOLE.format());
        assertEquals(DefaultVertexFormat.BLOCK, ContentShaders.BLACK_HOLE_MASK.format());
    }
}
