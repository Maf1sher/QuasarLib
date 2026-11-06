package mafisher.quasar.content;

import mafisher.quasar.client.effect.ScreenEffectOperation;
import net.minecraft.resources.ResourceLocation;

/** Stable handles for operations owned by the demo content. */
public final class ContentScreenOperations {
    public static final ScreenEffectOperation RUNE_GLOW = ScreenEffectOperation.create(
        ResourceLocation.fromNamespaceAndPath(ContentBootstrap.NAMESPACE, "rune_glow")
    );

    private ContentScreenOperations() {
    }
}
