package mafisher.quasar.client.effect;

import java.io.IOException;
import mafisher.quasar.QuasarLib;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.ApiStatus;

/** Client mod-bus hooks for registry setup, shaders, and resource reloads. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
@ApiStatus.Internal
public final class ClientEffectModEvents {
    private ClientEffectModEvents() {
    }

    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        ClientEffectFrameRuntime.initialize();
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager ->
            ClientEffectFrameRuntime.onResourceReload(resourceManager, Minecraft.getInstance()::execute));
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        EffectShaders.registerAll(event);
    }
}
