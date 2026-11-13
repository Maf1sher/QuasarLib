package mafisher.quasar.api.effect.budget;

/** Hard validation limits shared by effect specs, descriptors, and network payloads. */
public final class EffectLimits {
    /** Largest authored scale for one visual, segmented, or gameplay effect. */
    public static final float MAX_SCALE = 128.0F;

    private EffectLimits() {
    }
}
