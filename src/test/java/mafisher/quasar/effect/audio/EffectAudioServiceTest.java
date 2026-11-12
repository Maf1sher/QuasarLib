package mafisher.quasar.effect.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectBudgetReport;
import mafisher.quasar.effect.EffectBudgetService;
import mafisher.quasar.effect.EffectPriority;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class EffectAudioServiceTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");
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
        EffectAudioService.clear(this.level);
        EffectBudgetService.clear(this.level);
    }

    @Test
    void startsAndStopsAPlan() {
        UUID effectId = UUID.randomUUID();

        EffectAudioService.start(this.level, plan(effectId, 100, 10), EffectAudience.dimensionWide());
        assertEquals(1, EffectAudioService.activeCount(this.level));
        assertNotNull(EffectAudioService.planOrNull(this.level, effectId));

        EffectAudioService.stop(this.level, effectId);

        assertEquals(0, EffectAudioService.activeCount(this.level));
        assertNull(EffectAudioService.planOrNull(this.level, effectId));
    }

    @Test
    void replacesAPlanForTheSameEffect() {
        UUID effectId = UUID.randomUUID();
        EffectAudioService.start(this.level, plan(effectId, 100, 10), EffectAudience.dimensionWide());
        EffectAudioService.start(this.level, plan(effectId, 200, 20), EffectAudience.dimensionWide());

        assertEquals(1, EffectAudioService.activeCount(this.level));
        assertEquals(200, EffectAudioService.planOrNull(this.level, effectId).durationTicks());
    }

    @Test
    void expiresPlans() {
        UUID effectId = UUID.randomUUID();
        EffectAudioService.start(this.level, plan(effectId, 10, 0), EffectAudience.dimensionWide());

        this.gameTime = 10;
        EffectAudioService.tick(this.level);

        assertEquals(0, EffectAudioService.activeCount(this.level));
    }

    @Test
    void evictsTheOldestPlanAtTheCap() {
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < EffectAudioService.MAX_ACTIVE_PLANS_PER_LEVEL + 1; index++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            EffectAudioService.start(this.level, plan(id, 100, 0), EffectAudience.dimensionWide());
        }

        assertEquals(EffectAudioService.MAX_ACTIVE_PLANS_PER_LEVEL, EffectAudioService.activeCount(this.level));
        assertNull(EffectAudioService.planOrNull(this.level, ids.getFirst()));
        assertNotNull(EffectAudioService.planOrNull(this.level, ids.getLast()));
    }

    @Test
    void evictsTheLowestPriorityPlanBeforeOlderNormalPlans() {
        UUID oldest = UUID.randomUUID();
        UUID background = UUID.randomUUID();
        EffectAudioService.start(
            this.level, plan(oldest, 100, 0), EffectAudience.dimensionWide(), EffectPriority.NORMAL
        );
        EffectAudioService.start(
            this.level, plan(background, 100, 0), EffectAudience.dimensionWide(), EffectPriority.BACKGROUND
        );
        for (int index = 2; index < EffectAudioService.MAX_ACTIVE_PLANS_PER_LEVEL; index++) {
            EffectAudioService.start(
                this.level, plan(UUID.randomUUID(), 100, 0), EffectAudience.dimensionWide(), EffectPriority.NORMAL
            );
        }

        UUID critical = UUID.randomUUID();
        EffectAudioService.start(
            this.level, plan(critical, 100, 0), EffectAudience.dimensionWide(), EffectPriority.CRITICAL
        );

        assertEquals(EffectAudioService.MAX_ACTIVE_PLANS_PER_LEVEL, EffectAudioService.activeCount(this.level));
        assertNull(EffectAudioService.planOrNull(this.level, background));
        assertNotNull(EffectAudioService.planOrNull(this.level, oldest));
        assertNotNull(EffectAudioService.planOrNull(this.level, critical));
    }

    @Test
    void dropsAnIncomingPlanBelowEveryActivePriority() {
        for (int index = 0; index < EffectAudioService.MAX_ACTIVE_PLANS_PER_LEVEL; index++) {
            EffectAudioService.start(
                this.level, plan(UUID.randomUUID(), 100, 0), EffectAudience.dimensionWide(),
                EffectPriority.CRITICAL
            );
        }
        UUID background = UUID.randomUUID();

        EffectAudioService.start(
            this.level, plan(background, 100, 0), EffectAudience.dimensionWide(), EffectPriority.BACKGROUND
        );

        assertEquals(EffectAudioService.MAX_ACTIVE_PLANS_PER_LEVEL, EffectAudioService.activeCount(this.level));
        assertNull(EffectAudioService.planOrNull(this.level, background));
        assertEquals(1L, EffectBudgetService.report(this.level).audioDrops());
    }

    @Test
    void reportsAudioCapacityAndDegradation() {
        EffectAudioService.start(
            this.level, plan(UUID.randomUUID(), 100, 0), EffectAudience.dimensionWide(), EffectPriority.NORMAL
        );

        EffectBudgetReport report = EffectBudgetService.report(this.level);
        assertEquals(1, report.audioPlans());
        assertEquals(EffectAudioService.MAX_ACTIVE_PLANS_PER_LEVEL, report.audioPlanBudget());
        assertEquals(0L, report.audioEvictions());

        for (int index = 1; index < EffectAudioService.MAX_ACTIVE_PLANS_PER_LEVEL; index++) {
            EffectAudioService.start(
                this.level, plan(UUID.randomUUID(), 100, 0), EffectAudience.dimensionWide(),
                EffectPriority.NORMAL
            );
        }
        EffectAudioService.start(
            this.level, plan(UUID.randomUUID(), 100, 0), EffectAudience.dimensionWide(), EffectPriority.HIGH
        );

        assertEquals(1L, EffectBudgetService.report(this.level).audioEvictions());
    }

    @Test
    void rejectsWrongDimensionAndClientSideStarts() {
        UUID effectId = UUID.randomUUID();
        EffectAudioPlan wrongDimension = new EffectAudioPlan(
            effectId,
            ResourceLocation.withDefaultNamespace("the_nether"),
            Vec3.ZERO,
            0L,
            100,
            List.of(cue(0))
        );

        assertThrows(IllegalArgumentException.class,
            () -> EffectAudioService.start(this.level, wrongDimension, EffectAudience.dimensionWide()));

        when(this.level.isClientSide()).thenReturn(true);
        assertThrows(IllegalStateException.class,
            () -> EffectAudioService.start(this.level, plan(effectId, 100, 0),
                EffectAudience.dimensionWide()));
    }

    @Test
    void emptyPlansAreIgnored() {
        UUID effectId = UUID.randomUUID();
        EffectAudioPlan empty = new EffectAudioPlan(
            effectId, DIMENSION, Vec3.ZERO, 0L, 100, List.of()
        );

        EffectAudioService.start(this.level, empty, EffectAudience.dimensionWide());

        assertEquals(0, EffectAudioService.activeCount(this.level));
    }

    @Test
    void anEmptyReplacementStopsTheExistingPlan() {
        UUID effectId = UUID.randomUUID();
        EffectAudioService.start(this.level, plan(effectId, 100, 0), EffectAudience.dimensionWide());
        assertEquals(1, EffectAudioService.activeCount(this.level));

        EffectAudioPlan empty = new EffectAudioPlan(
            effectId, DIMENSION, Vec3.ZERO, 0L, 100, List.of()
        );
        EffectAudioService.start(this.level, empty, EffectAudience.dimensionWide());

        assertEquals(0, EffectAudioService.activeCount(this.level));
    }

    @Test
    void triggerOnlyPlansStayActive() {
        UUID effectId = UUID.randomUUID();
        EffectAudioService.start(this.level, triggerPlan(effectId), EffectAudience.dimensionWide());

        assertEquals(1, EffectAudioService.activeCount(this.level));
        assertTrue(EffectAudioService.trigger(this.level, effectId, "impact"));
    }

    @Test
    void unknownTriggersAndMissingPlansAreIgnored() {
        UUID effectId = UUID.randomUUID();
        EffectAudioService.start(this.level, triggerPlan(effectId), EffectAudience.dimensionWide());

        assertFalse(EffectAudioService.trigger(this.level, effectId, "missing"));
        assertFalse(EffectAudioService.trigger(this.level, UUID.randomUUID(), "impact"));
    }

    @Test
    void capsTriggerEventsPerTick() {
        UUID effectId = UUID.randomUUID();
        EffectAudioService.start(this.level, triggerPlan(effectId), EffectAudience.dimensionWide());

        for (int index = 0; index < EffectAudioService.MAX_TRIGGER_EVENTS_PER_TICK_PER_PLAN; index++) {
            assertTrue(EffectAudioService.trigger(this.level, effectId, "impact"));
        }
        assertFalse(EffectAudioService.trigger(this.level, effectId, "impact"));

        EffectAudioService.tick(this.level);

        assertTrue(EffectAudioService.trigger(this.level, effectId, "impact"));
    }

    @Test
    void resumePlansMarksRecipientsSoTickDoesNotResend() {
        ServerPlayer player = player(new Vec3(0.0, 64.0, 0.0));
        when(this.level.players()).thenReturn(List.of(player));

        try (MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
            EffectAudioService.start(this.level, plan(UUID.randomUUID(), 100, 10),
                EffectAudience.dimensionWide());
            packets.clearInvocations();

            assertEquals(1, EffectAudioService.resumePlans(this.level, player).size());

            EffectAudioService.tick(this.level);
            packets.verifyNoInteractions();
        }
    }

    @Test
    void resumePlansReturnsOnlyPlayablePlans() {
        UUID effectId = UUID.randomUUID();
        EffectAudioService.start(this.level, plan(effectId, 100, 50), EffectAudience.dimensionWide());
        ServerPlayer player = player(new Vec3(0.0, 64.0, 0.0));
        when(this.level.players()).thenReturn(List.of(player));

        this.gameTime = 40;
        List<EffectAudioPlan> plans = EffectAudioService.resumePlans(this.level, player);

        assertEquals(1, plans.size());
        assertEquals(effectId, plans.getFirst().effectId());

        this.gameTime = 100;
        assertTrue(EffectAudioService.resumePlans(this.level, player).isEmpty());
    }

    @Test
    void resumePlansRespectsNearbyAudience() {
        UUID effectId = UUID.randomUUID();
        EffectAudioPlan plan = new EffectAudioPlan(
            effectId,
            DIMENSION,
            new Vec3(0.0, 64.0, 0.0),
            0L,
            100,
            List.of(cue(10))
        );
        EffectAudioService.start(this.level, plan, EffectAudience.nearby(10.0));
        ServerPlayer far = player(new Vec3(100.0, 64.0, 0.0));
        when(this.level.players()).thenReturn(List.of(far));

        this.gameTime = 5;
        assertTrue(EffectAudioService.resumePlans(this.level, far).isEmpty());

        ServerPlayer near = player(new Vec3(5.0, 64.0, 0.0));
        when(this.level.players()).thenReturn(List.of(near));
        assertEquals(1, EffectAudioService.resumePlans(this.level, near).size());
    }

    private ServerPlayer player(Vec3 position) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(this.level);
        when(player.position()).thenReturn(position);
        return player;
    }

    private static EffectAudioPlan plan(UUID effectId, int durationTicks, int cueTick) {
        return new EffectAudioPlan(
            effectId,
            DIMENSION,
            Vec3.ZERO,
            0L,
            durationTicks,
            cueTick < durationTicks ? List.of(cue(cueTick)) : List.of()
        );
    }

    private static EffectAudioPlan triggerPlan(UUID effectId) {
        return new EffectAudioPlan(
            effectId,
            DIMENSION,
            Vec3.ZERO,
            0L,
            100,
            List.of(),
            List.of(new EffectAudioTrigger(
                "impact", Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F
            ))
        );
    }

    private static EffectAudioCue cue(int tick) {
        return new EffectAudioCue(tick, Vec3.ZERO, SOUND, SoundSource.MASTER, 1.0F, 1.0F);
    }
}
