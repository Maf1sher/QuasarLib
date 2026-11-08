package mafisher.quasar.client.effect;

import com.mojang.brigadier.CommandDispatcher;
import java.util.stream.Collectors;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.compat.CompatRegistry;
import mafisher.quasar.compat.RenderEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Client-only diagnostic commands for screen composition and compatibility. */
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
                .then(
                    Commands.literal("compat")
                        .executes(context -> reportCompat(context.getSource()))
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

    private static int reportCompat(CommandSourceStack source) {
        Minecraft minecraft = Minecraft.getInstance();
        double farPlane = minecraft.gameRenderer == null ? 256.0 : minecraft.gameRenderer.getDepthFar();
        RenderEnvironment environment = CompatRegistry.environment(farPlane);
        String providers = environment.activeProviders().isEmpty()
            ? "none"
            : environment.activeProviders().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        String depth = environment.hasDepthProvider()
            ? environment.depthSource().toString()
            : environment.depthSource() + " (vanilla fallback)";
        String horizon = String.format(
            "%.1f/%.1f (%s)", environment.horizonDistance(), environment.farPlane(),
            environment.hasHorizonProvider() ? "provider" : "vanilla far plane");
        String physics = environment.physicsAvailable()
            ? "available " + CompatRegistry.physics().id()
            : "absent (vanilla entities only)";
        String protection = environment.protectionAvailable()
            ? "available " + CompatRegistry.protection().id()
            : "absent (registered block protections only)";
        String report = "Compat providers: " + providers
            + "\ndepth=" + depth
            + " horizon=" + horizon
            + " shaderpack=" + environment.shaderPackState()
            + " physics=" + physics
            + " protection=" + protection;
        source.sendSuccess(() -> Component.literal(report), false);
        return 1;
    }
}
