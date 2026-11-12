package mafisher.quasar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.ToIntFunction;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.cinematic.CinematicAudioMode;
import mafisher.quasar.api.cinematic.CinematicEffectHandle;
import mafisher.quasar.api.cinematic.CinematicEffectService;
import mafisher.quasar.api.cinematic.CinematicEffectStartResult;
import mafisher.quasar.api.cinematic.CinematicTimeline;
import mafisher.quasar.api.cinematic.EffectEasing;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectBudgetReport;
import mafisher.quasar.api.effect.EffectDamageShape;
import mafisher.quasar.api.effect.EffectDebrisSpawn;
import mafisher.quasar.api.effect.EffectDebrisSpec;
import mafisher.quasar.api.effect.EffectForceField;
import mafisher.quasar.api.effect.EffectHitbox;
import mafisher.quasar.api.effect.EffectLifetimePolicy;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.EffectTickBudget;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectController;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.EffectVisualIds;
import mafisher.quasar.effect.cinematic.CinematicTimelineRegistry;
import mafisher.quasar.effect.entity.EffectProbeEntity;
import mafisher.quasar.effect.entity.QuasarEffectEntities;
import mafisher.quasar.effect.persistence.EffectRecoveryReport;
import mafisher.quasar.effect.persistence.EffectRestoreService;
import mafisher.quasar.sound.QuasarSounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.ApiStatus;

@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
@ApiStatus.Internal
public final class EffectDebugCommands {
    private static final float ANGLE_LIMIT_DEGREES = 360.0F;
    private static final int DEBUG_EFFECT_LIFETIME_TICKS = 30 * 20;
    private static final int GAMEPLAY_TELEGRAPH_TICKS = 20;
    private static final int GAMEPLAY_STRIKE_TICKS = 10;
    private static final int GAMEPLAY_FADE_TICKS = 20;
    private static final int GAMEPLAY_DURATION_TICKS =
        GAMEPLAY_TELEGRAPH_TICKS + GAMEPLAY_STRIKE_TICKS + GAMEPLAY_FADE_TICKS;

    private EffectDebugCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("quasarlib")
                .then(
                    Commands.literal("effect")
                        .requires(source -> source.hasPermission(2))
                        .then(withAngles(
                            Commands.literal("ring").executes(context -> testRing(
                                context.getSource(), defaultRingAngles(context.getSource())
                            )),
                            context -> testRing(context.getSource(), readAngles(context))
                        ))
                        .then(withAngles(
                            Commands.literal("waves").executes(context -> testWaves(
                                context.getSource(), defaultRingAngles(context.getSource())
                            )),
                            context -> testWaves(context.getSource(), readAngles(context))
                        ))
                        .then(withAngles(
                            Commands.literal("blur").executes(context -> testSceneBlur(
                                context.getSource(), defaultRingAngles(context.getSource())
                            )),
                            context -> testSceneBlur(context.getSource(), readAngles(context))
                        ))
                        .then(withAngles(
                            Commands.literal("bloom").executes(context -> testBloom(
                                context.getSource(), defaultRingAngles(context.getSource())
                            )),
                            context -> testBloom(context.getSource(), readAngles(context))
                        ))
                        .then(withAngles(
                            Commands.literal("black-hole").executes(context -> testBlackHole(
                                context.getSource(), defaultRingAngles(context.getSource())
                            )),
                            context -> testBlackHole(context.getSource(), readAngles(context))
                        ))
                        .then(withAngles(
                            Commands.literal("entity").executes(context -> testEntity(
                                context.getSource(), EffectAngles.ZERO
                            )),
                            context -> testEntity(context.getSource(), readAngles(context))
                        ))
                        .then(Commands.literal("gameplay-pulse")
                            .executes(context -> testGameplayPulse(context.getSource())))
                        .then(Commands.literal("debris-probe")
                            .executes(context -> testDebrisProbe(context.getSource())))
                        .then(Commands.literal("gameplay-stop-all")
                            .executes(context -> stopGameplayEffects(context.getSource())))
                        .then(withAngles(
                            Commands.literal("all").executes(context -> testAll(
                                context.getSource(), defaultRingAngles(context.getSource()), EffectAngles.ZERO
                            )),
                            context -> {
                                EffectAngles angles = readAngles(context);
                                return testAll(context.getSource(), angles, angles);
                            }
                        ))
                )
                .then(
                    Commands.literal("cinematic")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("demo")
                            .executes(context -> testCinematic(context.getSource())))
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
                        .then(Commands.literal("stop-all")
                            .executes(context -> stopCinematics(context.getSource())))
                )
                .then(
                    Commands.literal("persistence")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("report")
                            .executes(context -> persistenceReport(context.getSource())))
                )
                .then(
                    Commands.literal("budget")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> budgetReport(context.getSource()))
                )
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> withAngles(
        LiteralArgumentBuilder<CommandSourceStack> command,
        ToIntFunction<CommandContext<CommandSourceStack>> executor
    ) {
        return command.then(Commands.argument("yaw", FloatArgumentType.floatArg(
                -ANGLE_LIMIT_DEGREES, ANGLE_LIMIT_DEGREES
            ))
            .then(Commands.argument("pitch", FloatArgumentType.floatArg(
                    -ANGLE_LIMIT_DEGREES, ANGLE_LIMIT_DEGREES
                ))
                .then(Commands.argument("roll", FloatArgumentType.floatArg(
                        -ANGLE_LIMIT_DEGREES, ANGLE_LIMIT_DEGREES
                    ))
                    .executes(executor::applyAsInt))));
    }

    private static EffectAngles readAngles(CommandContext<CommandSourceStack> context) {
        return new EffectAngles(
            context.getArgument("yaw", Float.class),
            context.getArgument("pitch", Float.class),
            context.getArgument("roll", Float.class)
        );
    }

    private static EffectAngles defaultRingAngles(CommandSourceStack source) {
        return new EffectAngles(source.getRotation().y, 0.0F, 0.0F);
    }

    private static int testRing(CommandSourceStack source, EffectAngles angles) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        startRing(level, position, angles);
        playTestSound(level, position);
        source.sendSuccess(() -> Component.literal("Started the visual-only rune effect test."), false);
        return 1;
    }

    private static int testWaves(CommandSourceStack source, EffectAngles angles) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        VisualEffectService.start(level, VisualEffectSpec.builder(
            EffectVisualIds.DEBUG_WAVE_DISTORTION,
            position,
            DEBUG_EFFECT_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(128.0)
        )
            .rotation(angles.yaw(), angles.pitch(), angles.roll())
            .scale(5.0F)
            .build());
        source.sendSuccess(() -> Component.literal("Started the visual-only wave distortion test."), false);
        return 1;
    }

    private static int testSceneBlur(CommandSourceStack source, EffectAngles angles) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        VisualEffectService.start(level, VisualEffectSpec.builder(
            EffectVisualIds.DEBUG_SCENE_BLUR,
            position,
            DEBUG_EFFECT_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(128.0)
        )
            .rotation(angles.yaw(), angles.pitch(), angles.roll())
            .scale(5.0F)
            .build());
        source.sendSuccess(() -> Component.literal("Started the visual-only scene blur test."), false);
        return 1;
    }

    private static int testBloom(CommandSourceStack source, EffectAngles angles) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        VisualEffectService.start(level, VisualEffectSpec.builder(
            EffectVisualIds.DEBUG_BLOOM,
            position,
            DEBUG_EFFECT_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(128.0)
        )
            .rotation(angles.yaw(), angles.pitch(), angles.roll())
            .scale(5.0F)
            .build());
        source.sendSuccess(() -> Component.literal("Started the visual-only bloom test."), false);
        return 1;
    }

    private static int testBlackHole(CommandSourceStack source, EffectAngles angles) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        VisualEffectService.start(level, VisualEffectSpec.builder(
            EffectVisualIds.DEBUG_BLACK_HOLE,
            position,
            DEBUG_EFFECT_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(128.0)
        )
            .rotation(angles.yaw(), angles.pitch(), angles.roll())
            .scale(5.0F)
            .build());
        source.sendSuccess(() -> Component.literal("Started the visual-only black-hole effect test."), false);
        return 1;
    }

    private static int testEntity(CommandSourceStack source, EffectAngles angles) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        if (!spawnProbe(level, position, angles)) {
            source.sendFailure(Component.literal("Could not create the effect probe entity."));
            return 0;
        }
        playTestSound(level, position);
        source.sendSuccess(() -> Component.literal("Spawned the harmless timed effect entity test."), false);
        return 1;
    }

    private static int testGameplayPulse(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        ServerPlayer player = source.getPlayer();
        DamageSource damageSource = player == null
            ? level.damageSources().magic()
            : level.damageSources().playerAttack(player);
        GameplayEffectSpec spec = new GameplayEffectSpec(
            ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "debug_gameplay_pulse"),
            player == null ? null : player.getUUID(),
            position,
            EffectPhasePlan.of(
                new EffectPhase("telegraph", GAMEPLAY_TELEGRAPH_TICKS),
                new EffectPhase("strike", GAMEPLAY_STRIKE_TICKS),
                new EffectPhase("fade", GAMEPLAY_FADE_TICKS)
            ),
            level.getRandom().nextLong(),
            new EffectTickBudget(8, GAMEPLAY_STRIKE_TICKS, 0, 0, 0, 16, 24),
            OwnerPolicy.STOP_WHEN_UNAVAILABLE
        );
        GameplayEffectHandle handle;
        try {
            handle = GameplayEffectService.start(
                level,
                spec,
                new GameplayPulseController(position, damageSource)
            );
        } catch (IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        source.sendSuccess(
            () -> Component.literal("Started the debug gameplay pulse " + handle.sessionId() + "."),
            false
        );
        return 1;
    }

    private static int testDebrisProbe(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();
        Vec3 origin = player == null
            ? source.getPosition().add(0.0, 1.6, 0.0)
            : player.getEyePosition().add(player.getLookAngle().scale(5.0));
        GameplayEffectSpec spec = new GameplayEffectSpec(
            ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "debug_debris_probe"),
            player == null ? null : player.getUUID(),
            origin,
            EffectPhasePlan.of(new EffectPhase("burst", 40)),
            level.getRandom().nextLong(),
            new EffectTickBudget(0, 0, 0, 0, 0, 0, 64),
            OwnerPolicy.STOP_WHEN_UNAVAILABLE
        );
        try {
            GameplayEffectService.start(level, spec, new GameplayEffectController() {
                @Override
                public void onStart(GameplayEffectContext context) {
                    context.spawnDebris(new EffectDebrisSpec(
                        origin,
                        Blocks.STONE.defaultBlockState(),
                        32,
                        Vec3.ZERO,
                        0.0,
                        1.0,
                        0.0,
                        200,
                        EffectAudience.nearby(128.0),
                        null,
                        new EffectDebrisSpawn.Point(),
                        8.0F
                    ));
                }

                @Override
                public void onTick(GameplayEffectContext context) {
                }
            });
        } catch (IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        source.sendSuccess(
            () -> Component.literal("Spawned a stationary debris probe in front of the player."),
            false
        );
        return 1;
    }

    private static int stopGameplayEffects(CommandSourceStack source) {
        int stopped = GameplayEffectService.stopAll(
            source.getLevel(),
            GameplayEffectEndReason.CANCELLED
        );
        source.sendSuccess(
            () -> Component.literal("Stopped " + stopped + " gameplay effect session(s)."),
            false
        );
        return 1;
    }

    private static int testCinematic(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        float yaw = source.getRotation().y;
        long seed = level.getRandom().nextLong();
        CinematicTimeline timeline = CinematicTimeline.builder(
                ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "debug_cinematic_demo"),
                240,
                seed
            )
            .transformUpdateIntervalTicks(1)
            .audioMode(CinematicAudioMode.CLIENT)
            .sound(0, position, QuasarSounds.EFFECT_TEST.get(), SoundSource.MASTER, 4.0F, 1.0F)
            .visual("ring", 0, VisualEffectSpec.builder(
                EffectVisualIds.DEBUG_RUNE_CIRCLE,
                position,
                220,
                seed + 1L,
                EffectAudience.nearby(128.0)
            )
                .rotation(yaw, 0.0F, 0.0F)
                .scale(5.0F)
                .build())
            .transform("ring", 0, cinematicTransform(position, yaw), EffectEasing.EASE_OUT_CUBIC)
            .transform("ring", 100, cinematicTransform(position.add(0.0, 3.0, 0.0), yaw + 180.0F),
                EffectEasing.LINEAR)
            .sound(20, position, QuasarSounds.EFFECT_TEST.get(), SoundSource.MASTER, 4.0F, 1.2F)
            .visual("waves", 40, VisualEffectSpec.builder(
                EffectVisualIds.DEBUG_WAVE_DISTORTION,
                position,
                180,
                seed + 2L,
                EffectAudience.nearby(128.0)
            )
                .rotation(yaw, 0.0F, 0.0F)
                .scale(5.0F)
                .build())
            .sound(100, position, QuasarSounds.EFFECT_TEST.get(), SoundSource.MASTER, 4.0F, 0.8F)
            .visual("bloom", 100, VisualEffectSpec.builder(
                EffectVisualIds.DEBUG_BLOOM,
                position,
                120,
                seed + 3L,
                EffectAudience.nearby(128.0)
            )
                .rotation(yaw, 0.0F, 0.0F)
                .scale(5.0F)
                .build())
            .stopVisual("ring", 160)
            .build();

        CinematicEffectStartResult result;
        try {
            result = CinematicEffectService.start(level, timeline);
        } catch (IllegalStateException exception) {
            source.sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
        if (!result.active()) {
            source.sendFailure(Component.literal("The cinematic was culled by the session budget."));
            return 0;
        }
        CinematicEffectHandle handle = result.handle();
        source.sendSuccess(
            () -> Component.literal(
                "Started cinematic " + timeline.id() + " (" + timeline.cueCount() + " cues, "
                    + timeline.durationTicks() + " ticks): " + handle.sessionId() + "."
            ),
            false
        );
        return 1;
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

    private static int stopCinematics(CommandSourceStack source) {
        int stopped = CinematicEffectService.stopAll(source.getLevel());
        source.sendSuccess(
            () -> Component.literal("Stopped " + stopped + " cinematic effect(s)."),
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

    private static EffectTransform cinematicTransform(Vec3 position, float yaw) {
        return new EffectTransform(
            new EffectPoint(position.x, position.y, position.z),
            yaw,
            0.0F,
            0.0F
        );
    }

    private static int testAll(CommandSourceStack source, EffectAngles ringAngles, EffectAngles entityAngles) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        startRing(level, position, ringAngles);
        if (!spawnProbe(level, position, entityAngles)) {
            source.sendFailure(Component.literal("Started the visual test, but could not create the effect entity."));
            return 0;
        }
        playTestSound(level, position);
        source.sendSuccess(() -> Component.literal("Started both harmless effect framework tests."), false);
        return 1;
    }

    private static void startRing(ServerLevel level, Vec3 position, EffectAngles angles) {
        VisualEffectService.start(level, VisualEffectSpec.builder(
            EffectVisualIds.DEBUG_RUNE_CIRCLE,
            position,
            DEBUG_EFFECT_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(128.0)
        )
            .rotation(angles.yaw(), angles.pitch(), angles.roll())
            .scale(5.0F)
            .build());
    }

    private static boolean spawnProbe(ServerLevel level, Vec3 position, EffectAngles angles) {
        EffectProbeEntity entity = QuasarEffectEntities.EFFECT_PROBE.get().create(level);
        if (entity == null) {
            return false;
        }
        entity.configureLifetime(DEBUG_EFFECT_LIFETIME_TICKS);
        entity.setPos(position.x, position.y, position.z);
        entity.setYRot(angles.yaw());
        entity.setXRot(angles.pitch());
        entity.setEffectRoll(angles.roll());
        try (EffectScope scope = new EffectScope(level)) {
            scope.spawnEntity(entity, EffectLifetimePolicy.FINISH_NATURALLY);
        }
        return !entity.isRemoved();
    }

    private static void playTestSound(ServerLevel level, Vec3 position) {
        level.playSound(null, position.x, position.y, position.z, QuasarSounds.EFFECT_TEST.get(), SoundSource.PLAYERS,
            0.8F, 1.0F);
    }

    private static Vec3 effectPosition(CommandSourceStack source) {
        Vec3 position = source.getPosition();
        return new Vec3(position.x, position.y - 1.4, position.z);
    }

    private static final class GameplayPulseController implements GameplayEffectController {
        private final Vec3 position;
        private final DamageSource damageSource;
        private final EffectHitbox hitbox;
        private final EffectForceField shockwave;

        private GameplayPulseController(Vec3 position, DamageSource damageSource) {
            this.position = position;
            this.damageSource = damageSource;
            this.hitbox = new EffectHitbox(
                new EffectDamageShape.Sphere(
                    new EffectPoint(position.x, position.y, position.z),
                    4.0
                ),
                4.0F,
                0,
                8
            );
            this.shockwave = new EffectForceField.Shockwave(
                new EffectPoint(position.x, position.y, position.z),
                6.0,
                0.6,
                0.35,
                1.0,
                16
            );
        }

        @Override
        public void onStart(GameplayEffectContext context) {
            context.scope().playVisual(VisualEffectSpec.builder(
                EffectVisualIds.DEBUG_RUNE_CIRCLE,
                this.position,
                GAMEPLAY_DURATION_TICKS,
                context.spec().seed(),
                EffectAudience.nearby(128.0)
            )
                .rotation(0.0F, 0.0F, 0.0F)
                .scale(5.0F)
                .build());
        }

        @Override
        public void onPhaseEnter(GameplayEffectContext context, int phaseIndex) {
            if (phaseIndex == 1) {
                context.scope().playSound(
                    this.position,
                    QuasarSounds.EFFECT_TEST.get(),
                    SoundSource.PLAYERS,
                    0.8F,
                    1.0F
                );
                BlockPos origin = BlockPos.containing(this.position);
                BlockState state = context.level().getBlockState(origin.below());
                if (state.isAir()) {
                    state = context.level().getBlockState(origin);
                }
                if (state.isAir()) {
                    state = Blocks.STONE.defaultBlockState();
                }
                context.spawnDebris(new EffectDebrisSpec(
                    this.position.add(0.0, 0.5, 0.0),
                    state,
                    16,
                    new Vec3(0.0, 0.4, 0.0),
                    0.5,
                    0.7,
                    0.08,
                    40,
                    EffectAudience.nearby(128.0)
                ));
            }
        }

        @Override
        public void onTick(GameplayEffectContext context) {
            if (context.phaseIndex() == 1) {
                context.applyHitbox(this.hitbox, this.damageSource, target -> true);
                context.applyForceField(this.shockwave, target -> true);
            }
        }
    }

    private record EffectAngles(float yaw, float pitch, float roll) {
        private static final EffectAngles ZERO = new EffectAngles(0.0F, 0.0F, 0.0F);
    }
}
