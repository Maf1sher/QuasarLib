package mafisher.quasar;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(QuasarLib.MOD_ID)
public final class QuasarLib {
    public static final String MOD_ID = "quasar_lib";

    public QuasarLib(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, QuasarConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, QuasarClientConfig.SPEC);
    }
}
