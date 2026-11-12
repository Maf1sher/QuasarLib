package mafisher.quasar.api.compat;

import net.minecraft.resources.ResourceLocation;

/**
 * Reports whether an external shaderpack pipeline owns the current frame.
 *
 * <p>Screen composition is skipped during shadow passes and is best effort with
 * an active shaderpack; world effects remain visible if composition is
 * disabled.</p>
 */
public interface ShaderPackProvider {
    /** Stable provider ID, unique across every registered provider. */
    ResourceLocation id();

    /** Higher values win when several providers are registered. */
    int priority();

    /** Current shaderpack state for this frame. */
    ShaderPackState state();
}
