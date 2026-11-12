package mafisher.quasar.effect.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectPriority;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.api.scale.SegmentHeightProfile;
import mafisher.quasar.api.scale.SegmentTerrainConform;
import mafisher.quasar.api.scale.SegmentedVisualEffectService;
import mafisher.quasar.api.scale.SegmentedVisualEffectSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SegmentedEffectNbtTest {
    private static final ResourceLocation EFFECT_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "segmented_nbt");
    private static final UUID COORDINATOR_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000039");

    @Test
    void roundTripsLayoutHeightsPrioritiesAndAudio() {
        SegmentedVisualEffectSpec spec = spec();
        SegmentHeightProfile heights = SegmentHeightProfile.of(
            2, 2, new double[]{64.0, 65.0, 66.0, 67.0}
        );
        List<UUID> segmentIds = SegmentedVisualEffectService.restoreSegmentIds(
            COORDINATOR_ID, spec.segmentCount()
        );
        CompoundTag state = new CompoundTag();

        SegmentedEffectNbt.saveInto(state, spec, heights, segmentIds);

        assertTrue(state.contains("segment_ids"));
        assertTrue(state.contains("column_heights"));
        EffectDescriptor descriptor = descriptor(state);
        SegmentedEffectNbt.Loaded loaded = SegmentedEffectNbt.load(descriptor);
        assertEquals(spec.columns(), loaded.spec().columns());
        assertEquals(spec.rows(), loaded.spec().rows());
        assertEquals(spec.layers(), loaded.spec().layers());
        assertEquals(spec.spacing(), loaded.spec().spacing());
        assertEquals(spec.layerSpacing(), loaded.spec().layerSpacing());
        assertEquals(spec.priority(), loaded.spec().priority());
        assertEquals(1, loaded.spec().audio().cueCount());
        assertEquals(segmentIds, loaded.segmentIds());
        assertEquals(65.0, SegmentedEffectNbt.readHeights(state).y(1, 0));
    }

    @Test
    void absentHeightsLoadAsNone() {
        CompoundTag state = new CompoundTag();
        state.putInt("columns", 2);
        state.putInt("rows", 2);

        assertTrue(SegmentedEffectNbt.readHeights(state).isEmpty());
    }

    private static SegmentedVisualEffectSpec spec() {
        EffectAudioSpec audio = EffectAudioSpec.builder(200)
            .cue(EffectAudioCue.at(0, Vec3.ZERO, ResourceLocation.withDefaultNamespace("test"),
                net.minecraft.sounds.SoundSource.MASTER, 1.0F, 1.0F))
            .build();
        return SegmentedVisualEffectSpec.builder(EFFECT_ID, new Vec3(0.0, 64.0, 0.0), 200, 42L, EffectAudience.dimensionWide())
            .rotation(0.0F, 0.0F, 0.0F)
            .segmentScale(4.0F)
            .grid(2, 2)
            .spacing(8.0)
            .layers(2, 4.0)
            .audio(audio)
            .priority(EffectPriority.HIGH)
            .terrain(SegmentTerrainConform.NONE)
            .build();
    }

    private static EffectDescriptor descriptor(CompoundTag state) {
        SegmentedVisualEffectSpec spec = spec();
        return EffectDescriptor.builder(COORDINATOR_ID, EffectKind.SEGMENTED, EFFECT_ID, Level.OVERWORLD.location())
            .placement(new EffectTransform(
                new EffectPoint(spec.center().x, spec.center().y, spec.center().z),
                spec.yaw(),
                spec.pitch(),
                spec.roll()
            ), spec.segmentScale())
            .timing(spec.seed(), 0L, spec.durationTicks())
            .audience(spec.audience())
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(state)
            .build();
    }
}
