package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import mafisher.quasar.effect.EffectVisualIds;
import org.junit.jupiter.api.Test;

class ContentEffectIdsTest {
    @Test
    void usesFinalContentNamespace() {
        assertEquals("quasar_content", ContentBootstrap.NAMESPACE);
        assertEquals("quasar_content", ContentEffectIds.RUNE_CIRCLE.getNamespace());
        assertEquals("rune_circle", ContentEffectIds.RUNE_CIRCLE.getPath());
    }

    @Test
    void staysSeparateFromEngineDebugFixtures() {
        assertFalse(ContentEffectIds.RUNE_CIRCLE.getPath().startsWith("debug_"));
        assertNotEquals(EffectVisualIds.DEBUG_RUNE_CIRCLE, ContentEffectIds.RUNE_CIRCLE);
    }
}
