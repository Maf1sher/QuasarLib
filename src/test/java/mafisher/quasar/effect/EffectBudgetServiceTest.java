package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mafisher.quasar.api.effect.budget.EffectPriority;
import org.junit.jupiter.api.Test;

class EffectBudgetServiceTest {
    @Test
    void selectsTheLowestPriorityMapEntryAndKeepsTheOldestTie() {
        UUID background = UUID.randomUUID();
        UUID oldestLow = UUID.randomUUID();
        UUID newestLow = UUID.randomUUID();
        Map<UUID, EffectPriority> candidates = new LinkedHashMap<>();
        candidates.put(oldestLow, EffectPriority.LOW);
        candidates.put(background, EffectPriority.BACKGROUND);
        candidates.put(newestLow, EffectPriority.LOW);

        assertEquals(background,
            EffectBudgetService.lowestPriority(candidates, EffectPriority::weight, null));
        assertEquals(oldestLow,
            EffectBudgetService.lowestPriority(candidates, EffectPriority::weight, background));
    }

    @Test
    void selectsTheFirstLowestPriorityListElement() {
        EffectPriority first = EffectPriority.LOW;
        EffectPriority second = EffectPriority.BACKGROUND;
        EffectPriority third = EffectPriority.BACKGROUND;

        assertEquals(second,
            EffectBudgetService.lowestPriority(List.of(first, second, third), EffectPriority::weight));
        assertNull(EffectBudgetService.lowestPriority(List.of(), EffectPriority::weight));
    }
}
