package mafisher.quasar.api.compat;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import mafisher.quasar.api.effect.EffectTransform;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Guards a sub-level provider so failing calls read as absent poses. */
final class GuardedSubLevelProvider implements SubLevelProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuardedSubLevelProvider.class);
    private final ResourceLocation id;
    private final SubLevelProvider delegate;
    private final AtomicBoolean loggedAvailabilityFailure = new AtomicBoolean();
    private final AtomicBoolean loggedPoseFailure = new AtomicBoolean();

    GuardedSubLevelProvider(ResourceLocation id, SubLevelProvider delegate) {
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
                LOGGER.warn(
                    "Sub-level provider {} failed its availability check; treating it as unavailable",
                    this.id,
                    exception
                );
            }
            return false;
        }
    }

    @Override
    public Optional<EffectTransform> pose(UUID subLevelId) {
        Objects.requireNonNull(subLevelId, "subLevelId");
        try {
            Optional<EffectTransform> pose = this.delegate.pose(subLevelId);
            return pose == null ? Optional.empty() : pose;
        } catch (RuntimeException exception) {
            if (this.loggedPoseFailure.compareAndSet(false, true)) {
                LOGGER.warn("Sub-level provider {} failed a pose lookup; treating it as absent",
                    this.id, exception);
            }
            return Optional.empty();
        }
    }
}
