package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EffectScopeResourcesTest {
    @Test
    void closesTrackedResourcesOnceInReverseCreationOrder() {
        EffectScopeResources resources = new EffectScopeResources();
        List<String> cleaned = new ArrayList<>();
        resources.track(() -> cleaned.add("first"));
        resources.track(() -> cleaned.add("second"));

        resources.close();
        resources.close();

        assertEquals(List.of("second", "first"), cleaned);
    }

    @Test
    void untrackRemovesOnePendingCleanup() {
        EffectScopeResources resources = new EffectScopeResources();
        List<String> cleaned = new ArrayList<>();
        Object cancelled = new Object();
        resources.track(cancelled, () -> cleaned.add("cancelled"));
        resources.track(() -> cleaned.add("kept"));

        resources.untrack(cancelled);
        resources.close();

        assertEquals(List.of("kept"), cleaned);
    }

    @Test
    void cleansResourcesAddedAfterCloseImmediately() {
        EffectScopeResources resources = new EffectScopeResources();
        resources.close();
        List<String> cleaned = new ArrayList<>();

        resources.track(() -> cleaned.add("late"));

        assertEquals(List.of("late"), cleaned);
    }

    @Test
    void continuesCleanupAndReportsFailures() {
        EffectScopeResources resources = new EffectScopeResources();
        List<String> cleaned = new ArrayList<>();
        resources.track(() -> cleaned.add("last"));
        resources.track(() -> {
            throw new IllegalStateException("cleanup failed");
        });

        assertThrows(IllegalStateException.class, resources::close);
        assertEquals(List.of("last"), cleaned);
    }
}
