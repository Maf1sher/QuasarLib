package mafisher.quasar.api.compat;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import mafisher.quasar.api.block.BlockProtectionContext;
import mafisher.quasar.api.compat.ProtectionBridge;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Wraps a protection bridge so failures deny changes and read as unavailable. */
final class GuardedProtectionBridge implements ProtectionBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuardedProtectionBridge.class);
    private final ResourceLocation id;
    private final ProtectionBridge delegate;
    private final AtomicBoolean loggedAvailabilityFailure = new AtomicBoolean();
    private final AtomicBoolean loggedAllowFailure = new AtomicBoolean();

    GuardedProtectionBridge(ResourceLocation id, ProtectionBridge delegate) {
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
            if (this.loggedAvailabilityFailure.compareAndSet(false, true)) {
                LOGGER.warn("Protection bridge {} failed its availability check; treating it as unavailable",
                    this.id, exception);
            }
            return false;
        }
    }

    @Override
    public boolean allows(BlockProtectionContext context) {
        try {
            return this.delegate.allows(context);
        } catch (RuntimeException exception) {
            if (this.loggedAllowFailure.compareAndSet(false, true)) {
                LOGGER.error("Protection bridge {} failed; denying block changes from it",
                    this.id, exception);
            }
            return false;
        }
    }
}
