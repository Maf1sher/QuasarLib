package mafisher.quasar.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-owned group of visual effects and Minecraft entities created by one effect execution. */
public final class EffectScope implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");

    private final ServerLevel level;
    private final UUID sessionId;
    private final Map<UUID, VisualEffectHandle> activeVisuals = new HashMap<>();
    private final EffectScopeResources resources = new EffectScopeResources();
    private boolean closed;

    public EffectScope(ServerLevel level) {
        this(level, UUID.randomUUID());
    }

    public EffectScope(ServerLevel level, UUID sessionId) {
        this.level = Objects.requireNonNull(level, "level");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
    }

    public UUID sessionId() {
        return this.sessionId;
    }

    public UUID playVisual(VisualEffectSpec spec) {
        return this.playVisual(spec, EffectLifetimePolicy.CANCEL_WITH_SCOPE);
    }

    public UUID playVisual(VisualEffectSpec spec, EffectLifetimePolicy lifetimePolicy) {
        this.ensureOpen();
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(lifetimePolicy, "lifetimePolicy");

        VisualEffectHandle handle = VisualEffectService.start(this.level, spec);
        UUID instanceId = handle.instanceId();
        this.activeVisuals.put(instanceId, handle);
        if (lifetimePolicy == EffectLifetimePolicy.CANCEL_WITH_SCOPE) {
            this.resources.track(() -> this.stopVisual(instanceId));
        }
        return instanceId;
    }

    public void stopVisual(UUID instanceId) {
        VisualEffectHandle handle = this.activeVisuals.remove(Objects.requireNonNull(instanceId, "instanceId"));
        if (handle == null) {
            return;
        }
        VisualEffectService.stop(this.level, handle);
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
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
            || !Float.isFinite(volume) || volume < 0.0F
            || !Float.isFinite(pitch) || pitch <= 0.0F) {
            throw new IllegalArgumentException("Invalid effect sound parameters");
        }
        this.level.playSound(null, position.x, position.y, position.z, sound, source, volume, pitch);
    }

    @Override
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        try {
            this.resources.close();
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to clean up effect scope {}", this.sessionId, exception);
        } finally {
            this.activeVisuals.clear();
        }
    }

    private void ensureOpen() {
        if (this.closed) {
            throw new IllegalStateException("The effect scope is already closed");
        }
    }
}
