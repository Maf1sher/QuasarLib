package mafisher.quasar.content.rune;

/** Payload for the content-owned rune glow screen operation. */
record ContentRuneGlowSettings(float strength, float pulseSpeed, float red, float green,
    float blue) {
    static final ContentRuneGlowSettings DEFAULT = new ContentRuneGlowSettings(
        0.55F, 0.05F, 0.18F, 0.72F, 1.0F
    );

    ContentRuneGlowSettings {
        if (!Float.isFinite(strength) || strength < 0.0F || strength > 4.0F
            || !Float.isFinite(pulseSpeed) || pulseSpeed < 0.0F || pulseSpeed > 1.0F
            || !isUnit(red) || !isUnit(green) || !isUnit(blue)) {
            throw new IllegalArgumentException("Invalid rune glow settings");
        }
    }

    private static boolean isUnit(float value) {
        return Float.isFinite(value) && value >= 0.0F && value <= 1.0F;
    }
}
