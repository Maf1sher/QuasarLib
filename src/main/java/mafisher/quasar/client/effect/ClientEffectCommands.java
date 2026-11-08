package mafisher.quasar.client.effect;

import com.mojang.brigadier.CommandDispatcher;
import mafisher.quasar.QuasarLib;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Client-only diagnostic commands for screen composition. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientEffectCommands {
    private ClientEffectCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("quasarclient")
                .then(
                    Commands.literal("compositor-debug")
                        .executes(context -> requestCompositorDiagnostics(context.getSource()))
                )
        );
    }

    private static int requestCompositorDiagnostics(CommandSourceStack source) {
        if (!ClientEffectFrameRuntime.requestCompositorDiagnostics()) {
            source.sendFailure(Component.literal("The screen compositor is not available yet."));
            return 0;
        }
        source.sendSuccess(
            () -> Component.literal(
                "Armed one-shot screen compositor diagnostics; show a screen effect to capture a frame."),
            false
        );
        return 1;
    }
}
