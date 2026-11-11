package mafisher.quasar;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

/** Loads a clean default server config for tests that start destructive effects. */
public final class TestConfig {
    private TestConfig() {
    }

    public static void loadDefaults() {
        CommentedConfig config = CommentedConfig.inMemory();
        QuasarConfig.SPEC.correct(config);
        QuasarConfig.SPEC.acceptConfig(loadedConfig(config));
    }

    public static void unload() {
        QuasarConfig.SPEC.acceptConfig(null);
        QuasarConfig.DESTRUCTIVE_EFFECTS_ENABLED.clearCache();
    }

    private static IConfigSpec.ILoadedConfig loadedConfig(CommentedConfig config) {
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
