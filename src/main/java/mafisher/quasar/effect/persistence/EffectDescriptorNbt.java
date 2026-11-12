package mafisher.quasar.effect.persistence;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Stable NBT representation shared by save data and persistence tests. */
@ApiStatus.Internal
public final class EffectDescriptorNbt {
    private EffectDescriptorNbt() {
    }

    public static CompoundTag save(EffectDescriptor descriptor) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("instance_id", descriptor.instanceId());
        tag.putString("kind", descriptor.kind().name());
        tag.putString("type_id", descriptor.typeId().toString());
        tag.putString("dimension_id", descriptor.dimensionId().toString());
        tag.put("transform", saveTransform(descriptor.transform()));
        tag.putFloat("scale", descriptor.scale());
        tag.putLong("seed", descriptor.seed());
        tag.putLong("start_game_time", descriptor.startGameTime());
        tag.putInt("duration_ticks", descriptor.durationTicks());
        tag.put("audience", saveAudience(descriptor.audience()));
        tag.put("capabilities", saveCapabilities(descriptor.capabilities()));
        tag.putString("persistence", descriptor.persistence().name());
        if (descriptor.ownerSessionId() != null) {
            tag.putUUID("owner_session", descriptor.ownerSessionId());
        }
        tag.put("state", descriptor.stateView().copy());
        return tag;
    }

    public static EffectDescriptor load(CompoundTag tag) {
        try {
            UUID instanceId = tag.getUUID("instance_id");
            EffectKind kind = EffectKind.valueOf(tag.getString("kind"));
            ResourceLocation typeId = resourceLocation(tag.getString("type_id"), "type_id");
            ResourceLocation dimensionId = resourceLocation(tag.getString("dimension_id"), "dimension_id");
            EffectTransform transform = loadTransform(tag.getCompound("transform"));
            UUID ownerSession = tag.contains("owner_session") ? tag.getUUID("owner_session") : null;
            return EffectDescriptor.builder(instanceId, kind, typeId, dimensionId)
                .placement(transform, tag.getFloat("scale"))
                .timing(
                    tag.getLong("seed"),
                    tag.getLong("start_game_time"),
                    tag.getInt("duration_ticks")
                )
                .audience(loadAudience(tag.getCompound("audience")))
                .capabilities(loadCapabilities(tag.getCompound("capabilities")))
                .persistence(EffectPersistence.valueOf(tag.getString("persistence")))
                .ownerSessionId(ownerSession)
                .state(tag.getCompound("state"))
                .build();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid effect descriptor", exception);
        }
    }

    private static CompoundTag saveTransform(EffectTransform transform) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", transform.position().x());
        tag.putDouble("y", transform.position().y());
        tag.putDouble("z", transform.position().z());
        tag.putFloat("yaw", transform.yaw());
        tag.putFloat("pitch", transform.pitch());
        tag.putFloat("roll", transform.roll());
        return tag;
    }

    private static EffectTransform loadTransform(CompoundTag tag) {
        return new EffectTransform(
            new EffectPoint(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")),
            tag.getFloat("yaw"),
            tag.getFloat("pitch"),
            tag.getFloat("roll")
        );
    }

    private static CompoundTag saveAudience(EffectAudience audience) {
        CompoundTag tag = new CompoundTag();
        if (audience instanceof EffectAudience.Nearby nearby) {
            tag.putString("type", "nearby");
            tag.putDouble("radius", nearby.radius());
        } else {
            tag.putString("type", "dimension_wide");
        }
        return tag;
    }

    private static EffectAudience loadAudience(CompoundTag tag) {
        return switch (tag.getString("type")) {
            case "nearby" -> EffectAudience.nearby(tag.getDouble("radius"));
            case "dimension_wide" -> EffectAudience.dimensionWide();
            default -> throw new IllegalArgumentException("Unknown effect audience type");
        };
    }

    private static CompoundTag saveCapabilities(EffectCapabilities capabilities) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("destructive", capabilities.destructive());
        ListTag custom = new ListTag();
        for (ResourceLocation id : capabilities.custom()) {
            custom.add(StringTag.valueOf(id.toString()));
        }
        tag.put("custom", custom);
        return tag;
    }

    private static EffectCapabilities loadCapabilities(CompoundTag tag) {
        ListTag custom = tag.getList("custom", Tag.TAG_STRING);
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (int index = 0; index < custom.size(); index++) {
            ids.add(resourceLocation(custom.getString(index), "capability"));
        }
        return new EffectCapabilities(tag.getBoolean("destructive"), ids);
    }

    private static ResourceLocation resourceLocation(String value, String field) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid resource location in effect descriptor field " + field);
        }
        return id;
    }
}
