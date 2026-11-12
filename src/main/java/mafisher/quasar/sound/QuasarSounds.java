package mafisher.quasar.sound;

import java.util.function.Supplier;
import mafisher.quasar.QuasarLib;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class QuasarSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(
        BuiltInRegistries.SOUND_EVENT, QuasarLib.MOD_ID
    );

    public static final Supplier<SoundEvent> EFFECT_TEST = SOUNDS.register(
        "effect_test",
        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "effect_test"))
    );

    private QuasarSounds() {
    }
}
