package mafisher.quasar.content.demo;

import mafisher.quasar.api.entity.TimedEffectEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Harmless content-owned entity used to validate effect-entity tracking and client rendering. */
public final class ContentProbeEntity extends TimedEffectEntity {
    public ContentProbeEntity(EntityType<? extends ContentProbeEntity> entityType, Level level) {
        super(entityType, level);
    }
}
