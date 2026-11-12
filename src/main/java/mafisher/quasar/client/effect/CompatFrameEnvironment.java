package mafisher.quasar.client.effect;

import java.util.Objects;
import java.util.function.DoubleFunction;
import mafisher.quasar.compat.RenderEnvironment;

/**
 * Caches the compatibility environment for one client frame.
 *
 * <p>World and screen stages run in separate events during the same frame; both
 * must read the same resolved environment without asking providers twice.</p>
 */
final class CompatFrameEnvironment {
    private final DoubleFunction<RenderEnvironment> resolver;
    private RenderEnvironment environment;
    private boolean resolved;

    CompatFrameEnvironment(DoubleFunction<RenderEnvironment> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    RenderEnvironment beginFrame(double farPlane) {
        RenderEnvironment next = Objects.requireNonNull(this.resolver.apply(farPlane), "environment");
        this.environment = next;
        this.resolved = true;
        return next;
    }

    RenderEnvironment current(double farPlane) {
        return this.resolved ? this.environment : beginFrame(farPlane);
    }

    void endFrame() {
        this.environment = null;
        this.resolved = false;
    }
}
