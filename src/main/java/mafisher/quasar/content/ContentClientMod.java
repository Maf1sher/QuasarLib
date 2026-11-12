package mafisher.quasar.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only content registration. Loaded only on the physical client, so engine client classes stay untouched. */
@Mod(value = ContentBootstrap.MOD_ID, dist = Dist.CLIENT)
public final class ContentClientMod {
    public ContentClientMod(IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus, "modEventBus");
        modEventBus.addListener(ContentClientMod::registerShaders);
        modEventBus.addListener(ContentClientEffects::registerEntityRenderers);
        NeoForge.EVENT_BUS.addListener(ContentClientEffects::registerOperations);
        NeoForge.EVENT_BUS.addListener(ContentClientEffects::registerRenderers);
        NeoForge.EVENT_BUS.addListener(ContentClientEffects::onLevelUnload);
    }

    private static void registerShaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event) {
        try {
            ContentClientShaders.registerShaders(event);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not register content shaders", exception);
        }
    }
}
