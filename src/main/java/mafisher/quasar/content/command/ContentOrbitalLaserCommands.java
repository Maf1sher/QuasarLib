package mafisher.quasar.content.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Optional;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectStartResult;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.content.orbital.ContentOrbitalLaserGameplayController;
import mafisher.quasar.content.orbital.ContentOrbitalLaserSettings;
import mafisher.quasar.content.orbital.ContentOrbitalLaserSpecs;
import mafisher.quasar.content.orbital.ContentOrbitalLaserTarget;
import mafisher.quasar.content.orbital.ContentOrbitalLaserTarget.TargetColumn;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Content-owned command for the orbital-laser target-lock stage. */
final class ContentOrbitalLaserCommands {
    private static final String STOP_COMMAND = "orbital-laser-stop";

    private ContentOrbitalLaserCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("orbital-laser")
            .executes(context -> start(context, ContentOrbitalLaserSettings.DEFAULT_RADIUS, false))
            .then(Commands.literal("visual")
                .executes(context -> start(
                    context, ContentOrbitalLaserSettings.DEFAULT_RADIUS, true
                ))
                .then(Commands.argument("radius", radiusArgument())
                    .executes(context -> start(context, readRadius(context), true))))
            .then(Commands.argument("radius", radiusArgument())
                .executes(context -> start(context, readRadius(context), false)))
            .then(Commands.literal("stop")
                .executes(ContentOrbitalLaserCommands::stop)))
            .then(Commands.literal(STOP_COMMAND)
                .executes(ContentOrbitalLaserCommands::stop));
    }

    private static IntegerArgumentType radiusArgument() {
        return IntegerArgumentType.integer(
            ContentOrbitalLaserSettings.MIN_RADIUS,
            ContentOrbitalLaserSettings.MAX_RADIUS
        );
    }

    private static int readRadius(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "radius");
    }

    private static int start(
        CommandContext<CommandSourceStack> context,
        int radius,
        boolean visualOnly
    ) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();
        Vec3 origin = player == null
            ? source.getPosition().add(0.0, 1.6, 0.0)
            : player.getEyePosition();
        Vec3 direction = player == null
            ? Vec3.directionFromRotation(source.getRotation().x, source.getRotation().y)
            : player.getLookAngle();
        Optional<TargetColumn> resolved = ContentOrbitalLaserTarget.resolve(
            level, origin, direction, source.getEntity()
        );
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(
                "No loaded surface column under the crosshair within "
                    + (int)ContentOrbitalLaserSettings.MAX_TARGET_DISTANCE + " blocks."
            ));
            return 0;
        }
        TargetColumn target = resolved.get();
        long seed = level.getRandom().nextLong();
        return visualOnly
            ? startPreview(source, target, radius, seed)
            : startSession(source, target, radius, seed, player);
    }

    private static int startPreview(
        CommandSourceStack source,
        TargetColumn target,
        int radius,
        long seed
    ) {
        ServerLevel level = source.getLevel();
        try {
            VisualEffectStartResult result = VisualEffectService.start(
                level, ContentOrbitalLaserSpecs.preview(target, radius, seed)
            );
            if (!result.active()) {
                source.sendFailure(Component.literal(
                    "Could not start the orbital-laser preview (" + result.status() + ")."
                ));
                return 0;
            }
            ContentOrbitalLaserSessions.trackPreview(level, result.handle());
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        source.sendSuccess(
            () -> Component.literal(
                "Started the orbital-laser preview at " + describe(target)
                    + " (radius " + radius + ")."
            ),
            false
        );
        return 1;
    }

    private static int startSession(
        CommandSourceStack source,
        TargetColumn target,
        int radius,
        long seed,
        ServerPlayer player
    ) {
        ServerLevel level = source.getLevel();
        GameplayEffectSpec spec = ContentOrbitalLaserSpecs.gameplay(
            player == null ? null : player.getUUID(), target, radius, seed
        );
        GameplayEffectHandle handle;
        try {
            handle = GameplayEffectService.start(
                level,
                spec,
                new ContentOrbitalLaserGameplayController(),
                EffectPersistenceOptions.DEFAULT.withCapabilities(
                    EffectCapabilities.destructiveCapability()
                )
            );
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        ContentOrbitalLaserSessions.trackSession(level, handle);
        source.sendSuccess(
            () -> Component.literal(
                "Locked the orbital laser on " + describe(target) + " (radius " + radius
                    + ", session " + handle.sessionId() + ")."
            ),
            false
        );
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int sessions = ContentOrbitalLaserSessions.stopSessions(
            level,
            (stoppedLevel, handle) -> GameplayEffectService.stop(
                stoppedLevel, handle, GameplayEffectEndReason.CANCELLED
            )
        );
        int previews = ContentOrbitalLaserSessions.stopPreviews(
            level,
            VisualEffectService::stop
        );
        source.sendSuccess(
            () -> Component.literal(
                "Stopped " + sessions + " orbital laser session(s) and "
                    + previews + " preview(s)."
            ),
            false
        );
        return 1;
    }

    private static String describe(TargetColumn target) {
        return target.x() + ", " + target.firstFreeY() + ", " + target.z();
    }
}
