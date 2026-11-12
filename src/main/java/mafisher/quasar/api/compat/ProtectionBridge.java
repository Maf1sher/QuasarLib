package mafisher.quasar.api.compat;

import java.util.Objects;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.block.BlockProtectionContext;
import mafisher.quasar.effect.block.BlockProtectionService;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional bridge to a land-claim mod. QuasarLib never implements claims; a
 * bridge only asks the owning mod before a block change.
 *
 * <p>A bridge that throws denies the change and is logged once by the engine.
 * Without a bridge, the engine falls back to the registered
 * {@link mafisher.quasar.api.block.BlockProtection} callbacks.</p>
 */
public interface ProtectionBridge {
    ResourceLocation VANILLA_ID = ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "vanilla");

    /** Stable bridge ID, unique across registered providers. */
    ResourceLocation id();

    /** Whether a claim mod is loaded and answering queries. */
    boolean available();

    /** Returns {@code true} when the change may be applied. */
    boolean allows(BlockProtectionContext context);

    /** The fallback bridge backed by registered block protections. */
    static ProtectionBridge vanilla() {
        return new ProtectionBridge() {
            @Override
            public ResourceLocation id() {
                return VANILLA_ID;
            }

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public boolean allows(BlockProtectionContext context) {
                Objects.requireNonNull(context, "context");
                return BlockProtectionService.allows(context);
            }
        };
    }
}
