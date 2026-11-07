package mafisher.quasar.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Optional screen-space mask contribution for a visual-effect renderer.
 *
 * <p>For segmented coordinators, cells that return the same settings, mask, and mask render type share one mask pass
 * and one compositor operation. Standalone instances always compose individually.</p>
 */
public interface ScreenEffectRenderer extends VisualEffectRenderer {
    /** Returns the screen-space operation to apply to this effect. */
    default ScreenEffectSettings screenEffectSettings(EffectRenderContext context) {
        Objects.requireNonNull(context, "context");
        return ScreenEffectSettings.DIAGNOSTIC_PREVIEW;
    }

    /** Returns the per-instance compositor inputs for this effect. */
    ScreenEffectMask screenEffectMask(EffectRenderContext context);

    /** Returns a render type that writes coverage to the currently bound mask target. */
    RenderType screenMaskRenderType(EffectRenderContext context);

    /** Renders this effect's mask using the supplied mask-target render type. */
    void renderScreenMask(EffectRenderContext context, RenderType maskRenderType, PoseStack poseStack,
        MultiBufferSource bufferSource);
}
