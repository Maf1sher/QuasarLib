package mafisher.quasar.client.effect;

import mafisher.quasar.QuasarLib;
import mafisher.quasar.api.client.impulse.ClientEffectImpulses;
import mafisher.quasar.client.effect.audio.ClientEffectAudio;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector3f;

@EventBusSubscriber(modid = QuasarLib.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
@ApiStatus.Internal
public final class ClientEffectLifecycleEvents {
    private ClientEffectLifecycleEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        ClientEffectAudio.tick();
        ClientEffectImpulses.tick();
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientEffectImpulses.hasShake()) {
            return;
        }
        Vec3 position = event.getCamera().getPosition();
        Vector3f offsets = ClientEffectImpulses.shakeOffsets(
            position, (float) event.getPartialTick());
        if (offsets.x != 0.0F) {
            event.setYaw(event.getYaw() + offsets.x);
        }
        if (offsets.y != 0.0F) {
            event.setPitch(event.getPitch() + offsets.y);
        }
        if (offsets.z != 0.0F) {
            event.setRoll(event.getRoll() + offsets.z);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ClientEffectAudio.clear();
            ClientEffectImpulses.clear();
            ClientEffectFrameRuntime.onLevelUnload(level);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEffectFrameRuntime.onDisconnect();
    }
}
