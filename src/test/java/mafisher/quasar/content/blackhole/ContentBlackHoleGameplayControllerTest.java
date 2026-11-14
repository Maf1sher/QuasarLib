package mafisher.quasar.content.blackhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.GameplayEffectAccess;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class ContentBlackHoleGameplayControllerTest {
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
    void startsTheVisualAndPersistsTheDestructionPlan() {
        GameplayEffectContext context = context(age(0.0F), new CompoundTag());
        ContentBlackHoleGameplayController controller = new ContentBlackHoleGameplayController();

        try {
            controller.onStart(context);

            assertEquals(1, VisualEffectService.activeCount(this.world.level()));
            CompoundTag state = GameplayEffectAccess.rawPersistentState(context);
            assertTrue(state.contains("crater_start"));
            assertTrue(state.contains("crater_work_end"));
            assertTrue(state.contains("crater_ground_y"));
            assertEquals(15.0, state.getDouble("crater_ground_y"), 1.0E-6);
            assertTrue(state.contains("quasar_companion"));
            UUID childId = state.getCompound("quasar_companion").getUUID("child");
            EffectDescriptor child = this.world.store().find(childId);
            assertNotNull(child);
            assertEquals(EffectKind.VISUAL, child.kind());
            assertEquals(context.sessionId(), child.ownerSessionId());
            assertEquals(1200, child.durationTicks());
            assertEquals(7L, child.seed());
            assertEquals(1.0F, child.scale(), 1.0E-6);
            assertEquals(0, context.pendingBlockChanges());
        } finally {
            close(context);
        }
    }

    @Test
    void unloadedGroundSampleFallsBackWithoutForceLoading() {
        when(this.world.level().hasChunkAt(8, 8)).thenReturn(false);
        GameplayEffectContext context = context(age(0.0F), new CompoundTag());
        ContentBlackHoleGameplayController controller = new ContentBlackHoleGameplayController();

        try {
            controller.onStart(context);

            assertEquals(300.0,
                GameplayEffectAccess.rawPersistentState(context).getDouble("crater_ground_y"),
                1.0E-6);
            verify(this.world.level(), never()).getChunkAt(any());
        } finally {
            close(context);
        }
    }

    @Test
    void doesNotQueueBlockChangesBeforeIgnition() {
        GameplayEffectContext context = context(age(0.0F), new CompoundTag());
        ContentBlackHoleGameplayController controller = new ContentBlackHoleGameplayController();

        try {
            controller.onStart(context);
            context.beginTick(10);
            controller.onTick(context);

            assertEquals(0, context.pendingBlockChanges());
        } finally {
            close(context);
        }
    }

    @Test
    void carvesTheCoreAfterIgnition() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        Vec3 center = new Vec3(8.0, 8.0, 8.0);
        GameplayEffectContext context = context(
            center, 0.5F, age(0.0F), new CompoundTag()
        );
        ContentBlackHoleGameplayController controller = new ContentBlackHoleGameplayController();

        try {
            controller.onStart(context);
            context.beginTick(85);
            controller.onTick(context);

            assertTrue(context.pendingBlockChanges() > 0);
            assertTrue(GameplayEffectAccess.rawPersistentState(context).getBoolean("core_done"));
        } finally {
            close(context);
        }
    }

    @Test
    void advancesTheCraterAfterTheGrowthWindowStarts() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        Vec3 center = new Vec3(8.0, 8.0, 8.0);
        GameplayEffectContext context = context(
            center, 0.5F, age(0.25F), new CompoundTag()
        );
        ContentBlackHoleGameplayController controller = new ContentBlackHoleGameplayController();

        try {
            controller.onStart(context);
            context.beginTick((int)age(0.25F));
            controller.onTick(context);

            assertTrue(this.world.blockState(new BlockPos(8, 8, 8)).isAir());
            assertTrue(GameplayEffectAccess.rawPersistentState(context)
                .contains("carve_session"));
        } finally {
            close(context);
        }
    }

    @Test
    void tearingStopsAtTheWorkDeadline() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        Vec3 center = new Vec3(8.0, 8.0, 8.0);
        GameplayEffectContext context = context(
            center, 0.5F, age(0.95F), new CompoundTag()
        );
        ContentBlackHoleGameplayController controller = new ContentBlackHoleGameplayController();

        try {
            controller.onStart(context);
            context.beginTick((int)age(0.95F));
            controller.onTick(context);

            assertEquals(0, context.pendingBlockChanges());
        } finally {
            close(context);
        }
    }

    @Test
    void resumesACompletedLegacyCarveWithoutBudgetEdits() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        CompoundTag legacy = new CompoundTag();
        legacy.putDouble("crater_radius", 6.0);
        legacy.putDouble("crater_depth", 6.0);
        legacy.putFloat("crater_start", 0.085F);
        legacy.putFloat("crater_work_end", 0.76F);
        legacy.putDouble("crater_ground_y", 15.0);
        legacy.putBoolean("crater_released", true);
        legacy.putBoolean("core_done", true);
        Vec3 center = new Vec3(8.0, 8.0, 8.0);
        GameplayEffectContext context = context(
            center, 0.5F, age(0.25F), legacy
        );
        ContentBlackHoleGameplayController controller = new ContentBlackHoleGameplayController();

        try {
            controller.onResume(context);
            context.beginTick((int)age(0.25F));
            controller.onTick(context);

            assertEquals(0, context.pendingBlockChanges());
            assertEquals(Blocks.STONE.defaultBlockState(),
                this.world.blockState(new BlockPos(8, 8, 8)));
            assertTrue(GameplayEffectAccess.rawPersistentState(context)
                .getBoolean("crater_released"));
        } finally {
            close(context);
        }
    }

    @Test
    void resumeDoesNotStartAnotherVisual() {
        GameplayEffectContext first = context(age(0.0F), new CompoundTag());
        ContentBlackHoleGameplayController firstController = new ContentBlackHoleGameplayController();
        CompoundTag state;
        try {
            firstController.onStart(first);
            state = GameplayEffectAccess.rawPersistentState(first);
        } finally {
            close(first);
        }

        GameplayEffectContext resumed = context(age(100.0F), state);
        ContentBlackHoleGameplayController resumedController = new ContentBlackHoleGameplayController();
        try {
            resumedController.onResume(resumed);

            assertEquals(0, VisualEffectService.activeCount(this.world.level()));
            assertEquals(state.getFloat("yaw"),
                GameplayEffectAccess.rawPersistentState(resumed).getFloat("yaw"));
        } finally {
            close(resumed);
        }
    }

    @Test
    void appliesForcesDuringTheOpenPhaseWithoutTargets() {
        GameplayEffectContext context = context(age(0.5F), new CompoundTag());
        ContentBlackHoleGameplayController controller = new ContentBlackHoleGameplayController();

        try {
            controller.onStart(context);
            context.beginTick((int)age(0.5F));
            controller.onTick(context);

            assertEquals(0, context.pendingBlockChanges());
        } finally {
            close(context);
        }
    }

    @Test
    void releasesCraterSyncAtImplosion() {
        GameplayEffectContext context = context(age(1140.0F), new CompoundTag());
        ContentBlackHoleGameplayController controller = new ContentBlackHoleGameplayController();

        try {
            controller.onStart(context);
            context.beginTick(1140);
            controller.onTick(context);

            assertTrue(GameplayEffectAccess.rawPersistentState(context)
                .getBoolean("crater_released"));
        } finally {
            close(context);
        }
    }

    @Test
    void endRemovesTheSessionFromTheActiveSet() {
        GameplayEffectContext context = context(age(0.0F), new CompoundTag());
        ContentBlackHoleGameplayController controller = new ContentBlackHoleGameplayController();

        try {
            controller.onStart(context);
            controller.onEnd(context, GameplayEffectEndReason.CANCELLED);

            assertEquals(0, ContentBlackHoleGameplayController.stopAll(this.world.level()));
        } finally {
            close(context);
        }
    }

    @Test
    void endClosesTheTerrainEditor() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectContext context = context(
            new Vec3(8.0, 8.0, 8.0), 0.5F, age(0.0F), new CompoundTag()
        );
        ContentBlackHoleGameplayController controller = new ContentBlackHoleGameplayController();

        try {
            controller.onStart(context);
            controller.onEnd(context, GameplayEffectEndReason.CANCELLED);

            assertEquals(0, ContentBlackHoleGameplayController.stopAll(this.world.level()));
        } finally {
            close(context);
        }
    }

    @Test
    void stopAllOnlyStopsSessionsInThatLevel() {
        TestServerLevel other = new TestServerLevel();
        other.installRuntime();
        GameplayEffectContext first = context(age(0.0F), new CompoundTag());
        GameplayEffectContext second = context(age(0.0F), new CompoundTag());
        GameplayEffectContext otherContext = context(other, age(0.0F), new CompoundTag());
        ContentBlackHoleGameplayController firstController = new ContentBlackHoleGameplayController();
        ContentBlackHoleGameplayController secondController = new ContentBlackHoleGameplayController();
        ContentBlackHoleGameplayController otherController = new ContentBlackHoleGameplayController();

        try {
            firstController.onStart(first);
            secondController.onStart(second);
            otherController.onStart(otherContext);

            assertEquals(2, ContentBlackHoleGameplayController.stopAll(this.world.level()));
            assertEquals(1, ContentBlackHoleGameplayController.stopAll(other.level()));
        } finally {
            close(first);
            close(second);
            close(otherContext);
            other.close();
        }
    }

    private GameplayEffectContext context(float ageTicks, CompoundTag state) {
        return context(this.world, ageTicks, state);
    }

    private GameplayEffectContext context(
        Vec3 center,
        float scale,
        float ageTicks,
        CompoundTag state
    ) {
        VisualEffectSpec visual = visual(center, scale);
        GameplayEffectSpec spec = ContentBlackHoleGameplaySpec.of(visual);
        UUID sessionId = UUID.randomUUID();
        return GameplayEffectAccess.restore(
            this.world.level(),
            new EffectScope(this.world.level(), sessionId),
            spec,
            sessionId,
            EffectCapabilities.destructiveCapability(),
            new BlockJournal(),
            (int)ageTicks,
            state
        );
    }

    private GameplayEffectContext context(TestServerLevel target, float ageTicks, CompoundTag state) {
        VisualEffectSpec visual = visual(new Vec3(8.0, 300.0, 8.0), 1.0F);
        GameplayEffectSpec spec = ContentBlackHoleGameplaySpec.of(visual);
        UUID sessionId = UUID.randomUUID();
        return GameplayEffectAccess.restore(
            target.level(),
            new EffectScope(target.level(), sessionId),
            spec,
            sessionId,
            EffectCapabilities.destructiveCapability(),
            new BlockJournal(),
            (int)ageTicks,
            state
        );
    }

    private static float age(float progress) {
        return progress * 1200.0F;
    }

    private static VisualEffectSpec visual(Vec3 center, float scale) {
        return VisualEffectSpec.builder(
                ContentEffectIds.BLACK_HOLE,
                center,
                1200,
                7L,
                EffectAudience.dimensionWide()
            )
            .rotation(10.0F, 8.0F, 0.0F)
            .scale(scale)
            .build();
    }

    private static void close(GameplayEffectContext context) {
        context.close();
        context.scope().close();
    }
}
