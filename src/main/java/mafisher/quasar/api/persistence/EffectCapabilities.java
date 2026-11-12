package mafisher.quasar.api.persistence;

import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Capabilities declared by an effect. The engine currently records them for policy and
 * future budget work; consumers may also declare their own capability IDs.
 */
public record EffectCapabilities(boolean destructive, Set<ResourceLocation> custom) {
    public static final EffectCapabilities NONE = new EffectCapabilities(false, Set.of());

    public EffectCapabilities {
        Objects.requireNonNull(custom, "custom");
        custom = Set.copyOf(custom);
    }

    public static EffectCapabilities destructiveCapability() {
        return new EffectCapabilities(true, Set.of());
    }

    public boolean supports(ResourceLocation capability) {
        return this.custom.contains(Objects.requireNonNull(capability, "capability"));
    }

    public EffectCapabilities withCustom(ResourceLocation capability) {
        Objects.requireNonNull(capability, "capability");
        Set<ResourceLocation> updated = new java.util.LinkedHashSet<>(this.custom);
        updated.add(capability);
        return new EffectCapabilities(this.destructive, updated);
    }
}
