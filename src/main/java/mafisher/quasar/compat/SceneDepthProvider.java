package mafisher.quasar.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.resources.ResourceLocation;

/**
 * Supplies the scene depth that effect masks and screen composition should read.
 *
 * <p>Providers are optional and version-pinned by their adapter. QuasarLib falls
 * back to the vanilla main target when no provider is active, so a missing
 * provider degrades depth accuracy but never fails an effect.</p>
 */
public interface SceneDepthProvider {
    /** Stable provider ID, unique across every registered provider. */
    ResourceLocation id();

    /** Higher values win when several providers are registered. */
    int priority();

    /** Where this depth comes from. */
    SceneDepthSource source();

    /**
     * Returns the depth-bearing target for the current frame, or {@code null}
     * when this provider cannot supply depth right now (for example during a
     * shaderpack shadow pass).
     */
    RenderTarget depthTarget();
}
