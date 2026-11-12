package mafisher.quasar.content;

import java.util.Objects;
import mafisher.quasar.content.command.ContentCommandSessions;
import mafisher.quasar.content.command.ContentCommands;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Content-owned mod entry point. The engine never imports this package; content uses only public engine contracts and
 * its own registrations. Moving the demo to another mod changes this entry point and the metadata, not engine code.
 */
@Mod(ContentBootstrap.MOD_ID)
public final class ContentBootstrap {
    /** Content mod id. It is also the namespace for content-owned effects and assets. */
    public static final String MOD_ID = "quasar_content";
    /** Content namespace for effect IDs, sounds, shaders, and datapack assets. */
    public static final String NAMESPACE = MOD_ID;

    public ContentBootstrap(IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus, "modEventBus");
        ContentSounds.register(modEventBus);
        ContentEffectEntities.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(ContentGameplayEffects::registerTypes);
        NeoForge.EVENT_BUS.addListener(ContentCommands::register);
        NeoForge.EVENT_BUS.addListener(ContentBootstrap::onLevelUnload);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ContentCommandSessions.clear(level);
        }
    }
}
