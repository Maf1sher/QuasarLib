package mafisher.quasar.effect.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.persistence.EffectDescriptor;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-level effect store. Invalid descriptors are moved to a quarantine list instead of being
 * discarded, and a store written by a newer schema is kept verbatim instead of being
 * overwritten. Descriptors and block journals stay in memory and serialize only when the level
 * saves, so per-tick transform updates do not allocate NBT. Block journals live in the same file,
 * keyed by effect ID, so they do not have to be reserialized with consumer state every tick.
 */
@ApiStatus.Internal
public final class EffectSavedData extends SavedData {
    public static final String FILE_ID = "quasar_lib_effects";
    public static final int SCHEMA_VERSION = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(EffectSavedData.class);
    private static final String QUARANTINED_JOURNAL_KEY = "quarantined_journal";

    private final Map<UUID, EffectDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<UUID, BlockJournal> journals = new LinkedHashMap<>();
    private final ListTag quarantine = new ListTag();
    private int schemaVersion = SCHEMA_VERSION;
    private boolean newerSchema;
    private CompoundTag newerSchemaSource;
    private boolean readOnlyWriteLogged;

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
            LOGGER.warn(
                "Effect store {} uses schema {} but this build supports {}; keeping it read-only",
                FILE_ID,
                version,
                SCHEMA_VERSION
            );
            return data;
        }

        data.loadDescriptors(tag);
        if (version >= 2) {
            data.loadJournals(tag);
        }
        data.loadQuarantine(tag);
        return data;
    }

    private void loadDescriptors(CompoundTag tag) {
        ListTag effects = tag.getList("effects", Tag.TAG_COMPOUND);
        for (int index = 0; index < effects.size(); index++) {
            CompoundTag entry = effects.getCompound(index);
            try {
                EffectDescriptor descriptor = EffectDescriptorNbt.load(entry);
                if (this.descriptors.containsKey(descriptor.instanceId())) {
                    LOGGER.warn("Quarantining duplicate effect {} from {}", descriptor.instanceId(), FILE_ID);
                    this.quarantine.add(entry.copy());
                    continue;
                }
                this.descriptors.put(descriptor.instanceId(), descriptor);
            } catch (RuntimeException exception) {
                this.quarantine.add(entry.copy());
                LOGGER.warn("Quarantining an unreadable effect entry from {}", FILE_ID, exception);
            }
        }
    }

    private void loadJournals(CompoundTag tag) {
        ListTag storedJournals = tag.getList("journals", Tag.TAG_COMPOUND);
        for (int index = 0; index < storedJournals.size(); index++) {
            CompoundTag entry = storedJournals.getCompound(index);
            UUID instanceId = entry.contains("effect_id") ? entry.getUUID("effect_id") : null;
            if (instanceId == null) {
                LOGGER.warn("Ignoring a block journal without an effect ID in {}", FILE_ID);
                continue;
            }
            EffectDescriptor descriptor = this.descriptors.get(instanceId);
            if (descriptor == null) {
                LOGGER.warn("Ignoring a block journal for unknown effect {} in {}", instanceId, FILE_ID);
                continue;
            }
            try {
                this.journals.put(instanceId, BlockJournal.load(entry));
            } catch (RuntimeException exception) {
                LOGGER.warn("Quarantining effect {} with an unreadable block journal", instanceId, exception);
                CompoundTag raw = EffectDescriptorNbt.save(descriptor);
                raw.put(QUARANTINED_JOURNAL_KEY, entry.copy());
                this.quarantine.add(raw);
                this.descriptors.remove(instanceId);
            }
        }
    }

    private void loadQuarantine(CompoundTag tag) {
        ListTag storedQuarantine = tag.getList("quarantine", Tag.TAG_COMPOUND);
        for (int index = 0; index < storedQuarantine.size(); index++) {
            this.quarantine.add(storedQuarantine.getCompound(index).copy());
        }
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
        if (!this.writable("put")) {
            return;
        }
        EffectDescriptor previous = this.descriptors.put(descriptor.instanceId(), descriptor);
        if (!Objects.equals(previous, descriptor)) {
            this.setDirty();
        }
    }

    public void remove(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (!this.writable("remove")) {
            return;
        }
        boolean removed = this.descriptors.remove(instanceId) != null;
        boolean journalRemoved = this.journals.remove(instanceId) != null;
        if (removed || journalRemoved) {
            this.setDirty();
        }
    }

    /** Moves one unreadable or unsupported descriptor into the preserved quarantine list. */
    public void quarantine(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (!this.writable("quarantine")) {
            return;
        }
        EffectDescriptor descriptor = this.descriptors.get(instanceId);
        BlockJournal journal = this.journals.get(instanceId);
        CompoundTag raw = null;
        if (descriptor != null) {
            try {
                raw = EffectDescriptorNbt.save(descriptor);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not serialize quarantined effect {}", instanceId, exception);
            }
        }
        this.descriptors.remove(instanceId);
        this.journals.remove(instanceId);
        if (raw != null) {
            if (journal != null && journal.isRollbackPending()) {
                try {
                    raw.put(QUARANTINED_JOURNAL_KEY, journal.save());
                    LOGGER.warn(
                        "Preserved a pending block rollback for quarantined effect {} ({} recorded change(s))",
                        instanceId,
                        journal.size()
                    );
                } catch (RuntimeException exception) {
                    LOGGER.warn("Could not preserve the block rollback for quarantined effect {}",
                        instanceId, exception);
                }
            }
            this.quarantine.add(raw);
            this.setDirty();
        } else {
            if (journal != null) {
                this.setDirty();
            }
            if (journal != null && journal.isRollbackPending()) {
                LOGGER.warn("Discarding a pending block rollback for quarantined effect {}", instanceId);
            }
        }
    }

    /** Removes descriptors owned by one parent session and returns how many were removed. */
    public int removeOwnedBy(UUID ownerSessionId) {
        Objects.requireNonNull(ownerSessionId, "ownerSessionId");
        if (!this.writable("removeOwnedBy")) {
            return 0;
        }
        List<UUID> owned = this.descriptors.values().stream()
            .filter(descriptor -> ownerSessionId.equals(descriptor.ownerSessionId()))
            .map(EffectDescriptor::instanceId)
            .toList();
        List<UUID> removed = new ArrayList<>();
        List<UUID> retained = new ArrayList<>();
        int removedJournals = 0;
        for (UUID instanceId : owned) {
            BlockJournal journal = this.journals.get(instanceId);
            if (journal != null && journal.isRollbackPending()) {
                retained.add(instanceId);
                continue;
            }
            this.descriptors.remove(instanceId);
            if (this.journals.remove(instanceId) != null) {
                removedJournals++;
            }
            removed.add(instanceId);
        }
        if (!removed.isEmpty()) {
            this.setDirty();
            LOGGER.info(
                "Removed {} child effect(s) and {} journal(s) owned by {}: {}",
                removed.size(),
                removedJournals,
                ownerSessionId,
                removed
            );
        }
        if (!retained.isEmpty()) {
            LOGGER.warn(
                "Kept {} child effect(s) owned by {} because their rollback is pending: {}",
                retained.size(),
                ownerSessionId,
                retained
            );
        }
        return removed.size();
    }

    public BlockJournal journal(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return this.journals.get(instanceId);
    }

    /**
     * Stores one block journal. Returns {@code false} when the store does not accept writes, so a
     * caller can keep its dirty marker until a later attempt actually persists progress.
     */
    public boolean putJournal(UUID instanceId, BlockJournal journal) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(journal, "journal");
        if (!this.writable("putJournal") || !this.descriptors.containsKey(instanceId)) {
            return false;
        }
        this.journals.put(instanceId, journal);
        this.setDirty();
        return true;
    }

    public void removeJournal(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        if (!this.writable("removeJournal")) {
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

    private boolean writable(String operation) {
        if (!this.newerSchema) {
            return true;
        }
        if (!this.readOnlyWriteLogged) {
            this.readOnlyWriteLogged = true;
            LOGGER.warn(
                "Ignoring {} for effect store {}: schema {} is newer than supported schema {}",
                operation,
                FILE_ID,
                this.schemaVersion,
                SCHEMA_VERSION
            );
        }
        return false;
    }
}
