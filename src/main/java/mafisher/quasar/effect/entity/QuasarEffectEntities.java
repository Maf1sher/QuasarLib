package mafisher.quasar.effect.entity;

import java.util.function.Supplier;
import mafisher.quasar.QuasarLib;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class QuasarEffectEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(
        BuiltInRegistries.ENTITY_TYPE, QuasarLib.MOD_ID
    );

    public static final Supplier<EntityType<EffectProbeEntity>> EFFECT_PROBE = ENTITY_TYPES.register(
        "effect_probe",
        () -> EntityType.Builder.of(EffectProbeEntity::new, MobCategory.MISC)
            .sized(1.0F, 3.0F)
            .clientTrackingRange(10)
            .updateInterval(1)
            .noSave()
            .noSummon()
            .build("effect_probe")
    );

    private QuasarEffectEntities() {
    }
}
