package mafisher.quasar.apicheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.function.Predicate;
import mafisher.quasar.api.effect.debris.EffectDebrisSpec;
import mafisher.quasar.api.effect.force.EffectForceField;
import mafisher.quasar.api.effect.force.EffectForceResult;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import org.junit.jupiter.api.Test;

/** Compiles from outside the implementation package to pin the public force and debris surface. */
class PublicForceApiVisibilityTest {
    @Test
    void exposesForceAndDebrisMethods() throws NoSuchMethodException {
        assertNotNull(GameplayEffectContext.class.getMethod(
            "applyForceField", EffectForceField.class, Predicate.class
        ));
        assertNotNull(GameplayEffectContext.class.getMethod("spawnDebris", EffectDebrisSpec.class));
    }

    @Test
    void exposesForceValues() {
        assertEquals(3, EffectForceResult.class.getRecordComponents().length);
        assertNotNull(EffectForceField.pull(
            mafisher.quasar.api.effect.EffectPoint.ZERO, 1.0, 1.0, 0.0, 1
        ));
    }
}
