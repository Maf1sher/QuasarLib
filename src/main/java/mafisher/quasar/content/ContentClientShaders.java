package mafisher.quasar.content;

import java.io.IOException;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Client-only registration of content-owned shaders. */
@EventBusSubscriber(modid = ContentBootstrap.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ContentClientShaders {
    private ContentClientShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        ContentShaders.RUNE_CIRCLE.register(event);
        ContentShaders.RUNE_MASK.register(event);
        ContentShaders.RUNE_GLOW.register(event);
        ContentShaders.BLACK_HOLE.register(event);
        ContentShaders.BLACK_HOLE_MASK.register(event);
    }
}
