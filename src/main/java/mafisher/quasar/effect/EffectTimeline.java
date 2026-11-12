package mafisher.quasar.effect;

/** Pure timeline math shared by server-side scheduling and client-side rendering. */
public final class EffectTimeline {
    /** Shared maximum authored lifetime for every timed effect and audio plan. */
    public static final int MAX_DURATION_TICKS = 72_000;

    private EffectTimeline() {
    }

    /** Whole ticks elapsed since {@code startTime}, clamped to {@code [0, Integer.MAX_VALUE]}. */
    public static int ageTicks(long worldTime, long startTime) {
        long age = worldTime - startTime;
        if (age <= 0L) {
            return 0;
        }
        return (int)Math.min(Integer.MAX_VALUE, age);
    }

    public static double ageTicks(long worldTime, float partialTick, long startTime) {
        double partial = Float.isFinite(partialTick) ? clamp(partialTick, 0.0F, 1.0F) : 0.0;
        return Math.max(0.0, (double)worldTime + partial - startTime);
    }

    /** Whether {@code durationTicks} fits the shared one-tick-to-72,000-tick range. */
    public static boolean isValidDuration(int durationTicks) {
        return durationTicks >= 1 && durationTicks <= MAX_DURATION_TICKS;
    }

    public static float progress(long worldTime, float partialTick, long startTime, int durationTicks) {
        requireDuration(durationTicks);
        return (float)Math.min(1.0, ageTicks(worldTime, partialTick, startTime) / durationTicks);
    }

    public static boolean isFinished(long worldTime, long startTime, int durationTicks) {
        requireDuration(durationTicks);
        return worldTime - startTime >= durationTicks;
    }

    public static float fadeEnvelope(float progress, float fadeInFraction, float fadeOutFraction) {
        float normalizedProgress = clamp(progress, 0.0F, 1.0F);
        float fadeIn = clamp(fadeInFraction, 0.0F, 1.0F);
        float fadeOut = clamp(fadeOutFraction, 0.0F, 1.0F);
        float fadeInAlpha = fadeIn == 0.0F ? 1.0F : Math.min(1.0F, normalizedProgress / fadeIn);
        float fadeOutAlpha = fadeOut == 0.0F
            ? 1.0F
            : Math.min(1.0F, (1.0F - normalizedProgress) / fadeOut);
        return Math.min(fadeInAlpha, fadeOutAlpha);
    }

    public static float easeOutCubic(float progress) {
        float remaining = 1.0F - clamp(progress, 0.0F, 1.0F);
        return 1.0F - remaining * remaining * remaining;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void requireDuration(int durationTicks) {
        if (!isValidDuration(durationTicks)) {
            throw new IllegalArgumentException(
                "Effect duration must be between 1 and " + MAX_DURATION_TICKS + " ticks"
            );
        }
    }
}
