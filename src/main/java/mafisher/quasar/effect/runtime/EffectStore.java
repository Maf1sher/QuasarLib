package mafisher.quasar.effect.runtime;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.BlockJournal;
import org.jetbrains.annotations.ApiStatus;

/**
 * Per-level persistence seam for effect descriptors and block journals. The default implementation
 * writes through the level's saved data; tests can bind an in-memory store instead.
 */
@ApiStatus.Internal
public interface EffectStore {
    /** Whether the saved data was written by a newer schema and must stay read-only. */
    boolean isNewerSchema();

    /** Schema version of the saved data, or {@code 0} when the level has no data storage. */
    int schemaVersion();

    void recordStart(EffectDescriptor descriptor);

    void recordUpdate(EffectDescriptor descriptor);

    void recordStop(UUID instanceId);

    List<EffectDescriptor> descriptors();

    boolean contains(UUID instanceId);

    void remove(UUID instanceId);

    int removeOwnedBy(UUID ownerSessionId);

    void quarantine(UUID instanceId);

    BlockJournal journalOrNull(UUID instanceId);

    boolean recordJournal(UUID instanceId, BlockJournal journal);
}
