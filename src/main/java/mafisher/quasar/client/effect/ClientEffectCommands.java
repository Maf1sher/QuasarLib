package mafisher.quasar.client.effect;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Locale;
import java.util.stream.Collectors;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.compat.RenderEnvironment;
import mafisher.quasar.client.effect.quality.ClientFarPlane;
import mafisher.quasar.client.effect.render.ShaderPreflight;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client-only diagnostic and authoring commands for composition, compatibility, and reloads. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
@ApiStatus.Internal
public final class ClientEffectCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientEffectCommands.class);

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
        dispatcher.register(
            Commands.literal("quasarlib")
                .then(
                    Commands.literal("reload")
                        .executes(context -> reloadShaders(context.getSource()))
                )
        );
    }

    private static int reloadShaders(CommandSourceStack source) {
        ShaderPreflight.Report report;
        try {
            report = ShaderPreflight.check(Minecraft.getInstance().getResourceManager());
        } catch (RuntimeException exception) {
            LOGGER.error("Shader reload preflight failed", exception);
            source.sendFailure(Component.literal(
                "Shader reload preflight failed: " + exception.getMessage()));
            return 0;
        }
        if (!report.valid()) {
            for (String line : report.chatLines()) {
                source.sendFailure(Component.literal(line));
            }
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
            "Validated " + report.checked() + " shader program(s) in "
                + String.format(Locale.ROOT, "%.1f", report.elapsedMillis())
                + " ms; reloading client resources..."
        ), false);
        Minecraft minecraft = Minecraft.getInstance();
        try {
            minecraft.reloadResourcePacks().whenComplete((unused, throwable) ->
                minecraft.execute(() -> reportReload(minecraft, throwable)));
        } catch (RuntimeException exception) {
            LOGGER.error("Could not start the client resource reload", exception);
            source.sendFailure(Component.literal(
                "Could not start the client resource reload: " + exception.getMessage()));
            return 0;
        }
        return 1;
    }

    private static void reportReload(Minecraft minecraft, Throwable throwable) {
        if (throwable != null) {
            LOGGER.error("Client resource reload failed after shader preflight", throwable);
            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(
                    "Client resource reload failed; the last valid shaders remain. See the log."));
            }
            return;
        }
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal(
                "Client resources reloaded; shader programs and renderer definitions are current."));
        } else {
            LOGGER.info("Client resources reloaded after shader preflight");
        }
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
        RenderEnvironment environment = CompatRegistry.environment(ClientFarPlane.current());
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
            + " depth-occlusion=" + ClientEffectFrameRuntime.depthOcclusionStatus()
            + " horizon=" + horizon
            + " shaderpack=" + environment.shaderPackState()
            + " physics=" + physics
            + " protection=" + protection;
        source.sendSuccess(() -> Component.literal(report), false);
        return 1;
    }
}
