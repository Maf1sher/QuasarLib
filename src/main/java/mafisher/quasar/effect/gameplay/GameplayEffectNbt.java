package mafisher.quasar.effect.gameplay;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectAudioNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/** NBT helpers that keep a gameplay spec and its consumer state inside an effect descriptor. */
@ApiStatus.Internal
public final class GameplayEffectNbt {
    private static final String SPEC_KEY = "spec";
    private static final String STATE_KEY = "state";

    private GameplayEffectNbt() {
    }

    public static CompoundTag saveDescriptorState(GameplayEffectSpec spec, CompoundTag consumerState) {
        CompoundTag state = new CompoundTag();
        state.put(SPEC_KEY, saveSpec(spec));
        state.put(STATE_KEY, consumerState.copy());
        return state;
    }

    public static CompoundTag saveSpec(GameplayEffectSpec spec) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type_id", spec.typeId().toString());
        if (spec.ownerId() != null) {
            tag.putUUID("owner_id", spec.ownerId());
        }
        tag.putDouble("anchor_x", spec.anchor().x);
        tag.putDouble("anchor_y", spec.anchor().y);
        tag.putDouble("anchor_z", spec.anchor().z);
        tag.putLong("seed", spec.seed());

        ListTag phases = new ListTag();
        for (EffectPhase phase : spec.phases().phases()) {
            CompoundTag phaseTag = new CompoundTag();
            phaseTag.putString("name", phase.name());
            phaseTag.putInt("duration_ticks", phase.durationTicks());
            phases.add(phaseTag);
        }
        tag.put("phases", phases);

        CompoundTag budget = new CompoundTag();
        budget.putInt("max_targets", spec.budget().maxTargetsExamined());
        budget.putInt("max_hitboxes", spec.budget().maxHitboxCalls());
        budget.putInt("max_spawns", spec.budget().maxEntitySpawns());
        budget.putInt("max_block_changes", spec.budget().maxBlockChanges());
        budget.putInt("max_world_samples", spec.budget().maxWorldSamples());
        budget.putInt("max_force_targets", spec.budget().maxForceTargets());
        budget.putInt("max_debris_pieces", spec.budget().maxDebrisPieces());
        budget.putInt("max_section_edits", spec.budget().maxSectionEdits());
        tag.put("budget", budget);
        tag.putString("owner_policy", spec.ownerPolicy().name());
        tag.putString("priority", spec.priority().name());
        tag.putFloat("scale", spec.scale());
        if (!spec.audio().isEmpty()) {
            tag.put("audio", EffectAudioNbt.save(spec.audio()));
        }
        return tag;
    }

    public static GameplayEffectSpec loadSpec(EffectDescriptor descriptor) {
        CompoundTag state = descriptor.state();
        CompoundTag tag = state.getCompound(SPEC_KEY);
        if (tag.isEmpty()) {
            throw new IllegalArgumentException("Gameplay descriptor is missing its spec state");
        }
        ListTag phasesTag = tag.getList("phases", Tag.TAG_COMPOUND);
        List<EffectPhase> phases = new ArrayList<>(phasesTag.size());
        for (int index = 0; index < phasesTag.size(); index++) {
            CompoundTag phaseTag = phasesTag.getCompound(index);
            phases.add(new EffectPhase(phaseTag.getString("name"), phaseTag.getInt("duration_ticks")));
        }
        CompoundTag budgetTag = tag.getCompound("budget");
        EffectAudioSpec audio = tag.contains("audio", Tag.TAG_COMPOUND)
            ? EffectAudioNbt.load(tag.getCompound("audio"))
            : EffectAudioSpec.NONE;
        return new GameplayEffectSpec(
            descriptor.typeId(),
            tag.contains("owner_id") ? tag.getUUID("owner_id") : null,
            new Vec3(tag.getDouble("anchor_x"), tag.getDouble("anchor_y"), tag.getDouble("anchor_z")),
            EffectPhasePlan.of(phases),
            descriptor.seed(),
            new EffectTickBudget(
                budgetTag.getInt("max_targets"),
                budgetTag.getInt("max_hitboxes"),
                budgetTag.getInt("max_spawns"),
                budgetTag.getInt("max_block_changes"),
                budgetTag.getInt("max_world_samples"),
                budgetTag.getInt("max_force_targets"),
                budgetTag.getInt("max_debris_pieces"),
                budgetTag.getInt("max_section_edits")
            ),
            OwnerPolicy.valueOf(tag.getString("owner_policy")),
            audio,
            EffectPriority.byName(
                tag.getString("priority"),
                EffectPriority.NORMAL,
                "GAMEPLAY effect " + descriptor.instanceId()
            ),
            tag.contains("scale", Tag.TAG_FLOAT) ? tag.getFloat("scale") : descriptor.scale()
        );
    }

    public static CompoundTag loadConsumerState(EffectDescriptor descriptor) {
        return descriptor.state().getCompound(STATE_KEY).copy();
    }

    public static UUID ownerId(EffectDescriptor descriptor) {
        CompoundTag tag = descriptor.state().getCompound(SPEC_KEY);
        return tag.contains("owner_id") ? tag.getUUID("owner_id") : null;
    }
}
