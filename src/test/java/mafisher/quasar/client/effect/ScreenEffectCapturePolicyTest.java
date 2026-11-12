package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ScreenEffectCapturePolicyTest {
    @Test
    void keepsTheExactInputsWhileTheOperationSetIsStable() {
        assertSame(ScreenEffectInputs.NONE,
            ScreenEffectCapturePolicy.forNextFrame(ScreenEffectInputs.NONE, ScreenEffectInputs.NONE));
        assertSame(ScreenEffectInputs.ALL,
            ScreenEffectCapturePolicy.forNextFrame(ScreenEffectInputs.ALL, ScreenEffectInputs.ALL));
    }

    @Test
    void requestsEveryCaptureForOneFrameAfterTheOperationSetChanges() {
        assertSame(ScreenEffectInputs.ALL,
            ScreenEffectCapturePolicy.forNextFrame(ScreenEffectInputs.NONE, ScreenEffectInputs.ALL));
        assertSame(ScreenEffectInputs.ALL,
            ScreenEffectCapturePolicy.forNextFrame(ScreenEffectInputs.ALL, ScreenEffectInputs.NONE));
    }

    @Test
    void treatsAnEquivalentInstanceAsStable() {
        assertEquals(new ScreenEffectInputs(false, true),
            ScreenEffectCapturePolicy.forNextFrame(
                new ScreenEffectInputs(false, true), new ScreenEffectInputs(false, true)));
    }
}
