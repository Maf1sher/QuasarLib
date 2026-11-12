package mafisher.quasar.client.effect;

import mafisher.quasar.api.compat.RenderEnvironment;
import net.minecraft.client.Minecraft;

/** Resolves the client far plane with the shared vanilla fallback. */
final class ClientFarPlane {
    private ClientFarPlane() {
    }

    static double current() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameRenderer == null
            ? RenderEnvironment.VANILLA.farPlane()
            : minecraft.gameRenderer.getDepthFar();
    }
}
