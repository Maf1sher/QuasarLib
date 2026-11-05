package mafisher.quasar;

import mafisher.quasar.content.ContentBootstrap;
import mafisher.quasar.effect.entity.QuasarEffectEntities;
import mafisher.quasar.sound.QuasarSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(QuasarLib.MOD_ID)
public final class QuasarLib {
    public static final String MOD_ID = "quasar_lib";

    public QuasarLib(IEventBus modEventBus) {
        QuasarEffectEntities.ENTITY_TYPES.register(modEventBus);
        QuasarSounds.SOUNDS.register(modEventBus);
        ContentBootstrap.init(modEventBus);
    }
}
