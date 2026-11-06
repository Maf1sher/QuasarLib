package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioService;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import mafisher.quasar.effect.persistence.EffectAudioNbt;
import mafisher.quasar.effect.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.EffectKind;
import mafisher.quasar.effect.persistence.EffectPersistence;
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
        VisualEffectHandle handle = VisualEffectService.start(this.level, spec(100));

        assertEquals(1, EffectAudioService.activeCount(this.level));

        VisualEffectService.stop(this.level, handle);

        assertEquals(0, EffectAudioService.activeCount(this.level));
    }

    @Test
    void rejectsAudioCuesOutsideTheVisualDuration() {
        EffectAudioSpec audio = EffectAudioSpec.builder(5)
            .cue(4, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F)
            .build();

        assertThrows(IllegalArgumentException.class, () -> new VisualEffectSpec(
            EFFECT_ID,
            Vec3.ZERO,
            0.0F,
            0.0F,
            0.0F,
            1.0F,
            4,
            7L,
            EffectAudience.dimensionWide(),
            audio
        ));
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
        EffectDescriptor descriptor = new EffectDescriptor(
            instanceId,
            EffectKind.VISUAL,
            EFFECT_ID,
            ResourceLocation.withDefaultNamespace("overworld"),
            spec.transform(),
            spec.scale(),
            spec.seed(),
            0L,
            spec.durationTicks(),
            spec.audience(),
            EffectCapabilities.NONE,
            EffectPersistence.RESUMABLE,
            null,
            state
        );

        VisualEffectService.restore(this.level, spec, instanceId, 0L, descriptor);

        assertEquals(1, EffectAudioService.activeCount(this.level));
    }

    private static VisualEffectSpec spec(int durationTicks) {
        EffectAudioSpec audio = EffectAudioSpec.builder(durationTicks)
            .cue(0, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F,
                EffectAudioAttenuation.distance(64.0F))
            .build();
        return new VisualEffectSpec(
            EFFECT_ID,
            Vec3.ZERO,
            0.0F,
            0.0F,
            0.0F,
            1.0F,
            durationTicks,
            7L,
            EffectAudience.dimensionWide(),
            audio
        );
    }
}
