package mafisher.quasar.api.client.screen;

import java.util.Objects;
import mafisher.quasar.QuasarLib;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable handle for one screen-space compositor operation.
 *
 * <p>The engine registers its built-in operations at startup. Consumer mods
 * create their own handle with {@link #create(ResourceLocation)} and register a
 * renderer through {@link RegisterScreenEffectOperationsEvent}.</p>
 */
public final class ScreenEffectOperation {
    public static final ScreenEffectOperation DIAGNOSTIC_MASK_PREVIEW =
        create("diagnostic_mask_preview");
    public static final ScreenEffectOperation MASK_SCOPED_WAVE_DISTORTION =
        create("wave_distortion");
    public static final ScreenEffectOperation MASK_SCOPED_SCENE_BLUR =
        create("scene_blur");
    public static final ScreenEffectOperation MASK_SCOPED_BLOOM =
        create("bloom");
    public static final ScreenEffectOperation MASK_SCOPED_BLACK_HOLE =
        create("black_hole");

    private final ResourceLocation id;

    private ScreenEffectOperation(ResourceLocation id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    /** Creates a custom operation handle. */
    public static ScreenEffectOperation create(ResourceLocation id) {
        return new ScreenEffectOperation(id);
    }

    private static ScreenEffectOperation create(String path) {
        return create(ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, path));
    }

    /** Returns the operation ID used by settings and the registry. */
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ScreenEffectOperation operation && this.id.equals(operation.id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return this.id.toString();
    }
}
