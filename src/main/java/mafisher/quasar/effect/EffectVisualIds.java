package mafisher.quasar.effect;

import mafisher.quasar.QuasarLib;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class EffectVisualIds {
    public static final ResourceLocation DEBUG_RUNE_CIRCLE = ResourceLocation.fromNamespaceAndPath(
        QuasarLib.MOD_ID, "debug_rune_circle"
    );
    public static final ResourceLocation DEBUG_WAVE_DISTORTION = ResourceLocation.fromNamespaceAndPath(
        QuasarLib.MOD_ID, "debug_wave_distortion"
    );
    public static final ResourceLocation DEBUG_SCENE_BLUR = ResourceLocation.fromNamespaceAndPath(
        QuasarLib.MOD_ID, "debug_scene_blur"
    );
    public static final ResourceLocation DEBUG_BLOOM = ResourceLocation.fromNamespaceAndPath(
        QuasarLib.MOD_ID, "debug_bloom"
    );
    public static final ResourceLocation DEBUG_BLACK_HOLE = ResourceLocation.fromNamespaceAndPath(
        QuasarLib.MOD_ID, "debug_black_hole"
    );

    private EffectVisualIds() {
    }
}
