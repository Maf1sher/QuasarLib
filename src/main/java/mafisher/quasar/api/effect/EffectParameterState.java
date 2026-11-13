package mafisher.quasar.api.effect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jspecify.annotations.Nullable;

/**
 * Immutable typed overrides for one effect instance.
 *
 * <p>Curve-sampled values are not stored: they are deterministic from the effect progress and
 * are recomputed on the client after a resume. Only server-authored overrides are persisted, so
 * resume never replays old delta packets. The state is safe to share between the server and the
 * client and is written to a versioned NBT schema.</p>
 */
public final class EffectParameterState {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_VALUES = 64;
    public static final EffectParameterState EMPTY = new EffectParameterState(Map.of());

    private final Map<String, EffectCurve.Value> values;

    private EffectParameterState(Map<String, EffectCurve.Value> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EffectParameterState of(String name, EffectCurve.Value value) {
        return builder().put(name, value).build();
    }

    public Map<String, EffectCurve.Value> values() {
        return this.values;
    }

    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    public int size() {
        return this.values.size();
    }

    public boolean has(String name) {
        return this.values.containsKey(Objects.requireNonNull(name, "name"));
    }

    public EffectCurve.@Nullable Value value(String name) {
        return this.values.get(Objects.requireNonNull(name, "name"));
    }

    /** Returns a copy with one value replaced. */
    public EffectParameterState with(String name, EffectCurve.Value value) {
        String checkedName = EffectPhaseAnchor.requireName(name);
        Objects.requireNonNull(value, "value");
        Map<String, EffectCurve.Value> copy = new LinkedHashMap<>(this.values);
        copy.put(checkedName, value);
        if (copy.size() > MAX_VALUES) {
            throw new IllegalArgumentException(
                "An effect parameter state supports at most " + MAX_VALUES + " values"
            );
        }
        return new EffectParameterState(copy);
    }

    /** Returns a copy with one value removed. */
    public EffectParameterState without(String name) {
        Objects.requireNonNull(name, "name");
        if (!this.values.containsKey(name)) {
            return this;
        }
        Map<String, EffectCurve.Value> copy = new LinkedHashMap<>(this.values);
        copy.remove(name);
        return copy.isEmpty() ? EMPTY : new EffectParameterState(copy);
    }

    /** Returns a copy with every value from {@code other} taking precedence. */
    public EffectParameterState merge(EffectParameterState other) {
        Objects.requireNonNull(other, "other");
        if (other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }
        Map<String, EffectCurve.Value> copy = new LinkedHashMap<>(this.values);
        copy.putAll(other.values);
        if (copy.size() > MAX_VALUES) {
            throw new IllegalArgumentException(
                "An effect parameter state supports at most " + MAX_VALUES + " values"
            );
        }
        return new EffectParameterState(copy);
    }

    /** Writes the versioned override state. Empty state is a valid, versioned tag. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", SCHEMA_VERSION);
        ListTag list = new ListTag();
        for (Map.Entry<String, EffectCurve.Value> entry : this.values.entrySet()) {
            CompoundTag valueTag = new CompoundTag();
            EffectCurve.Value value = entry.getValue();
            valueTag.putString("name", entry.getKey());
            valueTag.putByte("type", (byte)value.type().ordinal());
            valueTag.putFloat("x", value.x());
            valueTag.putFloat("y", value.y());
            valueTag.putFloat("z", value.z());
            valueTag.putFloat("w", value.w());
            list.add(valueTag);
        }
        tag.put("values", list);
        return tag;
    }

    /** Reads one versioned override state; an empty tag means no overrides. */
    public static EffectParameterState load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        if (tag.getAllKeys().isEmpty()) {
            return EMPTY;
        }
        int version = tag.contains("version") ? tag.getInt("version") : 0;
        if (version > SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                "Unsupported effect parameter state schema " + version
            );
        }
        ListTag list = tag.getList("values", Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            return EMPTY;
        }
        if (list.size() > MAX_VALUES) {
            throw new IllegalArgumentException(
                "An effect parameter state supports at most " + MAX_VALUES + " values"
            );
        }
        Builder builder = builder();
        EffectCurve.ValueType[] types = EffectCurve.ValueType.values();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag valueTag = list.getCompound(index);
            String name = valueTag.getString("name");
            int typeIndex = valueTag.getByte("type");
            if (typeIndex < 0 || typeIndex >= types.length) {
                throw new IllegalArgumentException("Invalid effect parameter value type: " + typeIndex);
            }
            builder.put(name, new EffectCurve.Value(
                types[typeIndex],
                valueTag.getFloat("x"),
                valueTag.getFloat("y"),
                valueTag.getFloat("z"),
                valueTag.getFloat("w")
            ));
        }
        return builder.build();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof EffectParameterState state
            && this.values.equals(state.values);
    }

    @Override
    public int hashCode() {
        return this.values.hashCode();
    }

    /** Builder that rejects duplicate and unnamed parameters instead of silently dropping them. */
    public static final class Builder {
        private final Map<String, EffectCurve.Value> values = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder put(String name, EffectCurve.Value value) {
            String checkedName = EffectPhaseAnchor.requireName(name);
            Objects.requireNonNull(value, "value");
            if (!this.values.containsKey(checkedName) && this.values.size() >= MAX_VALUES) {
                throw new IllegalArgumentException(
                    "An effect parameter state supports at most " + MAX_VALUES + " values"
                );
            }
            this.values.put(checkedName, value);
            return this;
        }

        public Builder putFloat(String name, float value) {
            return this.put(name, EffectCurve.Value.scalar(value));
        }

        public Builder putVec2(String name, float x, float y) {
            return this.put(name, EffectCurve.Value.vec2(x, y));
        }

        public Builder putVec3(String name, float x, float y, float z) {
            return this.put(name, EffectCurve.Value.vec3(x, y, z));
        }

        public Builder putColor(String name, float red, float green, float blue, float alpha) {
            return this.put(name, EffectCurve.Value.color(red, green, blue, alpha));
        }

        public EffectParameterState build() {
            return this.values.isEmpty() ? EMPTY : new EffectParameterState(this.values);
        }
    }
}
