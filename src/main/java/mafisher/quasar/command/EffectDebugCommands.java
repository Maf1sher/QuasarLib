package mafisher.quasar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.EffectScope;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectLifetimePolicy;
import mafisher.quasar.effect.EffectVisualIds;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.entity.EffectProbeEntity;
import mafisher.quasar.effect.entity.QuasarEffectEntities;
import mafisher.quasar.effect.network.ArmScreenCompositorFailurePayload;
import mafisher.quasar.sound.QuasarSounds;
import java.util.function.ToIntFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class EffectDebugCommands {
    private static final float ANGLE_LIMIT_DEGREES = 360.0F;
    private static final int DEBUG_EFFECT_LIFETIME_TICKS = 30 * 20;

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
                        .then(withAngles(
                            Commands.literal("all").executes(context -> testAll(
                                context.getSource(), defaultRingAngles(context.getSource()), EffectAngles.ZERO
                            )),
                            context -> {
                                EffectAngles angles = readAngles(context);
                                return testAll(context.getSource(), angles, angles);
                            }
                        ))
                        .then(Commands.literal("compositor-fail-next")
                            .executes(context -> armCompositorFailure(context.getSource())))
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
        VisualEffectService.start(level, new VisualEffectSpec(
            EffectVisualIds.DEBUG_WAVE_DISTORTION,
            position,
            angles.yaw(),
            angles.pitch(),
            angles.roll(),
            5.0F,
            DEBUG_EFFECT_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(128.0)
        ));
        source.sendSuccess(() -> Component.literal("Started the visual-only wave distortion test."), false);
        return 1;
    }

    private static int testSceneBlur(CommandSourceStack source, EffectAngles angles) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        VisualEffectService.start(level, new VisualEffectSpec(
            EffectVisualIds.DEBUG_SCENE_BLUR,
            position,
            angles.yaw(),
            angles.pitch(),
            angles.roll(),
            5.0F,
            DEBUG_EFFECT_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(128.0)
        ));
        source.sendSuccess(() -> Component.literal("Started the visual-only scene blur test."), false);
        return 1;
    }

    private static int testBloom(CommandSourceStack source, EffectAngles angles) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        VisualEffectService.start(level, new VisualEffectSpec(
            EffectVisualIds.DEBUG_BLOOM,
            position,
            angles.yaw(),
            angles.pitch(),
            angles.roll(),
            5.0F,
            DEBUG_EFFECT_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(128.0)
        ));
        source.sendSuccess(() -> Component.literal("Started the visual-only bloom test."), false);
        return 1;
    }

    private static int testBlackHole(CommandSourceStack source, EffectAngles angles) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        VisualEffectService.start(level, new VisualEffectSpec(
            EffectVisualIds.DEBUG_BLACK_HOLE,
            position,
            angles.yaw(),
            angles.pitch(),
            angles.roll(),
            5.0F,
            DEBUG_EFFECT_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(128.0)
        ));
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
        VisualEffectService.start(level, new VisualEffectSpec(
            EffectVisualIds.DEBUG_RUNE_CIRCLE,
            position,
            angles.yaw(),
            angles.pitch(),
            angles.roll(),
            5.0F,
            DEBUG_EFFECT_LIFETIME_TICKS,
            level.getRandom().nextLong(),
            EffectAudience.nearby(128.0)
        ));
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

    private static int armCompositorFailure(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        PacketDistributor.sendToPlayer(player, new ArmScreenCompositorFailurePayload());
        source.sendSuccess(
            () -> Component.literal("Armed one compositor failure for your client; show a screen effect to trigger it."),
            false
        );
        return 1;
    }

    private static Vec3 effectPosition(CommandSourceStack source) {
        Vec3 position = source.getPosition();
        return new Vec3(position.x, position.y - 1.4, position.z);
    }

    private record EffectAngles(float yaw, float pitch, float roll) {
        private static final EffectAngles ZERO = new EffectAngles(0.0F, 0.0F, 0.0F);
    }
}
