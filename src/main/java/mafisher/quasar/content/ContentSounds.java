package mafisher.quasar.content;

import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Content-owned sound events shipped under the {@code quasar_content} namespace. */
public final class ContentSounds {
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(
        BuiltInRegistries.SOUND_EVENT, ContentBootstrap.NAMESPACE
    );

    public static final Supplier<SoundEvent> EFFECT_TEST = register("effect_test");
    public static final Supplier<SoundEvent> BLACK_HOLE_DREAD = register("black_hole_dread");
    public static final Supplier<SoundEvent> BLACK_HOLE_DRONE = register("black_hole_drone");
    public static final Supplier<SoundEvent> BLACK_HOLE_IGNITION = register("black_hole_ignition");
    public static final Supplier<SoundEvent> BLACK_HOLE_SHOCKWAVE = register("black_hole_shockwave");
    public static final Supplier<SoundEvent> BLACK_HOLE_ACCRETION = register("black_hole_accretion");
    public static final Supplier<SoundEvent> BLACK_HOLE_PULSE = register("black_hole_pulse");
    public static final Supplier<SoundEvent> BLACK_HOLE_COLLAPSE = register("black_hole_collapse");
    public static final Supplier<SoundEvent> BLACK_HOLE_IMPLOSION = register("black_hole_implosion");
    public static final Supplier<SoundEvent> BLACK_HOLE_ECHO = register("black_hole_echo");
    public static final Supplier<SoundEvent> ORBITAL_LASER_WARNING =
        register("orbital_laser_warning");
    public static final Supplier<SoundEvent> ORBITAL_LASER_DESCENT =
        register("orbital_laser_descent");
    public static final Supplier<SoundEvent> ORBITAL_LASER_IMPACT =
        register("orbital_laser_impact");
    public static final Supplier<SoundEvent> ORBITAL_LASER_DRONE =
        register("orbital_laser_drone");
    public static final Supplier<SoundEvent> ORBITAL_LASER_CRACKLE =
        register("orbital_laser_crackle");
    public static final Supplier<SoundEvent> ORBITAL_LASER_COLLAPSE =
        register("orbital_laser_collapse");
    public static final Supplier<SoundEvent> ORBITAL_LASER_AFTERMATH =
        register("orbital_laser_aftermath");

    private ContentSounds() {
    }

    static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }

    private static Supplier<SoundEvent> register(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(ContentBootstrap.NAMESPACE, path)
        ));
    }
}
