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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-level effect store. Invalid descriptors are moved to a quarantine list instead of being
 * discarded, and a store written by a newer schema is kept verbatim instead of being
 * overwritten. Descriptors and block journals stay in memory and serialize only when the level
 * saves, so per-tick transform updates do not allocate NBT. Block journals live in the same file,
 * keyed by effect ID, so they do not have to be reserialized with consumer state every tick.
 */
public final class EffectSavedData extends SavedData {
    public static final String FILE_ID = "quasar_lib_effects";
    public static final int SCHEMA_VERSION = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");

    private final Map<UUID, EffectDescriptor> descriptors = new LinkedHashMap<>();
    /** Raw NBT from the last load, preserved so unsupported entries can be quarantined verbatim. */
    private final Map<UUID, CompoundTag> rawDescriptors = new LinkedHashMap<>();
    private final Map<UUID, BlockJournal> journals = new LinkedHashMap<>();
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
        int version = tag.contains("schema_version") ? tag.getInt("schema_version") : 1;
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

        if (version >= 2) {
            ListTag storedJournals = tag.getList("journals", Tag.TAG_COMPOUND);
            for (int index = 0; index < storedJournals.size(); index++) {
                CompoundTag entry = storedJournals.getCompound(index);
                UUID instanceId = entry.contains("effect_id") ? entry.getUUID("effect_id") : null;
                if (instanceId == null || !data.descriptors.containsKey(instanceId)) {
                    continue;
                }
                try {
                    data.journals.put(instanceId, BlockJournal.load(entry));
                } catch (RuntimeException exception) {
                    LOGGER.warn("Quarantining effect {} with an unreadable block journal", instanceId, exception);
                    CompoundTag raw = data.rawDescriptors.remove(instanceId);
                    data.descriptors.remove(instanceId);
                    if (raw != null) {
                        data.quarantine.add(raw);
                    }
                }
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

        ListTag storedJournals = new ListTag();
        for (Map.Entry<UUID, BlockJournal> entry : this.journals.entrySet()) {
            if (!this.descriptors.containsKey(entry.getKey())) {
                continue;
            }
            CompoundTag journalTag = entry.getValue().save();
            journalTag.putUUID("effect_id", entry.getKey());
            storedJournals.add(journalTag);
        }
        result.put("journals", storedJournals);
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
        this.setDirty();
    }

    public void remove(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (this.newerSchema) {
            return;
        }
        boolean removed = this.descriptors.remove(instanceId) != null;
        this.rawDescriptors.remove(instanceId);
        boolean journalRemoved = this.journals.remove(instanceId) != null;
        if (removed || journalRemoved) {
            this.setDirty();
        }
    }

    /** Moves one unreadable or unsupported descriptor into the preserved quarantine list. */
    public void quarantine(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (this.newerSchema) {
            return;
        }
        CompoundTag raw = this.rawDescriptors.get(instanceId);
        EffectDescriptor descriptor = this.descriptors.get(instanceId);
        if (raw == null && descriptor != null) {
            try {
                raw = EffectDescriptorNbt.save(descriptor);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not serialize quarantined effect {}", instanceId, exception);
            }
        }
        this.rawDescriptors.remove(instanceId);
        this.descriptors.remove(instanceId);
        BlockJournal journal = this.journals.remove(instanceId);
        if (raw != null) {
            this.quarantine.add(raw);
            this.setDirty();
        } else if (journal != null) {
            this.setDirty();
        }
        if (journal != null && journal.isRollbackPending()) {
            LOGGER.warn("Discarding a pending block rollback for quarantined effect {}", instanceId);
        }
    }

    /** Removes descriptors owned by one parent session and returns how many were removed. */
    public int removeOwnedBy(UUID ownerSessionId) {
        Objects.requireNonNull(ownerSessionId, "ownerSessionId");
        if (this.newerSchema) {
            return 0;
        }
        List<UUID> owned = this.descriptors.values().stream()
            .filter(descriptor -> ownerSessionId.equals(descriptor.ownerSessionId()))
            .map(EffectDescriptor::instanceId)
            .toList();
        for (UUID instanceId : owned) {
            this.descriptors.remove(instanceId);
            this.rawDescriptors.remove(instanceId);
            this.journals.remove(instanceId);
        }
        if (!owned.isEmpty()) {
            this.setDirty();
        }
        return owned.size();
    }

    public BlockJournal journal(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return this.journals.get(instanceId);
    }

    public void putJournal(UUID instanceId, BlockJournal journal) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(journal, "journal");
        if (this.newerSchema || !this.descriptors.containsKey(instanceId)) {
            return;
        }
        this.journals.put(instanceId, journal);
        this.setDirty();
    }

    public void removeJournal(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (this.newerSchema) {
            return;
        }
        if (this.journals.remove(instanceId) != null) {
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

    /** A defensive copy of journal IDs, used by diagnostics and tests. */
    public List<UUID> journalIds() {
        return new ArrayList<>(this.journals.keySet());
    }
}
