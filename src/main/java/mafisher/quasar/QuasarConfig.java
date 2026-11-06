package mafisher.quasar;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side configuration for destructive gameplay effects. */
public final class QuasarConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec.BooleanValue DESTRUCTIVE_EFFECTS_ENABLED = BUILDER
        .comment(
            "Allows gameplay effects that declare the destructive capability to start and restore.",
            "Disabling this prevents execution; effect definitions and persisted descriptors are kept.",
            "Requires a world restart."
        )
        .worldRestart()
        .define("destructive_effects_enabled", true);

    static final ModConfigSpec.IntValue BLOCK_JOURNAL_MAX_ENTRIES = BUILDER
        .comment(
            "Maximum recorded block changes per destructive effect.",
            "Overflow still applies changes but disables rollback for that effect.",
            "Requires a world restart."
        )
        .worldRestart()
        .defineInRange("block_journal_max_entries", 65_536, 1, 1_000_000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private QuasarConfig() {
    }

    /** Whether destructive gameplay effects may start or resume on this server. */
    public static boolean destructiveEffectsEnabled() {
        try {
            return DESTRUCTIVE_EFFECTS_ENABLED.get();
        } catch (IllegalStateException notLoaded) {
            return DESTRUCTIVE_EFFECTS_ENABLED.getDefault();
        }
    }

    /** Maximum recorded block changes per effect before rollback is disabled. */
    public static int blockJournalMaxEntries() {
        try {
            return BLOCK_JOURNAL_MAX_ENTRIES.get();
        } catch (IllegalStateException notLoaded) {
            return BLOCK_JOURNAL_MAX_ENTRIES.getDefault();
        }
    }
}
