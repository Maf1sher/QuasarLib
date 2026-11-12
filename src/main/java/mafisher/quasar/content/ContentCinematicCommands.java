package mafisher.quasar.content;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import mafisher.quasar.api.cinematic.CinematicAudioMode;
import mafisher.quasar.api.cinematic.CinematicEffectHandle;
import mafisher.quasar.api.cinematic.CinematicEffectService;
import mafisher.quasar.api.cinematic.CinematicEffectStartResult;
import mafisher.quasar.api.cinematic.CinematicTimeline;
import mafisher.quasar.api.cinematic.EffectEasing;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.VisualEffectSpec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Content-owned cinematic demo commands. */
final class ContentCinematicCommands {
    private static final int DEMO_DURATION_TICKS = 240;

    private ContentCinematicCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("cinematic-demo")
            .executes(context -> startDemo(context.getSource())));
        root.then(Commands.literal("cinematic-stop")
            .executes(context -> stopDemo(context.getSource())));
    }

    private static CinematicTimeline buildDemoTimeline(ServerLevel level, Vec3 position, float yaw, long seed) {
        return CinematicTimeline.builder(
                ResourceLocation.fromNamespaceAndPath(ContentBootstrap.NAMESPACE, "cinematic_demo"),
                DEMO_DURATION_TICKS,
                seed
            )
            .transformUpdateIntervalTicks(1)
            .audioMode(CinematicAudioMode.CLIENT)
            .sound(0, position, ContentSounds.EFFECT_TEST.get(), SoundSource.MASTER, 4.0F, 1.0F)
            .visual("ring", 0, VisualEffectSpec.builder(
                ContentEffectIds.RUNE_CIRCLE,
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
            .sound(20, position, ContentSounds.EFFECT_TEST.get(), SoundSource.MASTER, 4.0F, 1.2F)
            .visual("waves", 40, VisualEffectSpec.builder(
                ContentEffectIds.WAVE_DISTORTION,
                position,
                180,
                seed + 2L,
                EffectAudience.nearby(128.0)
            )
                .rotation(yaw, 0.0F, 0.0F)
                .scale(5.0F)
                .build())
            .sound(100, position, ContentSounds.EFFECT_TEST.get(), SoundSource.MASTER, 4.0F, 0.8F)
            .visual("bloom", 100, VisualEffectSpec.builder(
                ContentEffectIds.BLOOM,
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
    }

    private static int startDemo(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        CinematicTimeline timeline = buildDemoTimeline(
            level, ContentCommandSupport.effectPosition(source), source.getRotation().y,
            level.getRandom().nextLong()
        );

        try {
            CinematicEffectStartResult result = CinematicEffectService.start(level, timeline);
            if (!result.active()) {
                source.sendFailure(Component.literal("The content cinematic was culled by the session budget."));
                return 0;
            }
            CinematicEffectHandle handle = result.handle();
            ContentCommandSessions.track(level, handle);
            source.sendSuccess(
                () -> Component.literal(
                    "Started content cinematic " + timeline.id() + " (" + timeline.cueCount() + " cues, "
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

    private static int stopDemo(CommandSourceStack source) {
        int stopped = ContentCommandSessions.stopCinematic(
            source.getLevel(),
            (level, handle) -> CinematicEffectService.stop(level, handle)
        );
        source.sendSuccess(
            () -> Component.literal("Stopped " + stopped + " content cinematic session(s)."),
            false
        );
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

}
