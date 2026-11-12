package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ContentEffectIdsTest {
    @Test
    void usesContentModNamespace() {
        assertEquals("quasar_content", ContentBootstrap.MOD_ID);
        assertEquals(ContentBootstrap.MOD_ID, ContentBootstrap.NAMESPACE);
        assertEquals("quasar_content", ContentEffectIds.RUNE_CIRCLE.getNamespace());
        assertEquals("rune_circle", ContentEffectIds.RUNE_CIRCLE.getPath());
        assertEquals("quasar_content", ContentEffectIds.BLACK_HOLE.getNamespace());
        assertEquals("black_hole", ContentEffectIds.BLACK_HOLE.getPath());
        assertEquals("quasar_content", ContentEffectIds.BLACK_HOLE_MESH.getNamespace());
    }

    @Test
    void contentFixturesDoNotUseDebugPrefixes() {
        assertFalse(ContentEffectIds.RUNE_CIRCLE.getPath().startsWith("debug_"));
        assertFalse(ContentEffectIds.BLACK_HOLE.getPath().startsWith("debug_"));
        assertFalse(ContentEffectIds.BLACK_HOLE_MESH.getPath().startsWith("debug_"));
    }
}
