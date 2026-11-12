package mafisher.quasar;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

/** Loads a clean default server config for tests that start destructive effects. */
public final class TestConfig {
    private TestConfig() {
    }

    public static void loadDefaults() {
        load(config -> {
        });
    }

    /** Loads corrected defaults after applying one test-specific customization. */
    public static void load(Consumer<CommentedConfig> customizer) {
        CommentedConfig config = CommentedConfig.inMemory();
        QuasarConfig.SPEC.correct(config);
        customizer.accept(config);
        clearCaches();
        QuasarConfig.SPEC.acceptConfig(loadedConfig(config));
    }

    public static void unload() {
        QuasarConfig.SPEC.acceptConfig(null);
        clearCaches();
    }

    private static void clearCaches() {
        QuasarConfig.DESTRUCTIVE_EFFECTS_ENABLED.clearCache();
        QuasarConfig.BLOCK_JOURNAL_MAX_ENTRIES.clearCache();
        QuasarConfig.BLOCK_JOURNAL_MAX_SECTIONS.clearCache();
        QuasarConfig.STANDALONE_VISUAL_BUDGET.clearCache();
        QuasarConfig.SEGMENT_BUDGET.clearCache();
        QuasarConfig.GAMEPLAY_SESSION_BUDGET.clearCache();
        QuasarConfig.GAMEPLAY_SESSIONS_PER_OWNER_BUDGET.clearCache();
        QuasarConfig.GAMEPLAY_STARTS_PER_OWNER_PER_TICK_BUDGET.clearCache();
        QuasarConfig.GAMEPLAY_PENDING_BUDGET.clearCache();
        QuasarConfig.CINEMATIC_SESSION_BUDGET.clearCache();
        QuasarConfig.BLOCK_CHANGES_PER_LEVEL_TICK_BUDGET.clearCache();
        QuasarConfig.SECTION_EDITS_PER_LEVEL_TICK_BUDGET.clearCache();
        QuasarConfig.TERRAIN_TICKET_BUDGET.clearCache();
        QuasarConfig.ENTITY_SPAWNS_PER_LEVEL_TICK_BUDGET.clearCache();
    }

    static IConfigSpec.ILoadedConfig loadedConfig(CommentedConfig config) {
        try {
            Class<?> type = Class.forName("net.neoforged.fml.config.LoadedConfig");
            Constructor<?> constructor = type.getDeclaredConstructor(
                CommentedConfig.class,
                Path.class,
                ModConfig.class
            );
            constructor.setAccessible(true);
            return (IConfigSpec.ILoadedConfig) constructor.newInstance(
                config,
                Path.of("quasar-test.toml"),
                null
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create a loaded test config", exception);
        }
    }
}
