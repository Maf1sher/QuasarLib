package mafisher.quasar.effect.runtime;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.EffectSavedData;
import org.jetbrains.annotations.ApiStatus;

/**
 * Per-level persistence seam for effect descriptors and block journals. The default implementation
 * writes through the level's saved data; tests can bind an in-memory store instead.
 */
@ApiStatus.Internal
public interface EffectStore {
    /** Returns the saved data store, or {@code null} when the level has no data storage. */
    EffectSavedData storeOrNull();

    void recordStart(EffectDescriptor descriptor);

    void recordUpdate(EffectDescriptor descriptor);

    void recordStop(UUID instanceId);

    List<EffectDescriptor> descriptors();

    BlockJournal journalOrNull(UUID instanceId);

    boolean recordJournal(UUID instanceId, BlockJournal journal);
}
