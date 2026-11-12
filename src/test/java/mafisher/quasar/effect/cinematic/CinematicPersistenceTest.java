package mafisher.quasar.effect.cinematic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.cinematic.CinematicAudioMode;
import mafisher.quasar.api.cinematic.CinematicEffectService;
import mafisher.quasar.api.cinematic.CinematicTimeline;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CinematicPersistenceTest {
    private static final ResourceLocation TIMELINE_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "persisted");
    private static final ResourceLocation VISUAL_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "visual");
    private static final SoundEvent SOUND = SoundEvent.createVariableRangeEvent(
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "test_sound")
    );
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000020");

    private ServerLevel level;
    private long gameTime;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
        this.gameTime = 50L;
    }

    @AfterEach
    void tearDown() {
        CinematicEffectService.stopAll(this.level);
        VisualEffectService.clear(this.level);
        EffectAudioService.clear(this.level);
    }

    @Test
    void fastForwardsWithoutReplayingPastSounds() {
        CinematicTimeline timeline = CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
            .sound(0, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F)
            .visual("first", 10, visualSpec(80))
            .visual("second", 40, visualSpec(20))
            .sound(60, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F)
            .build();

        CinematicEffectService.restore(this.level, timeline, SESSION, 0L, descriptor());

        assertEquals(2, VisualEffectService.activeCount(this.level));
        verify(this.level, never()).playSound(any(), anyDouble(), anyDouble(), anyDouble(),
            eq(SOUND), eq(SoundSource.MASTER), anyFloat(), anyFloat());

        this.gameTime = 60L;
        CinematicEffectService.tick(this.level);

        verify(this.level, times(1)).playSound(any(), anyDouble(), anyDouble(), anyDouble(),
            eq(SOUND), eq(SoundSource.MASTER), eq(1.0F), eq(1.0F));
    }

    @Test
    void dropsVisualsWhoseStartAndDurationAlreadyPassed() {
        CinematicTimeline timeline = CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
            .visual("short", 10, visualSpec(10))
            .build();

        CinematicEffectService.restore(this.level, timeline, SESSION, 0L, descriptor());

        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    @Test
    void restoresClientAudioPlanForLateJoiners() {
        CinematicTimeline timeline = CinematicTimeline.builder(TIMELINE_ID, 100, 1L)
            .audioMode(CinematicAudioMode.CLIENT)
            .sound(0, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F)
            .sound(60, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F)
            .build();

        CinematicEffectService.restore(this.level, timeline, SESSION, 0L, descriptor());

        assertEquals(1, EffectAudioService.activeCount(this.level));
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(this.level);
        when(player.position()).thenReturn(Vec3.ZERO);
        when(this.level.players()).thenReturn(List.of(player));

        this.gameTime = 50L;
        List<EffectAudioPlan> plans = EffectAudioService.resumePlans(this.level, player);

        assertEquals(1, plans.size());
        assertEquals(1, plans.getFirst().firstCueIndex(50, 2));
    }

    private static VisualEffectSpec visualSpec(int durationTicks) {
        return VisualEffectSpec.builder(VISUAL_ID, Vec3.ZERO, durationTicks, 2L, EffectAudience.nearby(64.0))
            .rotation(0.0F, 0.0F, 0.0F)
            .scale(1.0F)
            .build();
    }

    private static EffectDescriptor descriptor() {
        return EffectDescriptor.builder(SESSION, EffectKind.CINEMATIC, TIMELINE_ID, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
            .placement(new EffectTransform(new EffectPoint(0.0, 0.0, 0.0), 0.0F, 0.0F, 0.0F), 1.0F)
            .timing(1L, 0L, 100)
            .audience(EffectAudience.dimensionWide())
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(new CompoundTag())
            .build();
    }
}
