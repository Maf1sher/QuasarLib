package mafisher.quasar.apicheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.BlockRollbackService;
import org.junit.jupiter.api.Test;

/** Compiles from outside the implementation package to pin the journal/rollback surface. */
class PublicJournalApiVisibilityTest {
    @Test
    void exposesTheJournalAndRollbackServices() {
        BlockJournal journal = new BlockJournal();

        assertEquals(0, journal.size());
        assertFalse(journal.isRollbackPending());
        assertFalse(journal.canRollback());
        assertNotNull(BlockRollbackService.class);
    }
}
