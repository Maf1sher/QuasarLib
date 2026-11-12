package mafisher.quasar.effect.runtime;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.EffectPersistenceService;
import mafisher.quasar.effect.persistence.EffectSavedData;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/** Saved-data-backed effect store. The static persistence helper remains the write path until the
 * service migration moves that logic into this class. */
@ApiStatus.Internal
public final class SavedDataEffectStore implements EffectStore {
    private final ServerLevel level;

    public SavedDataEffectStore(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    @Override
    public EffectSavedData storeOrNull() {
        return EffectPersistenceService.storeOrNull(this.level);
    }

    @Override
    public void recordStart(EffectDescriptor descriptor) {
        EffectPersistenceService.recordStart(this.level, descriptor);
    }

    @Override
    public void recordUpdate(EffectDescriptor descriptor) {
        EffectPersistenceService.recordUpdate(this.level, descriptor);
    }

    @Override
    public void recordStop(UUID instanceId) {
        EffectPersistenceService.recordStop(this.level, instanceId);
    }

    @Override
    public List<EffectDescriptor> descriptors() {
        return EffectPersistenceService.descriptors(this.level);
    }

    @Override
    public BlockJournal journalOrNull(UUID instanceId) {
        return EffectPersistenceService.journalOrNull(this.level, instanceId);
    }

    @Override
    public boolean recordJournal(UUID instanceId, BlockJournal journal) {
        return EffectPersistenceService.recordJournal(this.level, instanceId, journal);
    }
}
