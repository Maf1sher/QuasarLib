package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.VisualEffectHandle;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.effect.persistence.EffectAudioNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VisualEffectAudioTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "audio_visual");
    private static final ResourceLocation SOUND = ResourceLocation.withDefaultNamespace("test");

    private ServerLevel level;
    private long gameTime;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
    }

    @AfterEach
    void tearDown() {
        VisualEffectService.clear(this.level);
        EffectAudioService.clear(this.level);
    }

    @Test
    void startsAndStopsAudioWithTheVisual() {
        VisualEffectHandle handle = VisualEffectService.start(this.level, spec(100)).handle();

        assertEquals(1, EffectAudioService.activeCount(this.level));

        VisualEffectService.stop(this.level, handle);

        assertEquals(0, EffectAudioService.activeCount(this.level));
    }

    @Test
    void rejectsAudioCuesOutsideTheVisualDuration() {
        EffectAudioSpec audio = EffectAudioSpec.builder(5)
            .cue(EffectAudioCue.at(4, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F))
            .build();

        assertThrows(IllegalArgumentException.class, () -> VisualEffectSpec.builder(EFFECT_ID, Vec3.ZERO, 4, 7L, EffectAudience.dimensionWide())
            .rotation(0.0F, 0.0F, 0.0F)
            .scale(1.0F)
            .audio(audio)
            .build());
    }

    @Test
    void stopsAudioWhenTheVisualExpires() {
        VisualEffectService.start(this.level, spec(5));

        this.gameTime = 5;
        VisualEffectService.tick(this.level);

        assertEquals(0, EffectAudioService.activeCount(this.level));
    }

    @Test
    void restoresAudioWithTheVisual() {
        UUID instanceId = UUID.randomUUID();
        VisualEffectSpec spec = spec(100);
        CompoundTag state = new CompoundTag();
        EffectAudioNbt.saveInto(state, spec.audio());
        EffectDescriptor descriptor = EffectDescriptor.builder(instanceId, EffectKind.VISUAL, EFFECT_ID, ResourceLocation.withDefaultNamespace("overworld"))
            .placement(spec.transform(), spec.scale())
            .timing(spec.seed(), 0L, spec.durationTicks())
            .audience(spec.audience())
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(state)
            .build();

        VisualEffectService.restore(this.level, spec, instanceId, 0L, descriptor);

        assertEquals(1, EffectAudioService.activeCount(this.level));
    }

    private static VisualEffectSpec spec(int durationTicks) {
        EffectAudioSpec audio = EffectAudioSpec.builder(durationTicks)
            .cue(EffectAudioCue.at(0, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F,
                EffectAudioAttenuation.distance(64.0F)))
            .build();
        return VisualEffectSpec.builder(EFFECT_ID, Vec3.ZERO, durationTicks, 7L, EffectAudience.dimensionWide())
            .rotation(0.0F, 0.0F, 0.0F)
            .scale(1.0F)
            .audio(audio)
            .build();
    }
}
