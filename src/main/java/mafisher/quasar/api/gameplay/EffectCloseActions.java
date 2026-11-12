package mafisher.quasar.api.gameplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Runs engine-owned session cleanup in reverse registration order, even after a failure. */
final class EffectCloseActions {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectCloseActions.class);

    private final List<Runnable> actions = new ArrayList<>();

    void add(Runnable action) {
        this.actions.add(Objects.requireNonNull(action, "action"));
    }

    void run(String effectId) {
        try {
            for (int index = this.actions.size() - 1; index >= 0; index--) {
                try {
                    this.actions.get(index).run();
                } catch (RuntimeException exception) {
                    LOGGER.warn("Gameplay effect {} cleanup failed", effectId, exception);
                }
            }
        } finally {
            this.actions.clear();
        }
    }
}
