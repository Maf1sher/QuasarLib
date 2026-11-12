package mafisher.quasar;

import mafisher.quasar.api.effect.EffectQualityTier;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-side presentation quality configuration. Loaded only on the physical client. */
public final class QuasarClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static final ModConfigSpec.EnumValue<QualityMode> QUALITY_MODE = BUILDER
        .comment(
            "Client presentation quality.",
            "AUTO lowers quality when the measured effect render cost exceeds effect_budget_ms.",
            "A fixed tier disables automatic degradation."
        )
        .defineEnum("quality_mode", QualityMode.AUTO);

    static final ModConfigSpec.EnumValue<EffectQualityTier> AUTO_MAX_TIER = BUILDER
        .comment(
            "Highest tier automatic degradation may use.",
            "Ignored when quality_mode is not AUTO."
        )
        .defineEnum("auto_max_tier", EffectQualityTier.HIGH);

    static final ModConfigSpec.DoubleValue EFFECT_BUDGET_MS = BUILDER
        .comment(
            "Soft per-frame budget for QuasarLib world rendering and screen composition, in milliseconds.",
            "This is a degradation threshold, not a regression gate.",
            "Used only in AUTO mode."
        )
        .defineInRange("effect_budget_ms", 2.5, 0.25, 50.0);

    static final ModConfigSpec.BooleanValue CINEMATIC_IMPULSES = BUILDER
        .comment(
            "Allow cinematic effects to add camera shake and screen pulses.",
            "Disable this for a steadier screen or light-sensitivity comfort."
        )
        .define("cinematic_impulses", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private QuasarClientConfig() {
    }

    public static QualityMode qualityMode() {
        try {
            return QUALITY_MODE.get();
        } catch (IllegalStateException notLoaded) {
            return QUALITY_MODE.getDefault();
        }
    }

    public static EffectQualityTier autoMaxTier() {
        try {
            return AUTO_MAX_TIER.get();
        } catch (IllegalStateException notLoaded) {
            return AUTO_MAX_TIER.getDefault();
        }
    }

    public static double effectBudgetMillis() {
        try {
            return EFFECT_BUDGET_MS.get();
        } catch (IllegalStateException notLoaded) {
            return EFFECT_BUDGET_MS.getDefault();
        }
    }

    /** Whether cinematic camera shake and screen flashes are allowed. */
    public static boolean cinematicImpulses() {
        try {
            return CINEMATIC_IMPULSES.get();
        } catch (IllegalStateException notLoaded) {
            return CINEMATIC_IMPULSES.getDefault();
        }
    }

    /** Configured quality mode. */
    public enum QualityMode {
        AUTO,
        LOW,
        MEDIUM,
        HIGH,
        ULTRA;

        /** Returns the fixed tier, or {@code null} for {@link #AUTO}. */
        public EffectQualityTier fixedTier() {
            return switch (this) {
                case AUTO -> null;
                case LOW -> EffectQualityTier.LOW;
                case MEDIUM -> EffectQualityTier.MEDIUM;
                case HIGH -> EffectQualityTier.HIGH;
                case ULTRA -> EffectQualityTier.ULTRA;
            };
        }
    }
}
