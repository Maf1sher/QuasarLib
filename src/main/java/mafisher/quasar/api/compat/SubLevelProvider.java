package mafisher.quasar.api.compat;

import java.util.Optional;
import java.util.UUID;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.effect.EffectTransform;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional bridge to a sub-level physics mod.
 *
 * <p>QuasarLib owns no sub-level simulation. A present provider supplies the current world pose
 * of a sub-level so attachments and queries can follow it; when none is registered, attachments
 * use their authored transform and keep working. The Sable adapter is added with the
 * compatibility step; this contract stays free of Sable classes.</p>
 */
public interface SubLevelProvider {
    ResourceLocation VANILLA_ID = ResourceLocation.fromNamespaceAndPath(
        QuasarLib.MOD_ID, "vanilla_sub_level"
    );

    /** Stable provider ID, unique across all compat provider kinds. */
    ResourceLocation id();

    /** Whether the sub-level mod is loaded and can supply poses. */
    boolean available();

    /** Current world pose of one sub-level, or empty when it is not loaded or unknown. */
    Optional<EffectTransform> pose(UUID subLevelId);

    /** The fallback used when no sub-level mod is present. */
    static SubLevelProvider unavailable() {
        return new SubLevelProvider() {
            @Override
            public ResourceLocation id() {
                return VANILLA_ID;
            }

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public Optional<EffectTransform> pose(UUID subLevelId) {
                return Optional.empty();
            }
        };
    }
}
