package mafisher.quasar.effect.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-level effect store. Invalid descriptors are moved to a quarantine list instead of being
 * discarded, and a store written by a newer schema is kept verbatim instead of being
 * overwritten.
 */
public final class EffectSavedData extends SavedData {
    public static final String FILE_ID = "quasar_lib_effects";
    public static final int SCHEMA_VERSION = 1;

    private final Map<UUID, EffectDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<UUID, CompoundTag> rawDescriptors = new LinkedHashMap<>();
    private final ListTag quarantine = new ListTag();
    private int schemaVersion = SCHEMA_VERSION;
    private boolean newerSchema;
    private CompoundTag newerSchemaSource;

    public EffectSavedData() {
    }

    public static SavedData.Factory<EffectSavedData> factory() {
        return new SavedData.Factory<>(EffectSavedData::new, EffectSavedData::load);
    }

    public static EffectSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EffectSavedData data = new EffectSavedData();
        int version = tag.contains("schema_version") ? tag.getInt("schema_version") : SCHEMA_VERSION;
        data.schemaVersion = version;
        if (version > SCHEMA_VERSION) {
            data.newerSchema = true;
            data.newerSchemaSource = tag.copy();
            return data;
        }

        ListTag effects = tag.getList("effects", Tag.TAG_COMPOUND);
        for (int index = 0; index < effects.size(); index++) {
            CompoundTag entry = effects.getCompound(index);
            try {
                EffectDescriptor descriptor = EffectDescriptorNbt.load(entry);
                if (data.descriptors.containsKey(descriptor.instanceId())) {
                    data.quarantine.add(entry.copy());
                    continue;
                }
                data.descriptors.put(descriptor.instanceId(), descriptor);
                data.rawDescriptors.put(descriptor.instanceId(), entry.copy());
            } catch (RuntimeException exception) {
                data.quarantine.add(entry.copy());
            }
        }

        ListTag storedQuarantine = tag.getList("quarantine", Tag.TAG_COMPOUND);
        for (int index = 0; index < storedQuarantine.size(); index++) {
            data.quarantine.add(storedQuarantine.getCompound(index).copy());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (this.newerSchema && this.newerSchemaSource != null) {
            return this.newerSchemaSource.copy();
        }

        CompoundTag result = new CompoundTag();
        result.putInt("schema_version", SCHEMA_VERSION);
        ListTag effects = new ListTag();
        for (EffectDescriptor descriptor : this.descriptors.values()) {
            effects.add(EffectDescriptorNbt.save(descriptor));
        }
        result.put("effects", effects);
        result.put("quarantine", this.quarantine.copy());
        return result;
    }

    public List<EffectDescriptor> descriptors() {
        return List.copyOf(this.descriptors.values());
    }

    public EffectDescriptor find(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return this.descriptors.get(instanceId);
    }

    public boolean contains(UUID instanceId) {
        return this.descriptors.containsKey(instanceId);
    }

    public void put(EffectDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (this.newerSchema) {
            return;
        }
        this.descriptors.put(descriptor.instanceId(), descriptor);
        this.rawDescriptors.put(descriptor.instanceId(), EffectDescriptorNbt.save(descriptor));
        this.setDirty();
    }

    public void remove(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (this.newerSchema) {
            return;
        }
        boolean removed = this.descriptors.remove(instanceId) != null;
        this.rawDescriptors.remove(instanceId);
        if (removed) {
            this.setDirty();
        }
    }

    /** Moves one unreadable or unsupported descriptor into the preserved quarantine list. */
    public void quarantine(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (this.newerSchema) {
            return;
        }
        CompoundTag raw = this.rawDescriptors.remove(instanceId);
        this.descriptors.remove(instanceId);
        if (raw != null) {
            this.quarantine.add(raw);
            this.setDirty();
        }
    }

    public int quarantineCount() {
        return this.quarantine.size();
    }

    public int schemaVersion() {
        return this.schemaVersion;
    }

    public boolean isNewerSchema() {
        return this.newerSchema;
    }

    /** A defensive copy of the quarantine entries, used by diagnostics and tests. */
    public ListTag quarantineEntries() {
        return this.quarantine.copy();
    }

    public List<UUID> instanceIds() {
        return new ArrayList<>(this.descriptors.keySet());
    }
}
