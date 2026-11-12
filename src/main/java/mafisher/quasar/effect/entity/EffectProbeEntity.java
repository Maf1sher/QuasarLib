package mafisher.quasar.effect.entity;

import mafisher.quasar.api.entity.TimedEffectEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

/** Harmless registered entity used to validate effect-entity tracking and client rendering. */
@ApiStatus.Internal
public final class EffectProbeEntity extends TimedEffectEntity {
    public EffectProbeEntity(EntityType<? extends EffectProbeEntity> entityType, Level level) {
        super(entityType, level);
    }
}
