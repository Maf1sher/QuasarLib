package mafisher.quasar.effect.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.block.BlockChange;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

class EffectSavedDataTest {
    private static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void roundTripsDescriptors() {
        EffectSavedData data = new EffectSavedData();
        EffectDescriptor first = descriptor(1L);
        EffectDescriptor second = descriptor(2L);
        data.put(first);
        data.put(second);

        EffectSavedData loaded = EffectSavedData.load(data.save(new CompoundTag(), null), null);

        assertEquals(2, loaded.descriptors().size());
        assertEquals(first, loaded.find(first.instanceId()));
        assertEquals(second, loaded.find(second.instanceId()));
    }

    @Test
    void keepsNewerSchemasVerbatim() {
        CompoundTag source = new CompoundTag();
        source.putInt("schema_version", 99);
        source.putString("future", "data");

        EffectSavedData data = EffectSavedData.load(source, null);

        assertTrue(data.isNewerSchema());
        assertTrue(data.descriptors().isEmpty());
        assertEquals(99, data.save(new CompoundTag(), null).getInt("schema_version"));
    }

    @Test
    void quarantinesUnreadableEntries() {
        CompoundTag bad = new CompoundTag();
        bad.putString("kind", "NOT_A_KIND");
        ListTag effects = new ListTag();
        effects.add(bad);
        CompoundTag source = new CompoundTag();
        source.putInt("schema_version", EffectSavedData.SCHEMA_VERSION);
        source.put("effects", effects);

        EffectSavedData data = EffectSavedData.load(source, null);

        assertEquals(0, data.descriptors().size());
        assertEquals(1, data.quarantineCount());
    }

    @Test
    void quarantinesRuntimeDescriptorsOnDemand() {
        EffectSavedData data = new EffectSavedData();
        EffectDescriptor descriptor = descriptor(3L);
        data.put(descriptor);

        data.quarantine(descriptor.instanceId());

        assertFalse(data.contains(descriptor.instanceId()));
        assertEquals(1, data.quarantineCount());
        assertEquals(descriptor.instanceId(),
            data.quarantineEntries().getCompound(0).getUUID("instance_id"));
    }

    @Test
    void writesOnlyKnownSchema() {
        EffectSavedData data = new EffectSavedData();
        data.put(descriptor(4L));

        CompoundTag saved = data.save(new CompoundTag(), null);

        assertEquals(EffectSavedData.SCHEMA_VERSION, saved.getInt("schema_version"));
        assertEquals(Tag.TAG_LIST, saved.getTagType("effects"));
        assertEquals(Tag.TAG_LIST, saved.getTagType("journals"));
    }

    @Test
    void roundTripsBlockJournals() {
        EffectSavedData data = new EffectSavedData();
        EffectDescriptor descriptor = descriptor(5L);
        data.put(descriptor);
        BlockJournal journal = journal();
        data.putJournal(descriptor.instanceId(), journal);

        EffectSavedData loaded = EffectSavedData.load(data.save(new CompoundTag(), null), null);

        assertEquals(1, loaded.journal(descriptor.instanceId()).size());
        assertEquals(EffectSavedData.SCHEMA_VERSION, loaded.schemaVersion());
    }

    @Test
    void migratesLegacySchemaWithoutJournals() {
        EffectSavedData data = new EffectSavedData();
        data.put(descriptor(6L));
        CompoundTag legacy = data.save(new CompoundTag(), null);
        legacy.putInt("schema_version", 1);
        legacy.remove("journals");

        EffectSavedData loaded = EffectSavedData.load(legacy, null);

        assertEquals(1, loaded.schemaVersion());
        assertEquals(1, loaded.descriptors().size());

        CompoundTag resaved = loaded.save(new CompoundTag(), null);
        assertEquals(EffectSavedData.SCHEMA_VERSION, resaved.getInt("schema_version"));
    }

    @Test
    void removesJournalsWithDescriptors() {
        EffectSavedData data = new EffectSavedData();
        EffectDescriptor descriptor = descriptor(7L);
        data.put(descriptor);
        data.putJournal(descriptor.instanceId(), journal());

        data.remove(descriptor.instanceId());

        assertNull(data.journal(descriptor.instanceId()));
        assertTrue(data.journalIds().isEmpty());
    }

    @Test
    void removesChildDescriptorsByOwnerSession() {
        EffectSavedData data = new EffectSavedData();
        UUID parent = UUID.randomUUID();
        EffectDescriptor child = descriptor(8L, parent);
        data.put(child);

        assertEquals(1, data.removeOwnedBy(parent));

        assertFalse(data.contains(child.instanceId()));
        assertTrue(data.instanceIds().isEmpty());
    }

    @Test
    void ignoresJournalsForUnknownDescriptors() {
        EffectSavedData data = new EffectSavedData();

        assertFalse(data.putJournal(UUID.randomUUID(), journal()));

        assertTrue(data.journalIds().isEmpty());
    }

    @Test
    void putJournalReportsWhenItIsNotStored() {
        CompoundTag source = new CompoundTag();
        source.putInt("schema_version", 99);
        EffectSavedData data = EffectSavedData.load(source, null);

        assertFalse(data.putJournal(UUID.randomUUID(), journal()));
    }

    private static BlockJournal journal() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockEntity(net.minecraft.core.BlockPos.ZERO)).thenReturn(null);
        BlockJournal journal = new BlockJournal();
        journal.record(
            level,
            new BlockChange(BlockPos.ZERO, Blocks.AIR.defaultBlockState()),
            Blocks.STONE.defaultBlockState()
        );
        return journal;
    }

    private static EffectDescriptor descriptor(long startGameTime) {
        return descriptor(startGameTime, null);
    }

    private static EffectDescriptor descriptor(long startGameTime, UUID ownerSessionId) {
        return new EffectDescriptor(
            UUID.randomUUID(),
            EffectKind.VISUAL,
            TYPE,
            DIMENSION,
            new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F),
            1.0F,
            7L,
            startGameTime,
            100,
            EffectAudience.dimensionWide(),
            EffectCapabilities.NONE,
            EffectPersistence.RESUMABLE,
            ownerSessionId,
            new CompoundTag()
        );
    }
}
