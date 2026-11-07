package mafisher.quasar.effect.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectPriority;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioService;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import mafisher.quasar.effect.network.StartSegmentedVisualEffectPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SegmentedVisualEffectServiceTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");

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
        SegmentedVisualEffectService.stopAll(this.level);
        VisualEffectService.clear(this.level);
        EffectAudioService.clear(this.level);
    }

    @Test
    void startsAWholeGridWithoutStandaloneSlots() {
        SegmentedVisualEffectHandle handle = SegmentedVisualEffectService.start(this.level, spec(2, 2, 200));

        assertEquals(1, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(4, SegmentedVisualEffectService.activeSegmentCount(this.level));
        assertEquals(0, VisualEffectService.activeCount(this.level));

        SegmentedVisualEffectService.stop(this.level, handle);

        assertEquals(0, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(0, SegmentedVisualEffectService.activeSegmentCount(this.level));
    }

    @Test
    void aLargeGridStartsWithEveryStandaloneSlotFull() {
        for (int index = 0; index < VisualEffectService.MAX_ACTIVE_EFFECTS_PER_LEVEL; index++) {
            VisualEffectService.start(this.level, visualSpec());
        }

        SegmentedVisualEffectService.start(this.level, spec(16, 8, 200));

        assertEquals(VisualEffectService.MAX_ACTIVE_EFFECTS_PER_LEVEL,
            VisualEffectService.activeCount(this.level));
        assertEquals(128, SegmentedVisualEffectService.activeSegmentCount(this.level));
    }

    @Test
    void standaloneStartsNeverEvictSegmentedCells() {
        SegmentedVisualEffectService.start(this.level, spec(2, 2, 200));
        for (int index = 0; index < VisualEffectService.MAX_ACTIVE_EFFECTS_PER_LEVEL; index++) {
            VisualEffectService.start(this.level, visualSpec());
        }

        VisualEffectService.start(this.level, visualSpec());

        assertEquals(VisualEffectService.MAX_ACTIVE_EFFECTS_PER_LEVEL,
            VisualEffectService.activeCount(this.level));
        assertEquals(4, SegmentedVisualEffectService.activeSegmentCount(this.level));
        assertEquals(1, SegmentedVisualEffectService.activeCount(this.level));
    }

    @Test
    void cullsTheOldestCoordinatorBeyondTheDedicatedSegmentBudget() {
        int fullGrids = SegmentedVisualEffectService.MAX_ACTIVE_SEGMENTS_PER_LEVEL / 128;
        SegmentedVisualEffectHandle oldest = null;
        for (int index = 0; index < fullGrids; index++) {
            SegmentedVisualEffectHandle handle = SegmentedVisualEffectService.start(
                this.level, spec(16, 8, 200)
            );
            if (oldest == null) {
                oldest = handle;
            }
        }

        SegmentedVisualEffectHandle admitted = SegmentedVisualEffectService.start(
            this.level, spec(1, 1, 200)
        );

        assertTrue(SegmentedVisualEffectService.isActive(this.level, admitted));
        assertFalse(SegmentedVisualEffectService.isActive(this.level, oldest));
        assertEquals(fullGrids, SegmentedVisualEffectService.activeCount(this.level));
        assertTrue(SegmentedVisualEffectService.activeSegmentCount(this.level)
            <= SegmentedVisualEffectService.MAX_ACTIVE_SEGMENTS_PER_LEVEL);
    }

    @Test
    void cullsABackgroundCoordinatorBeforeANormalOne() {
        int fullGrids = SegmentedVisualEffectService.MAX_ACTIVE_SEGMENTS_PER_LEVEL / 128;
        SegmentedVisualEffectHandle background = SegmentedVisualEffectService.start(
            this.level, spec(16, 8, 200, EffectPriority.BACKGROUND)
        );
        for (int index = 1; index < fullGrids; index++) {
            SegmentedVisualEffectService.start(this.level, spec(16, 8, 200));
        }

        SegmentedVisualEffectHandle high = SegmentedVisualEffectService.start(
            this.level, spec(16, 8, 200, EffectPriority.HIGH)
        );

        assertTrue(SegmentedVisualEffectService.isActive(this.level, high));
        assertFalse(SegmentedVisualEffectService.isActive(this.level, background));
        assertEquals(fullGrids, SegmentedVisualEffectService.activeCount(this.level));
    }

    @Test
    void expiresAndReleasesEverySegment() {
        SegmentedVisualEffectService.start(this.level, spec(2, 2, 5));

        SegmentedVisualEffectService.tick(this.level);
        assertEquals(1, SegmentedVisualEffectService.activeCount(this.level));

        this.gameTime = 5;
        SegmentedVisualEffectService.tick(this.level);

        assertEquals(0, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(0, SegmentedVisualEffectService.activeSegmentCount(this.level));
    }

    @Test
    void updatesAndStopsAreIdempotent() {
        SegmentedVisualEffectHandle handle = SegmentedVisualEffectService.start(this.level, spec(2, 1, 200));
        EffectTransform transform = new EffectTransform(new EffectPoint(10.0, 70.0, -10.0), 45.0F, 0.0F, 0.0F);

        SegmentedVisualEffectService.update(this.level, handle, transform);
        SegmentedVisualEffectService.stop(this.level, handle);
        SegmentedVisualEffectService.stop(this.level, handle);
        SegmentedVisualEffectService.update(this.level, handle, transform);

        assertEquals(0, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(0, SegmentedVisualEffectService.activeSegmentCount(this.level));
    }

    @Test
    void stopAllReleasesEveryCoordinator() {
        SegmentedVisualEffectService.start(this.level, spec(2, 2, 200));
        SegmentedVisualEffectService.start(this.level, spec(1, 1, 200));

        assertEquals(2, SegmentedVisualEffectService.stopAll(this.level));

        assertEquals(0, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(0, SegmentedVisualEffectService.activeSegmentCount(this.level));
    }

    @Test
    void startsOneAudioPlanForTheCoordinator() {
        SegmentedVisualEffectHandle handle = SegmentedVisualEffectService.start(
            this.level, specWithAudio(2, 2, 200)
        );

        assertEquals(1, EffectAudioService.activeCount(this.level));

        SegmentedVisualEffectService.stop(this.level, handle);

        assertEquals(0, EffectAudioService.activeCount(this.level));
    }

    @Test
    void aSingleCellIsAllowed() {
        SegmentedVisualEffectHandle handle = SegmentedVisualEffectService.start(this.level, spec(1, 1, 200));

        assertEquals(1, SegmentedVisualEffectService.activeSegmentCount(this.level));
        assertEquals(0, VisualEffectService.activeCount(this.level));

        SegmentedVisualEffectService.stop(this.level, handle);
        assertEquals(0, SegmentedVisualEffectService.activeSegmentCount(this.level));
    }

    @Test
    void resumeIncludesTheCurrentBaseAndMarksTracking() {
        SegmentedVisualEffectHandle handle = SegmentedVisualEffectService.start(this.level, spec(2, 2, 200));
        EffectTransform moved = new EffectTransform(
            new EffectPoint(10.0, 70.0, -10.0), 45.0F, 12.0F, 3.0F
        );
        SegmentedVisualEffectService.update(this.level, handle, moved);
        ServerPlayer player = player(new Vec3(10.0, 70.0, -10.0));
        when(this.level.players()).thenReturn(List.of(player));
        this.gameTime = 50L;

        List<StartSegmentedVisualEffectPayload> payloads =
            SegmentedVisualEffectService.resumePayloads(this.level, player);

        assertEquals(1, payloads.size());
        StartSegmentedVisualEffectPayload payload = payloads.getFirst();
        assertEquals(10.0, payload.x());
        assertEquals(70.0, payload.y());
        assertEquals(-10.0, payload.z());
        assertEquals(45.0F, payload.yaw());
        assertEquals(12.0F, payload.pitch());
        assertEquals(3.0F, payload.roll());
        assertEquals(50L, payload.serverGameTime());
        assertEquals(4, payload.segmentCount());
    }

    private static SegmentedVisualEffectSpec spec(int columns, int rows, int durationTicks) {
        return spec(columns, rows, durationTicks, EffectPriority.NORMAL);
    }

    private static SegmentedVisualEffectSpec spec(
        int columns,
        int rows,
        int durationTicks,
        EffectPriority priority
    ) {
        return new SegmentedVisualEffectSpec(
            EFFECT_ID,
            new Vec3(0.0, 64.0, 0.0),
            0.0F,
            0.0F,
            0.0F,
            4.0F,
            columns,
            rows,
            columns * rows > 1 ? 8.0 : 0.0,
            durationTicks,
            42L,
            EffectAudience.dimensionWide(),
            EffectAudioSpec.NONE,
            priority
        );
    }

    private static VisualEffectSpec visualSpec() {
        return new VisualEffectSpec(
            EFFECT_ID,
            new Vec3(0.0, 64.0, 0.0),
            0.0F,
            0.0F,
            0.0F,
            1.0F,
            200,
            42L,
            EffectAudience.dimensionWide()
        );
    }

    private static SegmentedVisualEffectSpec specWithAudio(int columns, int rows, int durationTicks) {
        EffectAudioSpec audio = EffectAudioSpec.builder(durationTicks)
            .cue(
                0,
                new Vec3(0.0, 64.0, 0.0),
                ResourceLocation.withDefaultNamespace("test"),
                SoundSource.MASTER,
                1.0F,
                1.0F,
                EffectAudioAttenuation.distance(64.0F)
            )
            .build();
        return new SegmentedVisualEffectSpec(
            EFFECT_ID,
            new Vec3(0.0, 64.0, 0.0),
            0.0F,
            0.0F,
            0.0F,
            4.0F,
            columns,
            rows,
            columns * rows > 1 ? 8.0 : 0.0,
            durationTicks,
            42L,
            EffectAudience.dimensionWide(),
            audio
        );
    }

    private ServerPlayer player(Vec3 position) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        when(player.level()).thenReturn(this.level);
        when(player.position()).thenReturn(position);
        when(player.getX()).thenReturn(position.x);
        when(player.getY()).thenReturn(position.y);
        when(player.getZ()).thenReturn(position.z);
        return player;
    }
}
