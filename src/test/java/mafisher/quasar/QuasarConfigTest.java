package mafisher.quasar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuasarConfigTest {
    @Test
    void enablesDestructiveEffectsByDefault() {
        assertTrue(QuasarConfig.DESTRUCTIVE_EFFECTS_ENABLED.getDefault());
    }

    @Test
    void publishesANonEmptyConfigSpec() {
        assertFalse(QuasarConfig.SPEC.isEmpty());
    }

    @Test
    void fallsBackToTheDefaultBeforeTheConfigLoads() {
        assertTrue(QuasarConfig.destructiveEffectsEnabled());
    }
}
