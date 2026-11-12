package mafisher.quasar.effect.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.EffectSavedData;

/** In-memory effect store that also records persistence calls for assertions. */
public final class RecordingEffectStore implements EffectStore {
    private final EffectSavedData data = new EffectSavedData();
    private final List<EffectDescriptor> starts = new ArrayList<>();
    private final List<EffectDescriptor> updates = new ArrayList<>();
    private final List<UUID> stops = new ArrayList<>();

    @Override
    public boolean isNewerSchema() {
        return this.data.isNewerSchema();
    }

    @Override
    public int schemaVersion() {
        return this.data.schemaVersion();
    }

    @Override
    public void recordStart(EffectDescriptor descriptor) {
        this.starts.add(descriptor);
        if (descriptor.persistence() == EffectPersistence.RESUMABLE) {
            this.data.put(descriptor);
        }
    }

    @Override
    public void recordUpdate(EffectDescriptor descriptor) {
        this.updates.add(descriptor);
        if (descriptor.persistence() == EffectPersistence.RESUMABLE) {
            this.data.put(descriptor);
        }
    }

    @Override
    public void recordStop(UUID instanceId) {
        this.stops.add(instanceId);
        this.data.remove(instanceId);
    }

    @Override
    public List<EffectDescriptor> descriptors() {
        return this.data.descriptors();
    }

    @Override
    public boolean contains(UUID instanceId) {
        return this.data.contains(instanceId);
    }

    @Override
    public void remove(UUID instanceId) {
        this.data.remove(instanceId);
    }

    @Override
    public int removeOwnedBy(UUID ownerSessionId) {
        return this.data.removeOwnedBy(ownerSessionId);
    }

    @Override
    public void quarantine(UUID instanceId) {
        this.data.quarantine(instanceId);
    }

    @Override
    public BlockJournal journalOrNull(UUID instanceId) {
        return this.data.journal(instanceId);
    }

    @Override
    public boolean recordJournal(UUID instanceId, BlockJournal journal) {
        return this.data.putJournal(instanceId, journal);
    }

    public EffectSavedData data() {
        return this.data;
    }

    public List<EffectDescriptor> starts() {
        return List.copyOf(this.starts);
    }

    public List<EffectDescriptor> updates() {
        return List.copyOf(this.updates);
    }

    public List<UUID> stops() {
        return List.copyOf(this.stops);
    }
}
