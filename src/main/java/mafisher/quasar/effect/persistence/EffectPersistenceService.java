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
}
