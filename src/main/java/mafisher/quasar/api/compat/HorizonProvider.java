package mafisher.quasar.api.compat;

import net.minecraft.resources.ResourceLocation;

/**
 * Supplies the effective horizon distance and far plane for one frame.
 *
 * <p>Only one horizon provider may be active at a time: Distant Horizons and
 * Voxy are alternatives, never both. Without a provider, effects clip at the
 * vanilla far plane and QuasarLib ships no extended-far-plane fallback.</p>
 */
public interface HorizonProvider {
    /** Stable provider ID, unique across every registered provider. */
    ResourceLocation id();

    /** Higher values win when several providers are registered. */
    int priority();

    /** Effective render distance in blocks, including LOD terrain. */
    double horizonDistance();

    /** Effective far plane in blocks for the current projection. */
    double farPlane();
}
