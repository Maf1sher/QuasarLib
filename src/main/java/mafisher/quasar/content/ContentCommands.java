package mafisher.quasar.content;

import com.mojang.brigadier.CommandDispatcher;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.VisualEffectSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Content-owned command for manually validating content effects. */
final class ContentCommands {
    private static final int DEBUG_LIFETIME_TICKS = 30 * 20;
    private static final float DEBUG_SCALE = 5.0F;
    private static final double DEBUG_AUDIENCE_RADIUS = 128.0;

    private ContentCommands() {
    }

    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("quasarcontent")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("rune").executes(context -> startRune(context.getSource())))
        );
    }

    private static int startRune(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        VisualEffectService.start(level, new VisualEffectSpec(
            ContentEffectIds.RUNE_CIRCLE,
            new Vec3(position.x, position.y - 1.4, position.z),
            source.getRotation().y,
            0.0F,
            0.0F,
            DEBUG_SCALE,
            DEBUG_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(DEBUG_AUDIENCE_RADIUS)
        ));
        source.sendSuccess(() -> Component.literal("Started the content rune effect."), false);
        return 1;
    }
}
