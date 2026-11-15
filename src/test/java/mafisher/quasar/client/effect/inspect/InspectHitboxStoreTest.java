package mafisher.quasar.client.effect.inspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InspectHitboxStoreTest {
    @Test
    void missingSnapshotHasANoopStatusAndNoFade() {
        InspectHitboxStore.clear();

        assertNull(InspectHitboxStore.current(100L));
        assertEquals(0.0F, InspectHitboxStore.fade(100L), 1.0E-6F);
        assertTrue(InspectHitboxStore.status(100L).contains("run /quasarlib hitboxes"));
    }
}
