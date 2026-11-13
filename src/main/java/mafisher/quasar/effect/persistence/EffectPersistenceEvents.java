package mafisher.quasar.effect.persistence;

import java.util.List;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.scale.SegmentedVisualEffectService;
import mafisher.quasar.api.scale.StartSegmentedVisualEffectPayload;
import mafisher.quasar.effect.EffectLevelLifecycle;
import mafisher.quasar.effect.network.EffectAudioPlanPayload;
import mafisher.quasar.effect.network.ResumeEffectsPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.ApiStatus;

/** Restores persisted effects on level load and sends resume snapshots to joining players. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
@ApiStatus.Internal
public final class EffectPersistenceEvents {
    private EffectPersistenceEvents() {
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EffectLevelLifecycle.reset(level);
            EffectRestoreService.restoreLevel(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendResume(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendResume(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendResume(player);
        }
    }

    private static void sendResume(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<StartVisualEffectPayload> effects = VisualEffectService.resumePayloads(level, player);
        List<StartSegmentedVisualEffectPayload> segmentedEffects =
            SegmentedVisualEffectService.resumePayloads(level, player);
        PacketDistributor.sendToPlayer(
            player,
            new ResumeEffectsPayload(level.dimension().location(), effects, segmentedEffects)
        );
        VisualEffectService.sendParameterSnapshots(level, player);
        for (EffectAudioPlan plan : EffectAudioService.resumePlans(level, player)) {
            PacketDistributor.sendToPlayer(player, new EffectAudioPlanPayload(plan, level.getGameTime()));
        }
    }
}
