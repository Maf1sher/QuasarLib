package mafisher.quasar.api.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

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

    /**
     * Returns the projection that produced {@link #depthTarget()} with the
     * current frame's camera view, or {@code null} when this provider cannot
     * guarantee the pairing.
     *
     * <p>The projection must use the same camera view as the frame and the
     * standard NDC depth convention. Returning {@code null} disables
     * provider-depth occlusion for this provider; it never disables screen
     * composition. The engine copies the returned matrix, so providers may
     * reuse an internal instance.</p>
     *
     * @param frameProjection the current frame camera projection; providers that
     *     reuse it may return this value (the engine copies it)
     */
    default @Nullable Matrix4f depthProjection(Matrix4f frameProjection) {
        return null;
    }
}
