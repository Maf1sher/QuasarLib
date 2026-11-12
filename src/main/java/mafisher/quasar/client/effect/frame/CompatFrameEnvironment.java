package mafisher.quasar.client.effect.frame;

import java.util.Objects;
import java.util.function.DoubleFunction;
import mafisher.quasar.api.compat.RenderEnvironment;
import org.jetbrains.annotations.ApiStatus;

/**
 * Caches the compatibility environment for one client frame.
 *
 * <p>World and screen stages run in separate events during the same frame; both
 * must read the same resolved environment without asking providers twice.</p>
 */
@ApiStatus.Internal
public final class CompatFrameEnvironment {
    private final DoubleFunction<RenderEnvironment> resolver;
    private RenderEnvironment environment;
    private boolean resolved;

    public CompatFrameEnvironment(DoubleFunction<RenderEnvironment> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public RenderEnvironment beginFrame(double farPlane) {
        RenderEnvironment next = Objects.requireNonNull(this.resolver.apply(farPlane), "environment");
        this.environment = next;
        this.resolved = true;
        return next;
    }

    public RenderEnvironment current(double farPlane) {
        return this.resolved ? this.environment : beginFrame(farPlane);
    }

    public void endFrame() {
        this.environment = null;
        this.resolved = false;
    }
}
