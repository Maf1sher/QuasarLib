package mafisher.quasar.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jetbrains.annotations.ApiStatus;

/** Applies effect Euler rotations in the documented yaw, pitch, roll axis order. */
@ApiStatus.Internal
public final class EffectRenderTransform {
    private EffectRenderTransform() {
    }

    public static void applyRotation(PoseStack poseStack, float yaw, float pitch, float roll) {
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
    }
}
