package mafisher.quasar.api.client;

import java.util.Objects;

/**
 * Immutable per-effect selection of the operation applied by the screen compositor.
 *
 * <p>Built-in factories attach the operation's typed settings. Custom operations
 * use {@link #custom(ScreenEffectOperation, Object)} and read their payload in
 * the renderer through {@link #settings(Class)}.</p>
 */
public record ScreenEffectSettings(ScreenEffectOperation operation, Object settings) {
    public static final ScreenEffectSettings DIAGNOSTIC_PREVIEW =
        new ScreenEffectSettings(ScreenEffectOperation.DIAGNOSTIC_MASK_PREVIEW, null);

    public ScreenEffectSettings {
        Objects.requireNonNull(operation, "operation");
    }

    public ScreenEffectSettings(ScreenEffectOperation operation) {
        this(operation, null);
    }

    public static ScreenEffectSettings waveDistortion(WaveDistortionSettings settings) {
        return new ScreenEffectSettings(ScreenEffectOperation.MASK_SCOPED_WAVE_DISTORTION,
            Objects.requireNonNull(settings, "settings"));
    }

    public static ScreenEffectSettings sceneBlur(SceneBlurSettings settings) {
        return new ScreenEffectSettings(ScreenEffectOperation.MASK_SCOPED_SCENE_BLUR,
            Objects.requireNonNull(settings, "settings"));
    }

    public static ScreenEffectSettings bloom(BloomSettings settings) {
        return new ScreenEffectSettings(ScreenEffectOperation.MASK_SCOPED_BLOOM,
            Objects.requireNonNull(settings, "settings"));
    }

    public static ScreenEffectSettings blackHole(BlackHoleScreenSettings settings) {
        return new ScreenEffectSettings(ScreenEffectOperation.MASK_SCOPED_BLACK_HOLE,
            Objects.requireNonNull(settings, "settings"));
    }

    /** Creates settings for a consumer-owned operation. The payload may be {@code null}. */
    public static ScreenEffectSettings custom(ScreenEffectOperation operation, Object settings) {
        return new ScreenEffectSettings(operation, settings);
    }

    /**
     * Returns the payload as {@code type}.
     *
     * @throws IllegalStateException when the operation has no settings
     * @throws IllegalArgumentException when the payload is a different type
     */
    public <T> T settings(Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (this.settings == null) {
            throw new IllegalStateException("Screen effect operation " + this.operation.id()
                + " has no settings");
        }
        if (!type.isInstance(this.settings)) {
            throw new IllegalArgumentException("Screen effect operation " + this.operation.id()
                + " settings are " + this.settings.getClass().getName() + ", not " + type.getName());
        }
        return type.cast(this.settings);
    }
}
