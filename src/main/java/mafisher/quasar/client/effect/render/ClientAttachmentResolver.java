package mafisher.quasar.client.effect.render;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.api.client.render.VisualEffectInstance;
import mafisher.quasar.api.effect.EffectAttachment;
import mafisher.quasar.api.effect.EffectTransform;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves attached visuals against parent frames during rendering. Successful resolutions are
 * cached for one frame; the parent's movement is read from the client level, so an attached visual
 * never needs a per-tick transform packet.
 */
@ApiStatus.Internal
final class ClientAttachmentResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientAttachmentResolver.class);

    private final Map<UUID, VisualEffectInstance> instances;
    private final ParentFrames frames;
    private final Map<UUID, EffectTransform> cache = new HashMap<>();
    private final Map<UUID, EffectTransform> lastResolved = new HashMap<>();
    private final Set<UUID> visiting = new HashSet<>();
    private final Set<UUID> unresolved = new HashSet<>();
    private final Set<UUID> reportedCycles = new HashSet<>();

    ClientAttachmentResolver(Map<UUID, VisualEffectInstance> instances, ParentFrames frames) {
        this.instances = Objects.requireNonNull(instances, "instances");
        this.frames = Objects.requireNonNull(frames, "frames");
    }

    /** Clears per-frame state; last resolved frames survive so FREEZE_AT_LAST stays stable. */
    void beginFrame() {
        this.cache.clear();
        this.visiting.clear();
        this.unresolved.clear();
    }

    /** Forgets all state for one removed instance. */
    void forget(UUID instanceId) {
        this.cache.remove(instanceId);
        this.lastResolved.remove(instanceId);
        this.unresolved.remove(instanceId);
        this.reportedCycles.remove(instanceId);
    }

    /** Drops every cached frame, for example on a level change. */
    void clear() {
        this.cache.clear();
        this.lastResolved.clear();
        this.visiting.clear();
        this.unresolved.clear();
        this.reportedCycles.clear();
    }

    /**
     * Returns the world transform for one effect this frame, or {@code null} when a
     * {@code STOP_WITH_PARENT} attachment has no parent.
     */
    @Nullable
    EffectTransform resolve(VisualEffectInstance effect, long gameTime, float partialTick) {
        return this.resolveInternal(effect, gameTime, partialTick);
    }

    @Nullable
    private EffectTransform resolveInternal(
        VisualEffectInstance effect,
        long gameTime,
        float partialTick
    ) {
        UUID instanceId = effect.instanceId();
        EffectTransform cached = this.cache.get(instanceId);
        if (cached != null) {
            return cached;
        }
        if (this.unresolved.contains(instanceId)) {
            return null;
        }
        if (!this.visiting.add(instanceId)) {
            this.reportCycle(instanceId);
            this.unresolved.add(instanceId);
            return null;
        }
        if (this.visiting.size() > EffectAttachment.MAX_CHAIN_DEPTH) {
            this.reportCycle(instanceId);
            this.unresolved.add(instanceId);
            this.visiting.remove(instanceId);
            return null;
        }
        try {
            EffectAttachment attachment = effect.payload().attachment();
            if (attachment == null) {
                return effect.interpolatedTransform(gameTime, partialTick);
            }
            if (attachment.parent() instanceof EffectAttachment.Parent.SubLevel
                && !this.frames.subLevelAvailable()) {
                return this.freeze(instanceId, effect);
            }
            EffectTransform parentFrame = this.resolveParent(attachment.parent(), gameTime, partialTick);
            if (parentFrame == null) {
                return this.fallback(instanceId, effect, attachment);
            }
            EffectTransform resolved = attachment.resolve(parentFrame);
            this.cache.put(instanceId, resolved);
            this.lastResolved.put(instanceId, resolved);
            return resolved;
        } finally {
            this.visiting.remove(instanceId);
        }
    }

    @Nullable
    private EffectTransform resolveParent(
        EffectAttachment.Parent parent,
        long gameTime,
        float partialTick
    ) {
        if (parent instanceof EffectAttachment.Parent.Entity entity) {
            return this.frames.entity(entity.id(), partialTick);
        }
        if (parent instanceof EffectAttachment.Parent.Effect effect) {
            VisualEffectInstance instance = this.instances.get(effect.id());
            return instance == null ? null : this.resolveInternal(instance, gameTime, partialTick);
        }
        return this.frames.subLevel(((EffectAttachment.Parent.SubLevel) parent).id(), partialTick);
    }

    @Nullable
    private EffectTransform fallback(
        UUID instanceId,
        VisualEffectInstance effect,
        EffectAttachment attachment
    ) {
        if (attachment.parentLossPolicy() == EffectAttachment.ParentLossPolicy.FREEZE_AT_LAST) {
            return this.freeze(instanceId, effect);
        }
        this.unresolved.add(instanceId);
        return null;
    }

    private EffectTransform freeze(UUID instanceId, VisualEffectInstance effect) {
        EffectTransform resolved = this.lastResolved.getOrDefault(
            instanceId, effect.currentTransform()
        );
        this.cache.put(instanceId, resolved);
        return resolved;
    }

    private void reportCycle(UUID instanceId) {
        if (this.reportedCycles.add(instanceId)) {
            LOGGER.warn("Dropping visual effect attachment cycle at instance {}", instanceId);
        }
    }

    /** Client-side parent frame lookups; implemented by the render manager. */
    interface ParentFrames {
        @Nullable
        EffectTransform entity(UUID entityId, float partialTick);

        @Nullable
        EffectTransform subLevel(UUID subLevelId, float partialTick);

        boolean subLevelAvailable();
    }
}
