package mafisher.quasar.effect.persistence;

import java.util.List;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.network.ResumeEffectsPayload;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Restores persisted effects on level load and sends resume snapshots to joining players. */
@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class EffectPersistenceEvents {
    private EffectPersistenceEvents() {
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EffectRestoreService.restoreLevel(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            VisualEffectService.clear(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            VisualEffectService.clear(level);
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
        PacketDistributor.sendToPlayer(
            player,
            new ResumeEffectsPayload(level.dimension().location(), effects)
        );
    }
}
