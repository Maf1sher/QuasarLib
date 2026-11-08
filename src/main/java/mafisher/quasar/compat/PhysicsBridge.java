package mafisher.quasar.compat;

import mafisher.quasar.QuasarLib;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional bridge to a rigid-body or sub-level physics mod.
 *
 * <p>QuasarLib owns no rigid-body simulation. When a bridge is present it owns
 * bodies and forces; when absent, {@code EffectForceField} applies to vanilla
 * entities only. Force routing through the bridge is added with the Sable and
 * Create Aeronautics adapters; this type only declares availability.</p>
 */
public interface PhysicsBridge {
    ResourceLocation VANILLA_ID = ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "vanilla");

    /** Stable bridge ID, unique across registered providers. */
    ResourceLocation id();

    /** Whether the physics mod is loaded and can receive forces. */
    boolean available();

    /** The fallback bridge used when no physics mod is present. */
    static PhysicsBridge vanilla() {
        return new PhysicsBridge() {
            @Override
            public ResourceLocation id() {
                return VANILLA_ID;
            }

            @Override
            public boolean available() {
                return false;
            }
        };
    }
}
