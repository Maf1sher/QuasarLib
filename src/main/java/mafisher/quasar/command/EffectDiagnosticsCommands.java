package mafisher.quasar.command;

import com.mojang.brigadier.CommandDispatcher;
import java.util.UUID;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.cinematic.CinematicEffectHandle;
import mafisher.quasar.api.cinematic.CinematicEffectService;
import mafisher.quasar.api.cinematic.CinematicEffectStartResult;
import mafisher.quasar.api.cinematic.CinematicTimeline;
import mafisher.quasar.api.effect.EffectBudgetReport;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.cinematic.CinematicTimelineRegistry;
import mafisher.quasar.effect.persistence.EffectRecoveryReport;
import mafisher.quasar.effect.persistence.EffectRestoreService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.ApiStatus;

/** Read-only engine diagnostics and generic cinematic control. Demo commands live in content. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
@ApiStatus.Internal
public final class EffectDiagnosticsCommands {
    private EffectDiagnosticsCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("quasarlib")
                .then(Commands.literal("cinematic")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("play")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                            .suggests((context, builder) -> {
                                for (ResourceLocation id : CinematicTimelineRegistry.ids()) {
                                    builder.suggest(id.toString());
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> playCinematic(
                                context.getSource(),
                                ResourceLocationArgument.getId(context, "id")
                            ))))
                    .then(Commands.literal("list")
                        .executes(context -> listCinematics(context.getSource())))
                    .then(Commands.literal("stop")
                        .then(Commands.argument("session", UuidArgument.uuid())
                            .executes(context -> stopCinematic(
                                context.getSource(),
                                UuidArgument.getUuid(context, "session")
                            )))))
                .then(Commands.literal("persistence")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("report")
                        .executes(context -> persistenceReport(context.getSource()))))
                .then(Commands.literal("budget")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> budgetReport(context.getSource())))
        );
    }

    private static int playCinematic(CommandSourceStack source, ResourceLocation id) {
        CinematicTimeline timeline = CinematicTimelineRegistry.find(id).orElse(null);
        if (timeline == null) {
            source.sendFailure(Component.literal("Unknown cinematic: " + id + ". Run /quasarlib cinematic list."));
            return 0;
        }
        try {
            CinematicEffectStartResult result = CinematicEffectService.start(
                source.getLevel(),
                timeline,
                source.getPosition()
            );
            if (!result.active()) {
                source.sendFailure(Component.literal("The cinematic was culled by the session budget."));
                return 0;
            }
            CinematicEffectHandle handle = result.handle();
            source.sendSuccess(
                () -> Component.literal(
                    "Started cinematic " + id + " (" + timeline.cueCount() + " cues, "
                        + timeline.durationTicks() + " ticks): " + handle.sessionId() + "."
                ),
                false
            );
            return 1;
        } catch (IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int listCinematics(CommandSourceStack source) {
        if (CinematicTimelineRegistry.size() == 0) {
            source.sendSuccess(
                () -> Component.literal("No datapack cinematics are loaded."),
                false
            );
            return 1;
        }
        source.sendSuccess(
            () -> Component.literal(
                "Loaded " + CinematicTimelineRegistry.size() + " cinematic(s): "
                    + CinematicTimelineRegistry.ids()
            ),
            false
        );
        return 1;
    }

    private static int stopCinematic(CommandSourceStack source, UUID sessionId) {
        CinematicEffectService.stop(
            source.getLevel(),
            new CinematicEffectHandle(sessionId)
        );
        source.sendSuccess(
            () -> Component.literal("Stopped cinematic session " + sessionId + "."),
            false
        );
        return 1;
    }

    private static int budgetReport(CommandSourceStack source) {
        EffectBudgetReport report = EffectBudgetService.report(source.getLevel());
        source.sendSuccess(
            () -> Component.literal(
                "Budget: visuals " + report.standaloneVisuals() + "/" + report.standaloneVisualBudget()
                    + ", segments " + report.segments() + "/" + report.segmentBudget()
                    + ", gameplay " + report.gameplaySessions() + "/" + report.gameplaySessionBudget()
                    + " (pending " + report.pendingGameplaySessions() + "/" + report.gameplayPendingBudget() + ")"
                    + ", cinematics " + report.cinematicSessions() + "/" + report.cinematicSessionBudget()
                    + ", audio " + report.audioPlans() + "/" + report.audioPlanBudget()
                    + ", over budget " + report.overBudget() + "."
            ),
            false
        );
        source.sendSuccess(
            () -> Component.literal(
                "Soft degradation: culled " + report.culledEffects() + ", deferred "
                    + report.deferredStarts() + ", dropped spawns " + report.droppedEntitySpawns()
                    + ", audio evicted " + report.audioEvictions()
                    + ", audio dropped " + report.audioDrops() + "."
            ),
            false
        );
        return 1;
    }

    private static int persistenceReport(CommandSourceStack source) {
        EffectRecoveryReport report = EffectRestoreService.report(source.getLevel());
        source.sendSuccess(
            () -> Component.literal(
                "Persistence: restored " + report.restored() + ", expired " + report.expired()
                    + ", quarantined " + report.quarantined() + ", deferred " + report.unsupported()
                    + ", rollbacks " + report.rollbacks() + "."
            ),
            false
        );
        for (String message : report.messages()) {
            source.sendSuccess(() -> Component.literal("  " + message), false);
        }
        return 1;
    }
}
