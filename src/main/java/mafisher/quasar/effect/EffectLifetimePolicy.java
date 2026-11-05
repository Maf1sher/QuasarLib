package mafisher.quasar.effect;

/** Whether an effect resource is cancelled with its owning scope or finishes its own finite lifetime. */
public enum EffectLifetimePolicy {
    CANCEL_WITH_SCOPE,
    FINISH_NATURALLY
}
