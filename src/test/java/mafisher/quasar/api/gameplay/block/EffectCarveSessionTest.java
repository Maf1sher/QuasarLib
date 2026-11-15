package mafisher.quasar.api.gameplay.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.GameplayEffectAccess;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class EffectCarveSessionTest {
    private static final int DURATION = 1_200;
    private static final Vec3 CENTER = new Vec3(8.0, 8.0, 8.0);
    private static final double SURFACE = 15.0;

    private TestServerLevel world;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        this.world = new TestServerLevel();
        this.world.installRuntime();
    }

    @AfterEach
    void tearDown() {
        this.world.close();
        TestConfig.unload();
    }

    @Test
    void startDoesNotEditOrForceLoadAChunk() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, new CompoundTag());

        EffectCarveSession session = EffectCarveSession.start(
            context, spec(false), EffectCarvePlan.plan(context, spec(false))
        );

        assertEquals(0, context.pendingBlockChanges());
        assertFalse(session.complete());
        assertFalse(session.incomplete());
        verify(this.world.level(), never()).getChunkAt(any());
        session.close(false);
        close(context);
    }

    @Test
    void carvesTheExactCoreAfterIgnition() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(85, new CompoundTag());
        EffectCarveSpec spec = spec(true);
        EffectCarveSession session = EffectCarveSession.start(
            context, spec, EffectCarvePlan.plan(context, spec)
        );

        session.tick();

        assertTrue(session.coreComplete());
        assertTrue(context.pendingBlockChanges() > 0);
        session.close(false);
        close(context);
    }

    @Test
    void lateCoreContinuesWithTheMainVolume() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, 1, new CompoundTag());
        EffectCarveSpec spec = spec(true, 6.0, 3.0);
        EffectCarvePlan plan = EffectCarvePlan.plan(context, spec);
        EffectCarveSession session = EffectCarveSession.start(context, spec, plan);

        context.beginTick(Math.round(0.10F * DURATION));
        session.tick();
        assertFalse(session.coreComplete());

        context.beginTick(Math.round(plan.growthStart() * DURATION) + 2);
        session.tick();

        assertTrue(session.coreSkipped());
        assertTrue(session.progress().completedSections() > 0);
        session.close(false);
        close(context);
    }

    @Test
    void bulkSectionEditsBumpTheBlockEditVersion() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, new CompoundTag());
        EffectCarveSpec spec = spec(true);
        EffectCarvePlan plan = EffectCarvePlan.plan(context, spec);
        EffectCarveSession session = EffectCarveSession.start(context, spec, plan);

        context.beginTick(Math.round(plan.growthStart() * DURATION) + 2);
        long before = context.blockEditVersion();
        session.tick();

        assertTrue(session.progress().completedSections() > 0);
        assertTrue(context.blockEditVersion() > before);
        session.close(false);
        close(context);
    }

    @Test
    void stopsEditingAtTheDeadlineAndMarksTheCarveIncomplete() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(1_080, new CompoundTag());
        EffectCarveSpec spec = spec(true);
        EffectCarveSession session = EffectCarveSession.start(
            context, spec, EffectCarvePlan.plan(context, spec)
        );

        session.tick();

        assertTrue(session.incomplete());
        assertFalse(session.complete());
        assertTrue(session.coreSkipped());
        assertTrue(session.syncReleased());
        assertEquals(0, session.progress().clearedBlocks());
        session.close(false);
        close(context);
    }

    @Test
    void earlyEditorCompletionDoesNotSnapTheAuthoredFront() {
        GameplayEffectContext context = context(0, new CompoundTag());
        EffectCarveSpec spec = spec(false, 1.0);
        EffectCarvePlan plan = EffectCarvePlan.plan(context, spec);
        EffectCarveSession session = EffectCarveSession.start(context, spec, plan);

        context.beginTick(Math.round(plan.growthStart() * DURATION) + 1);
        session.tick();
        float earlyFront = session.frontScale();
        assertTrue(session.complete());
        assertTrue(earlyFront < 1.0F);

        context.beginTick(Math.round(
            (plan.growthStart() + plan.workEnd()) * 0.5F * DURATION
        ));
        session.tick();

        assertTrue(session.frontScale() > earlyFront);
        assertTrue(session.frontScale() < 1.0F);
        session.close(false);
        close(context);
    }

    @Test
    void saveAndRestoreKeepThePlanAndCursor() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, new CompoundTag());
        EffectCarveSpec spec = spec(false);
        EffectCarvePlan plan = EffectCarvePlan.plan(context, spec);
        EffectCarveSession session = EffectCarveSession.start(context, spec, plan);

        context.beginTick(Math.round(plan.growthStart() * DURATION) + 2);
        session.tick();
        float front = session.frontScale();
        int progress = session.progress().completedSections();
        CompoundTag saved = session.save();
        session.close(false);

        GameplayEffectContext resumed = context(
            Math.round(plan.growthStart() * DURATION) + 2, saved
        );
        EffectCarveSession restored = EffectCarveSession.restore(resumed, spec, saved);

        assertEquals(plan.scale(), restored.plan().scale(), 1.0E-6);
        assertEquals(plan.workEnd(), restored.plan().workEnd(), 1.0E-6);
        assertEquals(progress, restored.progress().completedSections());
        assertEquals(front, restored.frontScale(), 1.0E-6);
        restored.close(false);
        close(resumed);
        close(context);
    }

    @Test
    void unknownStateStartsAFreshIdempotentCarve() {
        GameplayEffectContext context = context(0, new CompoundTag());
        EffectCarveSpec spec = spec(false);
        CompoundTag unknown = new CompoundTag();
        unknown.putInt("version", 99);

        EffectCarveSession session = EffectCarveSession.restore(context, spec, unknown);

        assertFalse(session.complete());
        assertEquals(0, session.progress().completedSections());
        session.close(false);
        close(context);
    }

    @Test
    void topDownSessionCarvesFromTheTopAndRestoresItsFront() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(0, new CompoundTag());
        EffectCarveSpec spec = topDownSpec(4.0);
        EffectCarvePlan plan = EffectCarvePlan.plan(context, spec);
        EffectCarveSession session = EffectCarveSession.start(context, spec, plan);

        assertEquals(EffectBulkBlockEditor.Ordering.TOP_DOWN, session.plan().ordering());
        assertEquals(plan.frontTopY(), session.frontY(), 1.0E-6);

        context.beginTick(Math.round(plan.growthStart() * DURATION) + 2);
        session.tick();

        assertTrue(session.frontY() < plan.frontTopY());
        assertTrue(session.progress().completedSections() > 0);
        assertEquals(0.0, session.carvedFrontY(), 1.0E-6);

        double front = session.frontY();
        CompoundTag saved = session.save();
        session.close(false);

        GameplayEffectContext resumed = context(
            Math.round(plan.growthStart() * DURATION) + 2, saved
        );
        EffectCarveSession restored = EffectCarveSession.restore(resumed, spec, saved);

        assertEquals(EffectBulkBlockEditor.Ordering.TOP_DOWN, restored.plan().ordering());
        assertEquals(front, restored.frontY(), 1.0E-6);
        assertEquals(0.0, restored.carvedFrontY(), 1.0E-6);
        restored.close(false);
        close(resumed);
        close(context);
    }

    @Test
    void topDownSessionStopsAtTheDeadline() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(1_080, new CompoundTag());
        EffectCarveSpec spec = topDownSpec(4.0);
        EffectCarveSession session = EffectCarveSession.start(
            context, spec, EffectCarvePlan.plan(context, spec)
        );

        session.tick();

        assertTrue(session.incomplete());
        assertFalse(session.complete());
        assertEquals(session.plan().frontTopY(), session.frontY(), 1.0E-6);
        assertEquals(0, session.progress().clearedBlocks());
        session.close(false);
        close(context);
    }

    private EffectCarveSpec topDownSpec(double radius) {
        EffectPoint center = new EffectPoint(CENTER.x, CENTER.y, CENTER.z);
        return new EffectCarveSpec(
            EffectBlockVolume.cylinder(center, radius, 0.0, SURFACE),
            Blocks.AIR.defaultBlockState(),
            true,
            true,
            EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
            0.085F,
            0.860F,
            null,
            0.07F,
            SURFACE,
            EffectBulkBlockEditor.Ordering.TOP_DOWN
        );
    }

    private EffectCarveSpec spec(boolean core) {
        return spec(core, 6.0);
    }

    private EffectCarveSpec spec(boolean core, double radius) {
        return spec(core, radius, 1.0);
    }

    private EffectCarveSpec spec(boolean core, double radius, double coreRadius) {
        EffectPoint center = new EffectPoint(CENTER.x, CENTER.y, CENTER.z);
        return new EffectCarveSpec(
            EffectBlockVolume.sphere(center, radius),
            Blocks.AIR.defaultBlockState(),
            true,
            true,
            EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
            0.085F,
            0.860F,
            core ? EffectBlockVolume.sphere(center, coreRadius) : null,
            0.07F,
            SURFACE
        );
    }

    private GameplayEffectContext context(int ageTicks, CompoundTag state) {
        return context(ageTicks, 4_096, state);
    }

    private GameplayEffectContext context(
        int ageTicks,
        int maxBlockChanges,
        CompoundTag state
    ) {
        EffectPhasePlan phases = EffectPhasePlan.of(new EffectPhase("open", DURATION));
        GameplayEffectSpec spec = new GameplayEffectSpec(
            ResourceLocation.fromNamespaceAndPath("quasar_lib", "carve_session_test"),
            null,
            CENTER,
            phases,
            7L,
            new EffectTickBudget(0, 0, 0, maxBlockChanges, 16_384, 0, 0, 64),
            OwnerPolicy.CONTINUE,
            EffectAudioSpec.NONE,
            EffectPriority.NORMAL,
            1.0F
        );
        UUID sessionId = UUID.randomUUID();
        return GameplayEffectAccess.restore(
            this.world.level(),
            new EffectScope(this.world.level(), sessionId),
            spec,
            sessionId,
            EffectCapabilities.destructiveCapability(),
            new BlockJournal(),
            ageTicks,
            state
        );
    }

    private static void close(GameplayEffectContext context) {
        context.close();
        context.scope().close();
    }
}
