package mafisher.quasar.content;

import java.util.function.Supplier;
import mafisher.quasar.content.demo.ContentProbeEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Content-owned effect entity registrations. */
public final class ContentEffectEntities {
    static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(
        BuiltInRegistries.ENTITY_TYPE, ContentBootstrap.NAMESPACE
    );

    public static final Supplier<EntityType<ContentProbeEntity>> PROBE = ENTITY_TYPES.register(
        "effect_probe",
        () -> EntityType.Builder.of(ContentProbeEntity::new, MobCategory.MISC)
            .sized(1.0F, 3.0F)
            .clientTrackingRange(10)
            .updateInterval(1)
            .noSave()
            .noSummon()
            .build("effect_probe")
    );

    private ContentEffectEntities() {
    }

    static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
