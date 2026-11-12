package mafisher.quasar.client.effect.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;
import mafisher.quasar.api.compat.RenderEnvironment;
import org.junit.jupiter.api.Test;

class CompatFrameEnvironmentTest {
    @Test
    void resolvesOncePerFrame() {
        AtomicInteger calls = new AtomicInteger();
        CompatFrameEnvironment frame = frame(calls);

        assertSame(RenderEnvironment.VANILLA, frame.beginFrame(256.0));
        assertSame(RenderEnvironment.VANILLA, frame.current(256.0));

        assertEquals(1, calls.get());
    }

    @Test
    void currentWithoutBeginResolvesImmediately() {
        AtomicInteger calls = new AtomicInteger();
        CompatFrameEnvironment frame = frame(calls);

        assertSame(RenderEnvironment.VANILLA, frame.current(256.0));

        assertEquals(1, calls.get());
    }

    @Test
    void endFrameAllowsTheNextFrameToResolve() {
        AtomicInteger calls = new AtomicInteger();
        CompatFrameEnvironment frame = frame(calls);

        frame.beginFrame(256.0);
        frame.endFrame();
        frame.beginFrame(256.0);

        assertEquals(2, calls.get());
    }

    private static CompatFrameEnvironment frame(AtomicInteger calls) {
        return new CompatFrameEnvironment(farPlane -> {
            calls.incrementAndGet();
            return RenderEnvironment.VANILLA;
        });
    }
}
