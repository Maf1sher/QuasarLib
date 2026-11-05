package mafisher.quasar.command;

import com.mojang.brigadier.CommandDispatcher;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.EffectScope;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectLifetimePolicy;
import mafisher.quasar.effect.EffectVisualIds;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.entity.EffectProbeEntity;
import mafisher.quasar.effect.entity.QuasarEffectEntities;
import mafisher.quasar.sound.QuasarSounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class EffectDebugCommands {
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
                        .then(Commands.literal("ring").executes(context -> testRing(context.getSource())))
                        .then(Commands.literal("entity").executes(context -> testEntity(context.getSource())))
                        .then(Commands.literal("all").executes(context -> testAll(context.getSource())))
                )
        );
    }

    private static int testRing(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        startRing(level, position, source.getRotation().y);
        playTestSound(level, position);
        source.sendSuccess(() -> Component.literal("Started the visual-only rune effect test."), false);
        return 1;
    }

    private static int testEntity(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        if (!spawnProbe(level, position)) {
            source.sendFailure(Component.literal("Could not create the effect probe entity."));
            return 0;
        }
        playTestSound(level, position);
        source.sendSuccess(() -> Component.literal("Spawned the harmless timed effect entity test."), false);
        return 1;
    }

    private static int testAll(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 position = effectPosition(source);
        startRing(level, position, source.getRotation().y);
        if (!spawnProbe(level, position)) {
            source.sendFailure(Component.literal("Started the visual test, but could not create the effect entity."));
            return 0;
        }
        playTestSound(level, position);
        source.sendSuccess(() -> Component.literal("Started both harmless effect framework tests."), false);
        return 1;
    }

    private static void startRing(ServerLevel level, Vec3 position, float yaw) {
        VisualEffectService.start(level, new VisualEffectSpec(
            EffectVisualIds.DEBUG_RUNE_CIRCLE,
            position,
            yaw,
            0.0F,
            5.0F,
            120,
            level.getRandom().nextLong(),
            EffectAudience.nearby(128.0)
        ));
    }

    private static boolean spawnProbe(ServerLevel level, Vec3 position) {
        EffectProbeEntity entity = QuasarEffectEntities.EFFECT_PROBE.get().create(level);
        if (entity == null) {
            return false;
        }
        entity.configureLifetime(80);
        entity.setPos(position.x, position.y, position.z);
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
}
