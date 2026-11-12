package mafisher.quasar.api.compat;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import mafisher.quasar.api.compat.PhysicsBridge;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Guards a physics bridge so a failing availability check reads as absent. */
final class GuardedPhysicsBridge implements PhysicsBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuardedPhysicsBridge.class);
    private final ResourceLocation id;
    private final PhysicsBridge delegate;
    private final AtomicBoolean loggedFailure = new AtomicBoolean();

    GuardedPhysicsBridge(ResourceLocation id, PhysicsBridge delegate) {
        this.id = Objects.requireNonNull(id, "id");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public boolean available() {
        try {
            return this.delegate.available();
        } catch (RuntimeException exception) {
            if (this.loggedFailure.compareAndSet(false, true)) {
                LOGGER.warn("Physics bridge {} failed its availability check; treating it as unavailable",
                    this.id, exception);
            }
            return false;
        }
    }
}
