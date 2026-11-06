package mafisher.quasar.content;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.scale.SegmentedVisualEffectService;
import mafisher.quasar.effect.scale.SegmentedVisualEffectSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Content-owned command for manually validating content effects. */
final class ContentCommands {
    private static final int DEBUG_LIFETIME_TICKS = 30 * 20;
    private static final int SEGMENTED_LIFETIME_TICKS = 60 * 20;
    private static final float DEBUG_SCALE = 5.0F;
    private static final double DEBUG_AUDIENCE_RADIUS = 128.0;
    private static final double SEGMENTED_AUDIENCE_RADIUS = 512.0;
    private static final int DEFAULT_COLUMNS = 4;
    private static final int DEFAULT_ROWS = 4;
    private static final double DEFAULT_SPACING = DEBUG_SCALE * 2.0;

    private ContentCommands() {
    }

    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("quasarcontent")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("rune").executes(context -> startRune(context.getSource())))
                .then(Commands.literal("segmented")
                    .executes(context -> startSegmented(
                        context.getSource(), DEFAULT_COLUMNS, DEFAULT_ROWS, DEFAULT_SPACING
                    ))
                    .then(Commands.argument("columns", IntegerArgumentType.integer(1, 128))
                        .then(Commands.argument("rows", IntegerArgumentType.integer(1, 128))
                            .executes(context -> startSegmented(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "columns"),
                                IntegerArgumentType.getInteger(context, "rows"),
                                DEFAULT_SPACING
                            ))
                            .then(Commands.argument("spacing", DoubleArgumentType.doubleArg(1.0, 512.0))
                                .executes(context -> startSegmented(
                                    context.getSource(),
                                    IntegerArgumentType.getInteger(context, "columns"),
                                    IntegerArgumentType.getInteger(context, "rows"),
                                    DoubleArgumentType.getDouble(context, "spacing")
                                ))))))
                .then(Commands.literal("segmented-stop-all")
                    .executes(context -> stopSegmented(context.getSource())))
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

    private static int startSegmented(CommandSourceStack source, int columns, int rows, double spacing) {
        ServerLevel level = source.getLevel();
        try {
            SegmentedVisualEffectSpec spec = segmentedSpec(
                source.getPosition(),
                source.getRotation().y,
                columns,
                rows,
                spacing,
                level.getRandom().nextLong()
            );
            SegmentedVisualEffectService.start(level, spec);
            source.sendSuccess(
                () -> Component.literal(
                    "Started a " + spec.columns() + "x" + spec.rows() + " segmented content rune ("
                        + spec.segmentCount() + " segments, spacing " + spec.spacing() + ")."
                ),
                false
            );
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int stopSegmented(CommandSourceStack source) {
        int stopped = SegmentedVisualEffectService.stopAll(source.getLevel());
        source.sendSuccess(
            () -> Component.literal("Stopped " + stopped + " segmented effect(s)."),
            false
        );
        return 1;
    }

    static SegmentedVisualEffectSpec segmentedSpec(
        Vec3 position,
        float yaw,
        int columns,
        int rows,
        double spacing,
        long seed
    ) {
        if ((long)columns * rows > SegmentedVisualEffectSpec.MAX_SEGMENTS) {
            throw new IllegalArgumentException("A segmented effect supports at most 128 segments");
        }
        return new SegmentedVisualEffectSpec(
            ContentEffectIds.RUNE_CIRCLE,
            new Vec3(position.x, position.y - 1.4, position.z),
            yaw,
            0.0F,
            0.0F,
            DEBUG_SCALE,
            columns,
            rows,
            spacing,
            SEGMENTED_LIFETIME_TICKS,
            seed,
            EffectAudience.nearby(SEGMENTED_AUDIENCE_RADIUS)
        );
    }
}
