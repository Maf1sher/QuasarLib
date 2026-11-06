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

    public static final ModConfigSpec SPEC = BUILDER.build();

    private QuasarConfig() {
    }

    /** Whether destructive gameplay effects may start or resume on this server. */
    public static boolean destructiveEffectsEnabled() {
        return DESTRUCTIVE_EFFECTS_ENABLED.get();
    }
}
