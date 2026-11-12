package mafisher.quasar.effect.runtime;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.EffectSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.ApiStatus;

/** Saved-data-backed effect store. */
@ApiStatus.Internal
public final class SavedDataEffectStore implements EffectStore {
    private final ServerLevel level;

    public SavedDataEffectStore(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
    }

    private EffectSavedData dataOrNull() {
        DimensionDataStorage storage = this.level.getDataStorage();
        if (storage == null) {
            return null;
        }
        return storage.computeIfAbsent(EffectSavedData.factory(), EffectSavedData.FILE_ID);
    }

    @Override
    public boolean isNewerSchema() {
        EffectSavedData data = dataOrNull();
        return data != null && data.isNewerSchema();
    }

    @Override
    public int schemaVersion() {
        EffectSavedData data = dataOrNull();
        return data == null ? 0 : data.schemaVersion();
    }

    @Override
    public void recordStart(EffectDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.persistence() != EffectPersistence.RESUMABLE) {
            return;
        }
        EffectSavedData data = dataOrNull();
        if (data != null) {
            data.put(descriptor);
        }
    }

    @Override
    public void recordUpdate(EffectDescriptor descriptor) {
        recordStart(descriptor);
    }

    @Override
    public void recordStop(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        EffectSavedData data = dataOrNull();
        if (data != null) {
            data.remove(instanceId);
        }
    }

    @Override
    public List<EffectDescriptor> descriptors() {
        EffectSavedData data = dataOrNull();
        return data == null ? List.of() : data.descriptors();
    }

    @Override
    public boolean contains(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        EffectSavedData data = dataOrNull();
        return data != null && data.contains(instanceId);
    }

    @Override
    public void remove(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        EffectSavedData data = dataOrNull();
        if (data != null) {
            data.remove(instanceId);
        }
    }

    @Override
    public int removeOwnedBy(UUID ownerSessionId) {
        Objects.requireNonNull(ownerSessionId, "ownerSessionId");
        EffectSavedData data = dataOrNull();
        return data == null ? 0 : data.removeOwnedBy(ownerSessionId);
    }

    @Override
    public void quarantine(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        EffectSavedData data = dataOrNull();
        if (data != null) {
            data.quarantine(instanceId);
        }
    }

    @Override
    public BlockJournal journalOrNull(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        EffectSavedData data = dataOrNull();
        return data == null ? null : data.journal(instanceId);
    }

    @Override
    public boolean recordJournal(UUID instanceId, BlockJournal journal) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(journal, "journal");
        EffectSavedData data = dataOrNull();
        return data != null && data.putJournal(instanceId, journal);
    }
}
