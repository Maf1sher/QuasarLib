package mafisher.quasar.effect.persistence;

import java.util.List;

/** Summary of one persistence restore attempt for diagnostics and the debug command. */
public record EffectRecoveryReport(
    int restored,
    int expired,
    int quarantined,
    int unsupported,
    int rollbacks,
    List<String> messages
) {
    public static final EffectRecoveryReport EMPTY =
        new EffectRecoveryReport(0, 0, 0, 0, 0, List.of());

    public EffectRecoveryReport {
        messages = List.copyOf(messages);
    }

    /** Source-compatible constructor without rollback counts. */
    public EffectRecoveryReport(
        int restored,
        int expired,
        int quarantined,
        int unsupported,
        List<String> messages
    ) {
        this(restored, expired, quarantined, unsupported, 0, messages);
    }
}
