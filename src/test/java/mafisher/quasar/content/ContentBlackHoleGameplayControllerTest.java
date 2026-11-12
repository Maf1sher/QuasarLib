package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
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
            assertEquals(0, context.pendingBlockChanges());
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
        GameplayEffectSpec spec = ContentCommands.blackHoleGameplaySpec(visual);
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
        GameplayEffectSpec spec = ContentCommands.blackHoleGameplaySpec(visual);
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
