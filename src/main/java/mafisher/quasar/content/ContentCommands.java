package mafisher.quasar.content;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import mafisher.quasar.effect.scale.SegmentedVisualEffectService;
import mafisher.quasar.effect.scale.SegmentedVisualEffectSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Content-owned command for manually validating content effects. */
final class ContentCommands {
    private static final int DEBUG_LIFETIME_TICKS = 30 * 20;
    private static final int SEGMENTED_LIFETIME_TICKS = 60 * 20;
    private static final int BLACK_HOLE_LIFETIME_TICKS = 60 * 20;
    private static final float DEBUG_SCALE = 5.0F;
    private static final double DEBUG_AUDIENCE_RADIUS = 128.0;
    private static final double SEGMENTED_AUDIENCE_RADIUS = 512.0;
    private static final double BLACK_HOLE_DEFAULT_DISTANCE = 160.0;
    private static final float BLACK_HOLE_DEFAULT_SCALE = 6.0F;
    private static final float BLACK_HOLE_DISK_PITCH = 8.0F;
    private static final double BLACK_HOLE_MIN_HEIGHT_OFFSET = 24.0;
    private static final int DEFAULT_COLUMNS = 4;
    private static final int DEFAULT_ROWS = 4;
    private static final double DEFAULT_SPACING = DEBUG_SCALE * 2.0;
    private static final SoundEvent RUNE_CHIME = SoundEvent.createVariableRangeEvent(
        ResourceLocation.withDefaultNamespace("block.amethyst_block.chime")
    );

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
                .then(Commands.literal("blackhole")
                    .executes(context -> startBlackHole(
                        context.getSource(),
                        BLACK_HOLE_DEFAULT_DISTANCE,
                        BLACK_HOLE_DEFAULT_SCALE,
                        ContentBlackHoleGeometry.VARIANT_AMBER
                    ))
                    .then(blackHoleVariant("gold", ContentBlackHoleGeometry.VARIANT_GOLD))
                    .then(blackHoleVariant("neon", ContentBlackHoleGeometry.VARIANT_NEON))
                    .then(Commands.argument("distance", DoubleArgumentType.doubleArg(48.0, 512.0))
                        .executes(context -> startBlackHole(
                            context.getSource(),
                            DoubleArgumentType.getDouble(context, "distance"),
                            BLACK_HOLE_DEFAULT_SCALE,
                            ContentBlackHoleGeometry.VARIANT_AMBER
                        ))
                        .then(Commands.argument("scale", DoubleArgumentType.doubleArg(2.0, 64.0))
                            .executes(context -> startBlackHole(
                                context.getSource(),
                                DoubleArgumentType.getDouble(context, "distance"),
                                (float)DoubleArgumentType.getDouble(context, "scale"),
                                ContentBlackHoleGeometry.VARIANT_AMBER
                            )))))
        );
    }

    private static int startRune(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Vec3 effectPosition = new Vec3(position.x, position.y - 1.4, position.z);
        EffectAudioSpec audio = EffectAudioSpec.builder(DEBUG_LIFETIME_TICKS)
            .cue(
                0,
                effectPosition,
                RUNE_CHIME,
                SoundSource.MASTER,
                1.0F,
                1.0F,
                EffectAudioAttenuation.distance(64.0F)
            )
            .cue(
                20,
                effectPosition.add(0.0, 2.0, 0.0),
                RUNE_CHIME,
                SoundSource.MASTER,
                0.8F,
                1.2F,
                EffectAudioAttenuation.directional(
                    new Vec3(0.0, -1.0, 0.0),
                    45.0F,
                    90.0F,
                    0.25F,
                    64.0F
                )
            )
            .build();
        VisualEffectService.start(level, new VisualEffectSpec(
            ContentEffectIds.RUNE_CIRCLE,
            effectPosition,
            source.getRotation().y,
            0.0F,
            0.0F,
            DEBUG_SCALE,
            DEBUG_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(DEBUG_AUDIENCE_RADIUS),
            audio
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

    private static LiteralArgumentBuilder<CommandSourceStack> blackHoleVariant(String name, int variant) {
        return Commands.literal(name)
            .executes(context -> startBlackHole(
                context.getSource(),
                BLACK_HOLE_DEFAULT_DISTANCE,
                BLACK_HOLE_DEFAULT_SCALE,
                variant
            ))
            .then(Commands.argument("distance", DoubleArgumentType.doubleArg(48.0, 512.0))
                .executes(context -> startBlackHole(
                    context.getSource(),
                    DoubleArgumentType.getDouble(context, "distance"),
                    BLACK_HOLE_DEFAULT_SCALE,
                    variant
                ))
                .then(Commands.argument("scale", DoubleArgumentType.doubleArg(2.0, 64.0))
                    .executes(context -> startBlackHole(
                        context.getSource(),
                        DoubleArgumentType.getDouble(context, "distance"),
                        (float)DoubleArgumentType.getDouble(context, "scale"),
                        variant
                    ))));
    }

    private static int startBlackHole(CommandSourceStack source, double distance, float scale, int variant) {
        ServerLevel level = source.getLevel();
        VisualEffectSpec spec = blackHoleSpec(
            source.getPosition(),
            source.getRotation().y,
            source.getRotation().x,
            distance,
            scale,
            variant,
            level.getRandom().nextLong()
        );
        VisualEffectService.start(level, spec);
        source.sendSuccess(
            () -> Component.literal(
                "Started the content black hole (" + blackHoleVariantName(variant) + ") at "
                    + (int)spec.position().x + ", " + (int)spec.position().y + ", "
                    + (int)spec.position().z + " (horizon radius " + spec.scale() + ")."
            ),
            false
        );
        return 1;
    }

    static VisualEffectSpec blackHoleSpec(
        Vec3 origin,
        float viewYaw,
        float viewPitch,
        double distance,
        float scale,
        int variant,
        long seed
    ) {
        if (!Double.isFinite(distance) || distance < 1.0) {
            throw new IllegalArgumentException("Black-hole distance must be finite and positive");
        }
        if (!Float.isFinite(scale) || scale <= 0.0F) {
            throw new IllegalArgumentException("Black-hole scale must be finite and positive");
        }
        Vec3 look = Vec3.directionFromRotation(viewPitch, viewYaw);
        Vec3 center = origin.add(look.scale(distance));
        center = new Vec3(
            center.x,
            Math.max(center.y, origin.y + BLACK_HOLE_MIN_HEIGHT_OFFSET),
            center.z
        );
        EffectAudioSpec audio = ContentBlackHoleAudio.plan(center, BLACK_HOLE_LIFETIME_TICKS);
        return new VisualEffectSpec(
            ContentEffectIds.BLACK_HOLE,
            center,
            viewYaw,
            BLACK_HOLE_DISK_PITCH,
            0.0F,
            scale,
            BLACK_HOLE_LIFETIME_TICKS,
            ContentBlackHoleGeometry.withVariant(seed, variant),
            EffectAudience.dimensionWide(),
            audio
        );
    }

    private static String blackHoleVariantName(int variant) {
        return switch (variant) {
            case ContentBlackHoleGeometry.VARIANT_GOLD -> "gold";
            case ContentBlackHoleGeometry.VARIANT_NEON -> "neon";
            default -> "amber";
        };
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
