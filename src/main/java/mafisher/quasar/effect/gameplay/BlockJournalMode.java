package mafisher.quasar.effect.gameplay;

/**
 * How a queued block change is recorded for rollback. {@link #PER_BLOCK} stores the previous state
 * of one block. {@link #SECTION} stores one original-state snapshot of the whole 16x16x16 section
 * the first time that section is touched, which keeps scattered edits (surface tearing, impacts)
 * cheap in journal entries while preserving a complete rollback.
 */
public enum BlockJournalMode {
    PER_BLOCK,
    SECTION
}
