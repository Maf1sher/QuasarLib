package mafisher.quasar.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Client-only renderer for one registered transient visual effect type. */
public interface VisualEffectRenderer {
    void render(VisualEffectInstance effect, float ageTicks, float progress, PoseStack poseStack,
        MultiBufferSource bufferSource);

    RenderType renderType();
}
