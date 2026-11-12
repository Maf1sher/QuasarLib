package mafisher.quasar.content;

import net.minecraft.resources.ResourceLocation;

/** Stable IDs for content-owned effects. These are not engine debug fixtures. */
public final class ContentEffectIds {
    public static final ResourceLocation RUNE_CIRCLE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "rune_circle"
    );
    public static final ResourceLocation WAVE_DISTORTION = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "wave_distortion"
    );
    public static final ResourceLocation SCENE_BLUR = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "scene_blur"
    );
    public static final ResourceLocation BLOOM = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "bloom"
    );
    public static final ResourceLocation BLACK_HOLE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "black_hole"
    );
    public static final ResourceLocation BLACK_HOLE_MESH = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "black_hole_mesh"
    );

    private ContentEffectIds() {
    }
}
