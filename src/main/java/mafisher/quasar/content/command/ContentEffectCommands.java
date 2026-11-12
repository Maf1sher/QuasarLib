package mafisher.quasar.content.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.function.ToIntFunction;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectDamageShape;
import mafisher.quasar.api.effect.EffectDebrisSpawn;
import mafisher.quasar.api.effect.EffectDebrisSpec;
import mafisher.quasar.api.effect.EffectForceField;
import mafisher.quasar.api.effect.EffectHitbox;
import mafisher.quasar.api.effect.EffectLifetimePolicy;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectScope;
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
import mafisher.quasar.content.ContentBootstrap;
import mafisher.quasar.content.ContentEffectEntities;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.content.ContentSounds;
import mafisher.quasar.content.demo.ContentProbeEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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

/** Content-owned demo commands for visuals, entities, and gameplay acceptance tests. */
final class ContentEffectCommands {
    private static final float ANGLE_LIMIT_DEGREES = 360.0F;
    private static final int DEMO_LIFETIME_TICKS = 30 * 20;
    private static final float DEMO_SCALE = 5.0F;
    private static final double DEMO_AUDIENCE_RADIUS = 128.0;
    private static final int TELEGRAPH_TICKS = 20;
    private static final int STRIKE_TICKS = 10;
    private static final int FADE_TICKS = 20;
    private static final int GAMEPLAY_DURATION_TICKS = TELEGRAPH_TICKS + STRIKE_TICKS + FADE_TICKS;

    private ContentEffectCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(withAngles(Commands.literal("wave"), context -> startVisual(
            context.getSource(), ContentEffectIds.WAVE_DISTORTION, readAngles(context)
        )));
        root.then(withAngles(Commands.literal("blur"), context -> startVisual(
            context.getSource(), ContentEffectIds.SCENE_BLUR, readAngles(context)
        )));
        root.then(withAngles(Commands.literal("bloom"), context -> startVisual(
            context.getSource(), ContentEffectIds.BLOOM, readAngles(context)
        )));
        root.then(withAngles(Commands.literal("probe"), context -> spawnProbe(
            context.getSource(), readAngles(context)
        )));
        root.then(Commands.literal("gameplay-pulse")
            .executes(context -> startGameplayPulse(context.getSource())));
        root.then(Commands.literal("debris-probe")
            .executes(context -> startDebrisProbe(context.getSource())));
        root.then(Commands.literal("gameplay-stop")
            .executes(context -> stopGameplay(context.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> withAngles(
        LiteralArgumentBuilder<CommandSourceStack> command,
        ToIntFunction<CommandContext<CommandSourceStack>> executor
    ) {
        return command.executes(context -> executor.applyAsInt(context))
            .then(Commands.argument("yaw", FloatArgumentType.floatArg(
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
        if (!context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("yaw"))) {
            return new EffectAngles(context.getSource().getRotation().y, 0.0F, 0.0F);
        }
        return new EffectAngles(
            context.getArgument("yaw", Float.class),
            context.getArgument("pitch", Float.class),
            context.getArgument("roll", Float.class)
        );
    }

    private static int startVisual(CommandSourceStack source, ResourceLocation effectId, EffectAngles angles) {
        ServerLevel level = source.getLevel();
        Vec3 position = ContentCommandSupport.effectPosition(source);
        VisualEffectService.start(level, VisualEffectSpec.builder(
            effectId,
            position,
            DEMO_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(DEMO_AUDIENCE_RADIUS)
        )
            .rotation(angles.yaw(), angles.pitch(), angles.roll())
            .scale(DEMO_SCALE)
            .build());
        source.sendSuccess(() -> Component.literal("Started the content visual " + effectId + "."), false);
        return 1;
    }

    private static int spawnProbe(CommandSourceStack source, EffectAngles angles) {
        ServerLevel level = source.getLevel();
        Vec3 position = ContentCommandSupport.effectPosition(source);
        ContentProbeEntity entity = ContentEffectEntities.PROBE.get().create(level);
        if (entity == null) {
            source.sendFailure(Component.literal("Could not create the content probe entity."));
            return 0;
        }
        entity.configureLifetime(DEMO_LIFETIME_TICKS);
        entity.setPos(position.x, position.y, position.z);
        entity.setYRot(angles.yaw());
        entity.setXRot(angles.pitch());
        entity.setEffectRoll(angles.roll());
        try (EffectScope scope = new EffectScope(level)) {
            scope.spawnEntity(entity, EffectLifetimePolicy.FINISH_NATURALLY);
        }
        if (entity.isRemoved()) {
            source.sendFailure(Component.literal("Could not create the content probe entity."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Spawned the content probe entity."), false);
        return 1;
    }

    private static int startGameplayPulse(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 position = ContentCommandSupport.effectPosition(source);
        ServerPlayer player = source.getPlayer();
        DamageSource damageSource = player == null
            ? level.damageSources().magic()
            : level.damageSources().playerAttack(player);
        GameplayEffectSpec spec = new GameplayEffectSpec(
            ResourceLocation.fromNamespaceAndPath(ContentBootstrap.NAMESPACE, "gameplay_pulse"),
            player == null ? null : player.getUUID(),
            position,
            EffectPhasePlan.of(
                new EffectPhase("telegraph", TELEGRAPH_TICKS),
                new EffectPhase("strike", STRIKE_TICKS),
                new EffectPhase("fade", FADE_TICKS)
            ),
            level.getRandom().nextLong(),
            new EffectTickBudget(8, STRIKE_TICKS, 0, 0, 0, 16, 24),
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
        ContentCommandSessions.track(level, handle);
        source.sendSuccess(
            () -> Component.literal("Started the content gameplay pulse " + handle.sessionId() + "."),
            false
        );
        return 1;
    }

    private static int startDebrisProbe(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ServerPlayer player = source.getPlayer();
        Vec3 origin = player == null
            ? source.getPosition().add(0.0, 1.6, 0.0)
            : player.getEyePosition().add(player.getLookAngle().scale(5.0));
        GameplayEffectSpec spec = new GameplayEffectSpec(
            ResourceLocation.fromNamespaceAndPath(ContentBootstrap.NAMESPACE, "debris_probe"),
            player == null ? null : player.getUUID(),
            origin,
            EffectPhasePlan.of(new EffectPhase("burst", 40)),
            level.getRandom().nextLong(),
            new EffectTickBudget(0, 0, 0, 0, 0, 0, 64),
            OwnerPolicy.STOP_WHEN_UNAVAILABLE
        );
        GameplayEffectHandle handle;
        try {
            handle = GameplayEffectService.start(level, spec, new GameplayEffectController() {
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
                        EffectAudience.nearby(DEMO_AUDIENCE_RADIUS),
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
        ContentCommandSessions.track(level, handle);
        source.sendSuccess(
            () -> Component.literal("Spawned the content debris probe in front of the player."),
            false
        );
        return 1;
    }

    private static int stopGameplay(CommandSourceStack source) {
        int stopped = ContentCommandSessions.stopGameplay(
            source.getLevel(),
            (level, handle) -> GameplayEffectService.stop(
                level, handle, GameplayEffectEndReason.CANCELLED
            )
        );
        source.sendSuccess(
            () -> Component.literal("Stopped " + stopped + " content command session(s)."),
            false
        );
        return 1;
    }

    private record EffectAngles(float yaw, float pitch, float roll) {
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
                new EffectDamageShape.Sphere(new EffectPoint(position.x, position.y, position.z), 4.0),
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
                ContentEffectIds.RUNE_CIRCLE,
                this.position,
                GAMEPLAY_DURATION_TICKS,
                context.spec().seed(),
                EffectAudience.nearby(DEMO_AUDIENCE_RADIUS)
            )
                .rotation(0.0F, 0.0F, 0.0F)
                .scale(DEMO_SCALE)
                .build());
        }

        @Override
        public void onPhaseEnter(GameplayEffectContext context, int phaseIndex) {
            if (phaseIndex != 1) {
                return;
            }
            context.scope().playSound(
                this.position,
                ContentSounds.EFFECT_TEST.get(),
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
                EffectAudience.nearby(DEMO_AUDIENCE_RADIUS)
            ));
        }

        @Override
        public void onTick(GameplayEffectContext context) {
            if (context.phaseIndex() == 1) {
                context.applyHitbox(this.hitbox, this.damageSource, target -> true);
                context.applyForceField(this.shockwave, target -> true);
            }
        }
    }
}
