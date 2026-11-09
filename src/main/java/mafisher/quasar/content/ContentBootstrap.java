package mafisher.quasar.content;

import java.util.Objects;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Single assembly entry point for content-owned registration. The mod entry point calls this once; engine packages must
 * not import content.
 */
public final class ContentBootstrap {
    /** Mod that currently ships the content. This changes when content moves to its own mod. */
    public static final String MOD_ID = "quasar_lib";
    /** Final content namespace for effect IDs and content assets. */
    public static final String NAMESPACE = "quasar_content";

    private ContentBootstrap() {
    }

    public static void init(IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus, "modEventBus");
        ContentSounds.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(ContentCommands::register);
        // Future content-owned DeferredRegister instances are registered on modEventBus here.
    }
}
