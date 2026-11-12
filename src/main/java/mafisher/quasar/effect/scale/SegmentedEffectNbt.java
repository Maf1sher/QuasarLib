package mafisher.quasar.effect.scale;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.effect.EffectPriority;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import mafisher.quasar.effect.persistence.EffectAudioNbt;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/**
 * NBT codec for one segmented coordinator's descriptor state. The service writes with
 * {@link #saveInto} and restore reads with {@link #load}, so the persistence keys stay in one
 * place instead of being duplicated between the runtime and restore paths.
 */
public final class SegmentedEffectNbt {
    private SegmentedEffectNbt() {
    }

    /** Writes the segmented layout, baked heights, segment identities, priority, and audio state. */
    public static void saveInto(
        CompoundTag state,
        SegmentedVisualEffectSpec spec,
        SegmentHeightProfile heights,
        List<UUID> segmentIds
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(heights, "heights");
        Objects.requireNonNull(segmentIds, "segmentIds");
        state.putInt("columns", spec.columns());
        state.putInt("rows", spec.rows());
        state.putInt("layers", spec.layers());
        state.putDouble("spacing", spec.spacing());
        state.putDouble("layer_spacing", spec.layerSpacing());
        state.putFloat("segment_scale", spec.segmentScale());
        state.putString("terrain_source", spec.terrain().source().name());
        state.putDouble("terrain_offset", spec.terrain().verticalOffset());
        saveHeights(state, heights);
        ListTag ids = new ListTag();
        for (UUID segmentId : segmentIds) {
            ids.add(StringTag.valueOf(segmentId.toString()));
        }
        state.put("segment_ids", ids);
        state.putString("priority", spec.priority().name());
        EffectAudioNbt.saveInto(state, spec.audio());
    }

    /** Rebuilds the segmented spec and its persisted segment identities from a descriptor. */
    public static Loaded load(EffectDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        CompoundTag state = descriptor.state();
        int layers = Math.max(1, state.getInt("layers"));
        EffectAudioSpec audio = EffectAudioNbt.loadFrom(state);
        SegmentTerrainConform terrain = new SegmentTerrainConform(
            SegmentHeightSource.byName(state.getString("terrain_source"), SegmentHeightSource.NONE),
            state.getDouble("terrain_offset")
        );
        SegmentedVisualEffectSpec spec = new SegmentedVisualEffectSpec(
            descriptor.typeId(),
            transformPosition(descriptor),
            descriptor.transform().yaw(),
            descriptor.transform().pitch(),
            descriptor.transform().roll(),
            descriptor.scale(),
            state.getInt("columns"),
            state.getInt("rows"),
            state.getDouble("spacing"),
            descriptor.durationTicks(),
            descriptor.seed(),
            descriptor.audience(),
            audio,
            priority(state, descriptor),
            layers,
            state.getDouble("layer_spacing"),
            terrain
        );
        return new Loaded(spec, segmentIds(state, descriptor, spec.segmentCount()));
    }

    /** Baked heights stored in descriptor state, or {@link SegmentHeightProfile#NONE}. */
    static SegmentHeightProfile readHeights(CompoundTag state) {
        int columns = state.getInt("columns");
        int rows = state.getInt("rows");
        if (columns < 1 || rows < 1 || !state.contains("column_heights", Tag.TAG_LONG_ARRAY)) {
            return SegmentHeightProfile.NONE;
        }
        long[] bits = state.getLongArray("column_heights");
        if (bits.length != columns * rows) {
            return SegmentHeightProfile.NONE;
        }
        double[] values = new double[bits.length];
        for (int index = 0; index < bits.length; index++) {
            values[index] = Double.longBitsToDouble(bits[index]);
        }
        return SegmentHeightProfile.of(columns, rows, values);
    }

    private static void saveHeights(CompoundTag state, SegmentHeightProfile heights) {
        if (heights.isEmpty()) {
            state.remove("column_heights");
            return;
        }
        double[] values = heights.values();
        long[] bits = new long[values.length];
        for (int index = 0; index < values.length; index++) {
            bits[index] = Double.doubleToRawLongBits(values[index]);
        }
        state.putLongArray("column_heights", bits);
    }

    private static List<UUID> segmentIds(CompoundTag state, EffectDescriptor descriptor, int count) {
        if (!state.contains("segment_ids")) {
            return SegmentedVisualEffectService.restoreSegmentIds(descriptor.instanceId(), count);
        }
        ListTag stored = state.getList("segment_ids", Tag.TAG_STRING);
        if (stored.size() != count) {
            throw new IllegalArgumentException(
                "Effect " + descriptor.instanceId() + " expected " + count
                    + " segment IDs but found " + stored.size()
            );
        }
        List<UUID> ids = new ArrayList<>(stored.size());
        for (int index = 0; index < stored.size(); index++) {
            try {
                ids.add(UUID.fromString(stored.getString(index)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                    "Invalid segment ID at index " + index + " for effect " + descriptor.instanceId(),
                    exception
                );
            }
        }
        return List.copyOf(ids);
    }

    private static EffectPriority priority(CompoundTag state, EffectDescriptor descriptor) {
        String context = descriptor.kind().name() + " effect " + descriptor.instanceId();
        return EffectPriority.byName(state.getString("priority"), EffectPriority.NORMAL, context);
    }

    private static Vec3 transformPosition(EffectDescriptor descriptor) {
        var position = descriptor.transform().position();
        return new Vec3(position.x(), position.y(), position.z());
    }

    /** Rebuilt segmented spec plus its persisted segment identities. */
    public record Loaded(SegmentedVisualEffectSpec spec, List<UUID> segmentIds) {
        public Loaded {
            Objects.requireNonNull(spec, "spec");
            segmentIds = List.copyOf(segmentIds);
        }
    }
}
