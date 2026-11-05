package mafisher.quasar.effect;

/** Work completed by one deterministic hitbox application. */
public record EffectHitResult(int examined, int damaged) {
    public static final EffectHitResult NONE = new EffectHitResult(0, 0);

    public EffectHitResult {
        if (examined < 0 || damaged < 0 || damaged > examined) {
            throw new IllegalArgumentException("Effect hit results cannot be negative or inconsistent");
        }
    }
}
