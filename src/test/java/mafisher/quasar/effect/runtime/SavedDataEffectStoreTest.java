package mafisher.quasar.effect.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.EffectSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
class SavedDataEffectStoreTest {
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("quasar_lib", "store_test");
    private static final ResourceLocation DIMENSION =
        ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void missingDataStorageIsANoOp() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getDataStorage()).thenReturn(null);
        SavedDataEffectStore store = new SavedDataEffectStore(level);
        EffectDescriptor descriptor = descriptor();

        assertFalse(store.isNewerSchema());
        assertEquals(0, store.schemaVersion());
        assertTrue(store.descriptors().isEmpty());
        assertFalse(store.contains(descriptor.instanceId()));
        assertNull(store.journalOrNull(descriptor.instanceId()));
        assertFalse(store.recordJournal(descriptor.instanceId(), new BlockJournal()));
        store.recordStart(descriptor);
        store.recordUpdate(descriptor);
        store.recordStop(descriptor.instanceId());
        store.remove(descriptor.instanceId());
        store.quarantine(descriptor.instanceId());
        assertEquals(0, store.removeOwnedBy(descriptor.instanceId()));
    }

    @Test
    void writesThroughTheSavedData() {
        ServerLevel level = mock(ServerLevel.class);
        DimensionDataStorage storage = mock(DimensionDataStorage.class);
        EffectSavedData data = new EffectSavedData();
        when(level.getDataStorage()).thenReturn(storage);
        doReturn(data).when(storage).computeIfAbsent(
            any(SavedData.Factory.class), eq(EffectSavedData.FILE_ID)
        );
        SavedDataEffectStore store = new SavedDataEffectStore(level);
        EffectDescriptor descriptor = descriptor();

        store.recordStart(descriptor);

        assertEquals(1, store.descriptors().size());
        assertTrue(store.contains(descriptor.instanceId()));
        assertEquals(EffectSavedData.SCHEMA_VERSION, store.schemaVersion());

        BlockJournal journal = new BlockJournal();
        assertTrue(store.recordJournal(descriptor.instanceId(), journal));
        assertSame(journal, store.journalOrNull(descriptor.instanceId()));

        store.recordStop(descriptor.instanceId());
        assertFalse(store.contains(descriptor.instanceId()));
    }

    private static EffectDescriptor descriptor() {
        return EffectDescriptor.builder(
                UUID.randomUUID(), EffectKind.VISUAL, TYPE, DIMENSION
            )
            .placement(
                new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F),
                1.0F
            )
            .timing(7L, 0L, 100)
            .audience(EffectAudience.dimensionWide())
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(new CompoundTag())
            .build();
    }
}
