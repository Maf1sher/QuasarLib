package mafisher.quasar.effect.persistence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.DimensionDataStorage;

/** Read/write access to the per-level effect store. Injection points are null-safe for tests. */
public final class EffectPersistenceService {
    private EffectPersistenceService() {
    }

    public static EffectSavedData storeOrNull(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        DimensionDataStorage storage = level.getDataStorage();
        if (storage == null) {
            return null;
        }
        return storage.computeIfAbsent(EffectSavedData.factory(), EffectSavedData.FILE_ID);
    }

    public static void recordStart(ServerLevel level, EffectDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.persistence() != EffectPersistence.RESUMABLE) {
            return;
        }
        EffectSavedData store = storeOrNull(level);
        if (store != null) {
            store.put(descriptor);
        }
    }

    public static void recordUpdate(ServerLevel level, EffectDescriptor descriptor) {
        recordStart(level, descriptor);
    }

    public static void recordStop(ServerLevel level, UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        EffectSavedData store = storeOrNull(level);
        if (store != null) {
            store.remove(instanceId);
        }
    }

    public static List<EffectDescriptor> descriptors(ServerLevel level) {
        EffectSavedData store = storeOrNull(level);
        return store == null ? List.of() : store.descriptors();
    }

    /** Returns the engine-managed block journal for one effect, or {@code null} when absent. */
    public static BlockJournal journalOrNull(ServerLevel level, UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        EffectSavedData store = storeOrNull(level);
        return store == null ? null : store.journal(instanceId);
    }

    /** Replaces the in-memory journal; the full NBT is serialized only when the level saves. */
    public static void recordJournal(ServerLevel level, UUID instanceId, BlockJournal journal) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(journal, "journal");
        EffectSavedData store = storeOrNull(level);
        if (store != null) {
            store.putJournal(instanceId, journal);
        }
    }
}
