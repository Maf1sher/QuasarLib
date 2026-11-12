package mafisher.quasar;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.function.Consumer;

/** Loads a clean default client config for tests that read client settings. */
public final class TestClientConfig {
    private TestClientConfig() {
    }

    public static void loadDefaults() {
        load(config -> {
        });
    }

    /** Loads corrected defaults after applying one test-specific customization. */
    public static void load(Consumer<CommentedConfig> customizer) {
        CommentedConfig config = CommentedConfig.inMemory();
        QuasarClientConfig.SPEC.correct(config);
        customizer.accept(config);
        QuasarClientConfig.SPEC.acceptConfig(TestConfig.loadedConfig(config));
    }

    public static void unload() {
        QuasarClientConfig.SPEC.acceptConfig(null);
    }
}
