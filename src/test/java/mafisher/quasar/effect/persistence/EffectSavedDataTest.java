package mafisher.quasar.effect.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
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
    void removesAndQuarantinesExistingDescriptors() {
        EffectSavedData data = new EffectSavedData();
        EffectDescriptor descriptor = descriptor(3L);
        data.put(descriptor);

        data.quarantine(descriptor.instanceId());

        assertFalse(data.contains(descriptor.instanceId()));
        assertEquals(1, data.quarantineCount());
    }

    @Test
    void writesOnlyKnownSchema() {
        EffectSavedData data = new EffectSavedData();
        data.put(descriptor(4L));

        CompoundTag saved = data.save(new CompoundTag(), null);

        assertEquals(EffectSavedData.SCHEMA_VERSION, saved.getInt("schema_version"));
        assertEquals(Tag.TAG_LIST, saved.getTagType("effects"));
    }

    private static EffectDescriptor descriptor(long startGameTime) {
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
            null,
            new CompoundTag()
        );
    }
}
