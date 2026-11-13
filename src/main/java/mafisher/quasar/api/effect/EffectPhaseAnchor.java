package mafisher.quasar.api.effect;

import java.util.Objects;

/**
 * Named, duration-independent position on an effect timeline.
 *
 * <p>An anchor is a normalized progress in {@code [0, 1]}, so a curve authored against it keeps
 * the same shape for every effect duration. Anchors are owned by
 * {@link EffectParameterSet} and can be derived from an {@link mafisher.quasar.api.audio.EffectPhaseTiming}.</p>
 */
public record EffectPhaseAnchor(String name, float progress) {
    public static final int MAX_NAME_LENGTH = 64;

    public EffectPhaseAnchor {
        requireName(name);
        if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
            throw new IllegalArgumentException("Effect phase anchor progress must be finite and in [0, 1]");
        }
    }

    /** Validates a parameter or anchor name shared by curves and parameter sets. */
    public static String requireName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                "Effect parameter and anchor names must be non-blank and at most "
                    + MAX_NAME_LENGTH + " characters"
            );
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Character.isWhitespace(character) || Character.isISOControl(character)) {
                throw new IllegalArgumentException(
                    "Effect parameter and anchor names cannot contain whitespace or control characters"
                );
            }
        }
        return name;
    }
}
