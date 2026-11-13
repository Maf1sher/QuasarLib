package mafisher.quasar.api.gameplay.policy;

/**
 * Server-side admission policy for destructive gameplay effects. Policies run on the server thread
 * when an effect starts or restores; returning {@code false} vetoes execution.
 */
@FunctionalInterface
public interface DestructiveEffectPolicy {
    /**
     * Decides whether one destructive effect may run. An exception also denies the effect and is
     * logged by the engine.
     */
    boolean allows(DestructiveEffectPolicyContext context);
}
