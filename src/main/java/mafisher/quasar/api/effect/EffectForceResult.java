package mafisher.quasar.api.effect;

/**
 * Work completed by one deterministic force-field application.
 *
 * @param examined entities that passed the target policy and fell inside the force volume
 * @param affected entities that received a non-zero velocity delta
 * @param truncated {@code true} when the field, session, or call target cap stopped the scan early
 */
public record EffectForceResult(int examined, int affected, boolean truncated) {
    public static final EffectForceResult NONE = new EffectForceResult(0, 0, false);

    public EffectForceResult {
        if (examined < 0 || affected < 0 || affected > examined) {
            throw new IllegalArgumentException("Effect force results cannot be negative or inconsistent");
        }
    }
}
