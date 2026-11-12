package mafisher.quasar.content;

import mafisher.quasar.api.client.ScreenEffectOperation;
import net.minecraft.resources.ResourceLocation;

/** Stable handles for operations owned by the demo content. */
public final class ContentScreenOperations {
    public static final ScreenEffectOperation RUNE_GLOW = ScreenEffectOperation.create(
        ResourceLocation.fromNamespaceAndPath(ContentBootstrap.NAMESPACE, "rune_glow")
    );
    public static final ScreenEffectOperation BLACK_HOLE = ScreenEffectOperation.create(
        ResourceLocation.fromNamespaceAndPath(ContentBootstrap.NAMESPACE, "black_hole")
    );

    private ContentScreenOperations() {
    }
}
