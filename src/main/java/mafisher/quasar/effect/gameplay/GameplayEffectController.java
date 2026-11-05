package mafisher.quasar.effect.gameplay;

/** Server-side behavior owned by one gameplay effect session. */
public interface GameplayEffectController {
    /** Called once at age zero, before the session is registered. */
    void onStart(GameplayEffectContext context);

    /** Called when the session enters a phase, including phase zero during start. */
    default void onPhaseEnter(GameplayEffectContext context, int phaseIndex) {
    }

    /** Called once per server tick while the session is active. */
    void onTick(GameplayEffectContext context);

    /** Called before the session scope is closed; it is terminal, so do not start new work or sessions. */
    default void onEnd(GameplayEffectContext context, GameplayEffectEndReason reason) {
    }
}
