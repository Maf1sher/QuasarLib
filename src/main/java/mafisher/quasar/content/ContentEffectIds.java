package mafisher.quasar.content;

import net.minecraft.resources.ResourceLocation;

/** Stable IDs for content-owned effects. These are not engine debug fixtures. */
public final class ContentEffectIds {
    public static final ResourceLocation RUNE_CIRCLE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "rune_circle"
    );
    public static final ResourceLocation BLACK_HOLE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "black_hole"
    );

    private ContentEffectIds() {
    }
}
