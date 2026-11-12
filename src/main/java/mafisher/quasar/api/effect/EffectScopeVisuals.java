package mafisher.quasar.api.effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;

/** Visual handle ledger owned by one effect scope. */
final class EffectScopeVisuals {
    private final ServerLevel level;
    private final EffectScopeResources resources;
    private final Supplier<EffectRemovalPolicy> removalPolicy;
    private final Map<UUID, VisualEffectHandle> activeVisuals = new HashMap<>();

    EffectScopeVisuals(
        ServerLevel level,
        EffectScopeResources resources,
        Supplier<EffectRemovalPolicy> removalPolicy
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.removalPolicy = Objects.requireNonNull(removalPolicy, "removalPolicy");
    }

    /** Tracks an active visual; returns false when it is already inactive or tracked. */
    boolean track(UUID instanceId, VisualEffectHandle handle, boolean cancelWithScope) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(handle, "handle");
        if (this.activeVisuals.containsKey(instanceId)
            || !VisualEffectService.isActive(this.level, handle)) {
            return false;
        }
        this.activeVisuals.put(instanceId, handle);
        if (cancelWithScope) {
            this.resources.track(instanceId, () -> this.stop(instanceId, this.removalPolicy.get()));
        }
        return true;
    }

    void prune() {
        for (UUID instanceId : new ArrayList<>(this.activeVisuals.keySet())) {
            if (!VisualEffectService.isActive(this.level, new VisualEffectHandle(instanceId))) {
                this.activeVisuals.remove(instanceId);
                this.resources.untrack(instanceId);
            }
        }
    }

    void stop(UUID instanceId, EffectRemovalPolicy policy) {
        UUID id = Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(policy, "policy");
        VisualEffectHandle handle = this.activeVisuals.remove(id);
        this.resources.untrack(id);
        if (handle != null) {
            VisualEffectService.stop(this.level, handle, policy);
        }
    }

    void update(UUID instanceId, EffectTransform transform) {
        this.prune();
        VisualEffectHandle handle = this.activeVisuals.get(instanceId);
        if (handle != null) {
            VisualEffectService.update(this.level, handle, transform);
        }
    }

    int size() {
        return this.activeVisuals.size();
    }

    void clear() {
        this.activeVisuals.clear();
    }
}
