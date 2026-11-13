package mafisher.quasar.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.api.scale.SegmentedVisualEffectHandle;
import mafisher.quasar.api.scale.SegmentedVisualEffectService;
import mafisher.quasar.api.scale.SegmentedVisualEffectSpec;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.content.blackhole.ContentBlackHoleAudio;
import mafisher.quasar.content.blackhole.ContentBlackHoleGameplayController;
import mafisher.quasar.content.blackhole.ContentBlackHoleGameplaySpec;
import mafisher.quasar.content.blackhole.ContentBlackHoleGeometry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Content-owned command for manually validating content effects. */
public final class ContentCommands {
    private static final int DEBUG_LIFETIME_TICKS = 30 * 20;
    private static final int SEGMENTED_LIFETIME_TICKS = 60 * 20;
    private static final int BLACK_HOLE_LIFETIME_TICKS = 60 * 20;
    private static final float DEBUG_SCALE = 5.0F;
    private static final double DEBUG_AUDIENCE_RADIUS = 128.0;
    private static final double SEGMENTED_AUDIENCE_RADIUS = 512.0;
    private static final double BLACK_HOLE_DEFAULT_DISTANCE = 160.0;
    private static final float BLACK_HOLE_DEFAULT_SCALE = 6.0F;
    private static final float BLACK_HOLE_DISK_PITCH = 8.0F;
    private static final int DEFAULT_COLUMNS = 4;
    private static final int DEFAULT_ROWS = 4;
    private static final double DEFAULT_SPACING = DEBUG_SCALE * 2.0;
    private static final SoundEvent RUNE_CHIME = SoundEvent.createVariableRangeEvent(
        ResourceLocation.withDefaultNamespace("block.amethyst_block.chime")
    );

    private ContentCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("quasarcontent")
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
            .then(Commands.literal("segmented-stop")
                .executes(context -> stopSegmented(context.getSource())))
            .then(Commands.literal("blackhole")
                .executes(context -> startBlackHole(
                    context.getSource(),
                    BLACK_HOLE_DEFAULT_DISTANCE,
                    BLACK_HOLE_DEFAULT_SCALE,
                    ContentBlackHoleGeometry.VARIANT_AMBER,
                    false
                ))
                .then(Commands.literal("visual")
                    .executes(context -> startBlackHole(
                        context.getSource(),
                        BLACK_HOLE_DEFAULT_DISTANCE,
                        BLACK_HOLE_DEFAULT_SCALE,
                        ContentBlackHoleGeometry.VARIANT_AMBER,
                        true
                    )))
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
            .then(Commands.literal("blackhole-mesh")
                .executes(context -> startBlackHoleMesh(
                    context.getSource(), BLACK_HOLE_DEFAULT_DISTANCE, BLACK_HOLE_DEFAULT_SCALE
                ))
                .then(Commands.argument("distance", DoubleArgumentType.doubleArg(48.0, 512.0))
                    .executes(context -> startBlackHoleMesh(
                        context.getSource(),
                        DoubleArgumentType.getDouble(context, "distance"),
                        BLACK_HOLE_DEFAULT_SCALE
                    ))
                    .then(Commands.argument("scale", DoubleArgumentType.doubleArg(2.0, 64.0))
                        .executes(context -> startBlackHoleMesh(
                            context.getSource(),
                            DoubleArgumentType.getDouble(context, "distance"),
                            (float)DoubleArgumentType.getDouble(context, "scale")
                        )))))
            .then(Commands.literal("blackhole-stop")
                .executes(context -> stopBlackHoles(context.getSource())));

        ContentEffectCommands.register(root);
        ContentCinematicCommands.register(root);
        dispatcher.register(root);
    }

    private static int startRune(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 effectPosition = ContentCommandSupport.effectPosition(source);
        EffectAudioSpec audio = EffectAudioSpec.builder(DEBUG_LIFETIME_TICKS)
            .cue(EffectAudioCue.at(
                0,
                effectPosition,
                RUNE_CHIME,
                SoundSource.MASTER,
                1.0F,
                1.0F,
                EffectAudioAttenuation.distance(64.0F)
            ))
            .cue(EffectAudioCue.at(
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
            ))
            .build();
        VisualEffectService.start(level, VisualEffectSpec.builder(
            ContentEffectIds.RUNE_CIRCLE,
            effectPosition,
            DEBUG_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(DEBUG_AUDIENCE_RADIUS)
        )
            .rotation(source.getRotation().y, 0.0F, 0.0F)
            .scale(DEBUG_SCALE)
            .audio(audio)
            .build());
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
            SegmentedVisualEffectHandle handle = SegmentedVisualEffectService.start(level, spec);
            ContentCommandSessions.track(level, handle);
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
        return startBlackHole(source, distance, scale, variant, false);
    }

    private static int startBlackHole(
        CommandSourceStack source,
        double distance,
        float scale,
        int variant,
        boolean visualOnly
    ) {
        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPlayer() != null
            ? source.getPlayer().getEyePosition()
            : source.getPosition();
        VisualEffectSpec visual = blackHoleSpec(
            origin,
            source.getRotation().y,
            source.getRotation().x,
            distance,
            scale,
            variant,
            level.getRandom().nextLong()
        );
        if (visualOnly) {
            VisualEffectService.start(level, visual);
            source.sendSuccess(
                () -> Component.literal(
                    "Started the visual-only content black hole ("
                        + blackHoleVariantName(variant) + ") at "
                        + (int)visual.position().x + ", " + (int)visual.position().y + ", "
                        + (int)visual.position().z + " (horizon radius " + visual.scale() + ")."
                ),
                false
            );
            return 1;
        }

        GameplayEffectSpec spec = ContentBlackHoleGameplaySpec.of(visual);
        CompoundTag initialState = new CompoundTag();
        initialState.putFloat("yaw", visual.yaw());
        initialState.putFloat("pitch", visual.pitch());
        initialState.putFloat("roll", visual.roll());
        GameplayEffectHandle handle;
        try {
            handle = GameplayEffectService.start(
                level,
                spec,
                new ContentBlackHoleGameplayController(),
                EffectPersistenceOptions.DEFAULT
                    .withCapabilities(EffectCapabilities.destructiveCapability())
                    .withState(initialState)
            );
        } catch (RuntimeException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        source.sendSuccess(
            () -> Component.literal(
                "Started the gameplay content black hole (" + blackHoleVariantName(variant) + ") at "
                    + (int)visual.position().x + ", " + (int)visual.position().y + ", "
                    + (int)visual.position().z + " (horizon radius " + visual.scale()
                    + ", session " + handle.sessionId() + ")."
            ),
            false
        );
        return 1;
    }

    private static int startBlackHoleMesh(CommandSourceStack source, double distance, float scale) {
        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPlayer() != null
            ? source.getPlayer().getEyePosition()
            : source.getPosition();
        VisualEffectSpec visual = blackHoleMeshSpec(
            origin,
            source.getRotation().y,
            source.getRotation().x,
            distance,
            scale,
            ContentBlackHoleGeometry.VARIANT_AMBER,
            level.getRandom().nextLong()
        );
        VisualEffectService.start(level, visual);
        source.sendSuccess(
            () -> Component.literal("Started the content mesh black-hole comparison."),
            false
        );
        return 1;
    }

    private static int stopBlackHoles(CommandSourceStack source) {
        int stopped = ContentBlackHoleGameplayController.stopAll(source.getLevel());
        source.sendSuccess(
            () -> Component.literal("Stopped " + stopped + " content black hole(s)."),
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
        return blackHoleSpec(ContentEffectIds.BLACK_HOLE,
            new BlackHolePlacement(origin, viewYaw, viewPitch, distance, scale, variant, seed));
    }

    static VisualEffectSpec blackHoleMeshSpec(
        Vec3 origin,
        float viewYaw,
        float viewPitch,
        double distance,
        float scale,
        int variant,
        long seed
    ) {
        return blackHoleSpec(ContentEffectIds.BLACK_HOLE_MESH,
            new BlackHolePlacement(origin, viewYaw, viewPitch, distance, scale, variant, seed));
    }

    private static VisualEffectSpec blackHoleSpec(ResourceLocation effectId, BlackHolePlacement placement) {
        if (!Double.isFinite(placement.distance()) || placement.distance() < 1.0) {
            throw new IllegalArgumentException("Black-hole distance must be finite and positive");
        }
        if (!Float.isFinite(placement.scale()) || placement.scale() <= 0.0F) {
            throw new IllegalArgumentException("Black-hole scale must be finite and positive");
        }
        Vec3 look = Vec3.directionFromRotation(placement.viewPitch(), placement.viewYaw());
        Vec3 center = placement.origin().add(look.scale(placement.distance()));
        EffectAudioSpec audio = ContentBlackHoleAudio.plan(center, BLACK_HOLE_LIFETIME_TICKS);
        return VisualEffectSpec.builder(
            effectId,
            center,
            BLACK_HOLE_LIFETIME_TICKS,
            ContentBlackHoleGeometry.withVariant(placement.seed(), placement.variant()),
            EffectAudience.dimensionWide()
        )
            .rotation(placement.viewYaw(), BLACK_HOLE_DISK_PITCH, 0.0F)
            .scale(placement.scale())
            .audio(audio)
            .build();
    }

    private record BlackHolePlacement(
        Vec3 origin,
        float viewYaw,
        float viewPitch,
        double distance,
        float scale,
        int variant,
        long seed
    ) {
    }

    private static String blackHoleVariantName(int variant) {
        return switch (variant) {
            case ContentBlackHoleGeometry.VARIANT_GOLD -> "gold";
            case ContentBlackHoleGeometry.VARIANT_NEON -> "neon";
            default -> "amber";
        };
    }

    private static int stopSegmented(CommandSourceStack source) {
        int stopped = ContentCommandSessions.stopSegmented(
            source.getLevel(),
            (level, handle) -> SegmentedVisualEffectService.stop(level, handle)
        );
        source.sendSuccess(
            () -> Component.literal("Stopped " + stopped + " content segmented effect(s)."),
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
        return SegmentedVisualEffectSpec.builder(
            ContentEffectIds.RUNE_CIRCLE,
            new Vec3(position.x, position.y - 1.4, position.z),
            SEGMENTED_LIFETIME_TICKS,
            seed,
            EffectAudience.nearby(SEGMENTED_AUDIENCE_RADIUS)
        )
            .rotation(yaw, 0.0F, 0.0F)
            .segmentScale(DEBUG_SCALE)
            .grid(columns, rows)
            .spacing(spacing)
            .layers(1, 0.0)
            .audio(EffectAudioSpec.NONE)
            .priority(EffectPriority.NORMAL)
            .build();
    }
}
