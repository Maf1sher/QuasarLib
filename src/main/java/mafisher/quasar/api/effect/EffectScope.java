package mafisher.quasar.api.effect;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.api.scale.SegmentLink;
import mafisher.quasar.effect.EffectSoundValidation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-owned group of visual effects and Minecraft entities created by one effect execution.
 * Finished or culled visuals are pruned from the ownership ledger on the next scope operation.
 */
public final class EffectScope implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectScope.class);

    private final ServerLevel level;
    private final UUID sessionId;
    private final EffectScopeResources resources = new EffectScopeResources();
    private final EffectScopeVisuals visuals;
    private boolean closed;
    private EffectRemovalPolicy removalPolicy = EffectRemovalPolicy.DELETE;

    public EffectScope(ServerLevel level) {
        this(level, UUID.randomUUID());
    }

    public EffectScope(ServerLevel level, UUID sessionId) {
        this.level = Objects.requireNonNull(level, "level");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.visuals = new EffectScopeVisuals(
            this.level,
            this.resources,
            () -> this.removalPolicy
        );
    }

    public UUID sessionId() {
        return this.sessionId;
    }

    public Optional<UUID> playVisual(VisualEffectSpec spec) {
        return this.playVisual(spec, EffectLifetimePolicy.CANCEL_WITH_SCOPE, null);
    }

    public Optional<UUID> playVisual(VisualEffectSpec spec, EffectLifetimePolicy lifetimePolicy) {
        return this.playVisual(spec, lifetimePolicy, null);
    }

    /** Plays a legacy coordinator-linked visual. New segmented effects use the segmented service. */
    public Optional<UUID> playVisual(VisualEffectSpec spec, SegmentLink segmentLink) {
        return this.playVisual(spec, EffectLifetimePolicy.CANCEL_WITH_SCOPE, segmentLink);
    }

    public Optional<UUID> playVisual(
        VisualEffectSpec spec,
        EffectLifetimePolicy lifetimePolicy,
        SegmentLink segmentLink
    ) {
        this.ensureOpen();
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(lifetimePolicy, "lifetimePolicy");
        this.visuals.prune();

        VisualEffectStartResult result = VisualEffectService.start(this.level, spec, segmentLink,
            lifetimePolicy == EffectLifetimePolicy.CANCEL_WITH_SCOPE
                ? EffectPersistenceOptions.DEFAULT.withOwnerSessionId(this.sessionId)
                : EffectPersistenceOptions.DEFAULT);
        if (!result.active()) {
            return Optional.empty();
        }
        UUID instanceId = result.handle().instanceId();
        this.visuals.track(
            instanceId,
            result.handle(),
            lifetimePolicy == EffectLifetimePolicy.CANCEL_WITH_SCOPE
        );
        return Optional.of(instanceId);
    }

    /**
     * Adds an already-started visual handle to this scope's cleanup ledger. Used when a
     * persisted effect restores its child visuals after a restart.
     */
    public void adoptVisual(UUID instanceId, VisualEffectHandle handle) {
        this.ensureOpen();
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(handle, "handle");
        if (!instanceId.equals(handle.instanceId())) {
            throw new IllegalArgumentException("Adopted visual ID does not match its handle");
        }
        this.visuals.prune();
        this.visuals.track(instanceId, handle, true);
    }

    /** Stops a visual created by this scope and removes it from the cleanup ledger. */
    public void stopVisual(UUID instanceId) {
        this.visuals.prune();
        this.visuals.stop(instanceId, EffectRemovalPolicy.DELETE);
    }

    /**
     * Updates a visual created by this scope without changing its lifetime or ownership.
     * Attached visuals follow their parent and reject explicit world transforms.
     */
    public void updateVisual(UUID instanceId, EffectTransform transform) {
        this.ensureOpen();
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(transform, "transform");
        this.visuals.update(instanceId, transform);
    }

    /** Replaces the parameter overrides of a visual created by this scope. */
    public void updateVisualParameters(UUID instanceId, EffectParameterState parameters) {
        this.ensureOpen();
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(parameters, "parameters");
        this.visuals.updateParameters(instanceId, parameters);
    }

    /** Spawns a registered entity and optionally binds its removal to this scope. */
    public <T extends Entity> T spawnEntity(T entity) {
        return this.spawnEntity(entity, EffectLifetimePolicy.CANCEL_WITH_SCOPE);
    }

    public <T extends Entity> T spawnEntity(T entity, EffectLifetimePolicy lifetimePolicy) {
        this.ensureOpen();
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(lifetimePolicy, "lifetimePolicy");
        if (entity.level() != this.level || entity.isRemoved()) {
            throw new IllegalArgumentException("Effect entities must be live and belong to this scope's level");
        }
        if (!this.level.addFreshEntity(entity)) {
            throw new IllegalStateException("The effect entity could not be added to the level");
        }
        if (lifetimePolicy == EffectLifetimePolicy.CANCEL_WITH_SCOPE) {
            this.resources.track(() -> {
                if (!entity.isRemoved()) {
                    entity.discard();
                }
            });
        }
        return entity;
    }

    /** Plays a server-originated positional sound; loops should be owned by a timed effect entity. */
    public void playSound(Vec3 position, SoundEvent sound, SoundSource source, float volume, float pitch) {
        this.ensureOpen();
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(source, "source");
        EffectSoundValidation.requirePlayable(position, volume, pitch, "Invalid effect sound parameters");
        this.level.playSound(null, position.x, position.y, position.z, sound, source, volume, pitch);
    }

    @Override
    public void close() {
        close(EffectRemovalPolicy.DELETE);
    }

    /**
     * Closes the scope. Level unload and server shutdown pass {@link EffectRemovalPolicy#KEEP_FOR_RESTORE}
     * so child visual descriptors survive for restore; explicit stops pass
     * {@link EffectRemovalPolicy#DELETE}.
     */
    public void close(EffectRemovalPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (this.closed) {
            return;
        }
        this.visuals.prune();
        this.closed = true;
        this.removalPolicy = policy;
        try {
            this.resources.close();
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to clean up effect scope {}", this.sessionId, exception);
        } finally {
            this.visuals.clear();
        }
    }

    public int activeVisualCount() {
        return this.visuals.size();
    }

    private void ensureOpen() {
        if (this.closed) {
            throw new IllegalStateException("The effect scope is already closed");
        }
    }
}
