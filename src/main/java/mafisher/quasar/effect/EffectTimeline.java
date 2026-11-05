package mafisher.quasar.effect;

/** Pure timeline math shared by server-side scheduling and client-side rendering. */
public final class EffectTimeline {
    private EffectTimeline() {
    }

    public static double ageTicks(long worldTime, float partialTick, long startTime) {
        double partial = Float.isFinite(partialTick) ? clamp(partialTick, 0.0F, 1.0F) : 0.0;
        return Math.max(0.0, (double)worldTime + partial - startTime);
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
        if (durationTicks < 1) {
            throw new IllegalArgumentException("Effect duration must be at least one tick");
        }
    }
}
