package mafisher.quasar.client.effect.quality;

import mafisher.quasar.api.compat.RenderEnvironment;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.ApiStatus;

/** Resolves the client far plane with the shared vanilla fallback. */
@ApiStatus.Internal
public final class ClientFarPlane {
    private ClientFarPlane() {
    }

    public static double current() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameRenderer == null
            ? RenderEnvironment.VANILLA.farPlane()
            : minecraft.gameRenderer.getDepthFar();
    }
}
