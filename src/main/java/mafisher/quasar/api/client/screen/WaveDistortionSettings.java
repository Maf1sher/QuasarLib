package mafisher.quasar.api.client.screen;

/** Validated parameters for the mask-scoped horizontal wave distortion operation. */
public record WaveDistortionSettings(
    float amplitudePixels,
    float frequencyCycles,
    float speedCyclesPerTick
) {
    public static final WaveDistortionSettings DEBUG_DEFAULT = new WaveDistortionSettings(8.0F, 12.0F, 0.035F);

    public WaveDistortionSettings {
        if (!Float.isFinite(amplitudePixels) || amplitudePixels < 0.0F || amplitudePixels > 64.0F
            || !Float.isFinite(frequencyCycles) || frequencyCycles <= 0.0F || frequencyCycles > 128.0F
            || !Float.isFinite(speedCyclesPerTick) || speedCyclesPerTick < 0.0F || speedCyclesPerTick > 1.0F) {
            throw new IllegalArgumentException("Invalid wave distortion settings");
        }
    }
}
