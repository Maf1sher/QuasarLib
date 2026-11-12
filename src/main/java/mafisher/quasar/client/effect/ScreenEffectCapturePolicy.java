package mafisher.quasar.client.effect;

import java.util.Objects;

/** Chooses the optional captures for the next frame from the previous and current operation requirements. */
final class ScreenEffectCapturePolicy {
    private ScreenEffectCapturePolicy() {
    }

    static ScreenEffectInputs forNextFrame(ScreenEffectInputs required, ScreenEffectInputs next) {
        Objects.requireNonNull(required, "required");
        Objects.requireNonNull(next, "next");
        // A changed operation set gets one conservative frame; optional captures may lag by the transition frame,
        // which operations already handle through hasOpaqueColor()/hasForegroundDepth().
        return next.equals(required) ? next : ScreenEffectInputs.ALL;
    }
}
