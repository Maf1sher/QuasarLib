package mafisher.quasar.effect.runtime;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.EffectSavedData;
import net.minecraft.server.level.ServerLevel;

/** Installs a runtime with in-memory persistence and a recording packet sink. */
public final class TestEffectRuntime {
    private TestEffectRuntime() {
    }

    public static LevelEffectRuntime install(ServerLevel level) {
        return install(level, new RecordingPacketSink());
    }

    public static LevelEffectRuntime install(ServerLevel level, RecordingPacketSink packets) {
        LevelEffectRuntime runtime = new LevelEffectRuntime(
            level,
            new InMemoryEffectStore(),
            packets,
            new ConfigEffectBudgetSource()
        );
        EffectRuntimeRegistry.install(level, runtime);
        return runtime;
    }

    public static void uninstall(ServerLevel level) {
        EffectRuntimeRegistry.close(level);
    }

    private static final class InMemoryEffectStore implements EffectStore {
        private final EffectSavedData data = new EffectSavedData();

        @Override
        public EffectSavedData storeOrNull() {
            return this.data;
        }

        @Override
        public void recordStart(EffectDescriptor descriptor) {
            if (descriptor.persistence() == EffectPersistence.RESUMABLE) {
                this.data.put(descriptor);
            }
        }

        @Override
        public void recordUpdate(EffectDescriptor descriptor) {
            recordStart(descriptor);
        }

        @Override
        public void recordStop(UUID instanceId) {
            this.data.remove(instanceId);
        }

        @Override
        public List<EffectDescriptor> descriptors() {
            return this.data.descriptors();
        }

        @Override
        public BlockJournal journalOrNull(UUID instanceId) {
            return this.data.journal(instanceId);
        }

        @Override
        public boolean recordJournal(UUID instanceId, BlockJournal journal) {
            return this.data.putJournal(instanceId, journal);
        }
    }
}
