package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.gameplay.CompanionSpec;
import mafisher.quasar.api.gameplay.EffectCompanion;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.effect.persistence.EffectRecoveryReport;
import mafisher.quasar.effect.persistence.EffectRestoreService;
import mafisher.quasar.effect.runtime.RecordingPacketSink;
import mafisher.quasar.effect.runtime.SavedDataEffectStore;
import mafisher.quasar.effect.runtime.TestBudgetSource;
import mafisher.quasar.testing.GameplayEffectFixtures;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class EffectCompanionRestoreTest {
    private static final ResourceLocation EFFECT =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "companion_restore_test");

    private TestServerLevel world;
    private TestBudgetSource budgets;
    private GameplayEffectFixtures.CompanionController restoredController;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        this.world = new TestServerLevel();
        this.budgets = new TestBudgetSource();
        this.world.installRuntime(
            new SavedDataEffectStore(this.world.level()),
            new RecordingPacketSink(),
            this.budgets
        );
        GameplayEffectTypeRegistry.clearForTesting();
        GameplayEffectTypeRegistry.register(
            GameplayEffectFixtures.TYPE,
            descriptor -> {
                this.restoredController =
                    new GameplayEffectFixtures.CompanionController(companionSpec());
                return this.restoredController;
            }
        );
    }

    @AfterEach
    void tearDown() {
        GameplayEffectService.stopAll(
            this.world.level(), GameplayEffectEndReason.CANCELLED
        );
        this.world.close();
        GameplayEffectTypeRegistry.clearForTesting();
        TestConfig.unload();
    }

    @Test
    void resumeAdoptsTheRestoredChildWithoutStartingAnother() {
        GameplayEffectFixtures.CompanionController started =
            new GameplayEffectFixtures.CompanionController(companionSpec());
        GameplayEffectHandle handle = GameplayEffectService.start(
            this.world.level(), parentSpec(), started
        );
        UUID childId = started.companion.childId();
        assertEquals(1, VisualEffectService.activeCount(this.world.level()));

        GameplayEffectService.stopAll(
            this.world.level(),
            GameplayEffectEndReason.LEVEL_UNLOAD,
            EffectRemovalPolicy.KEEP_FOR_RESTORE
        );
        VisualEffectService.clear(this.world.level());
        this.world.setGameTime(5L);

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.world.level());

        assertTrue(report.restored() >= 2);
        assertTrue(GameplayEffectService.isActive(this.world.level(), handle));
        assertEquals(1, VisualEffectService.activeCount(this.world.level()));
        assertNotNull(this.restoredController);
        assertEquals(childId, this.restoredController.companion.childId());
        assertEquals(
            EffectCompanion.Status.PENDING, this.restoredController.companion.status()
        );

        GameplayEffectService.tick(this.world.level());

        assertEquals(
            EffectCompanion.Status.ACTIVE, this.restoredController.companion.status()
        );
        assertEquals(1, VisualEffectService.activeCount(this.world.level()));
    }

    @Test
    void culledRestoreLeavesTheSessionActiveWithoutStartingAnother() {
        GameplayEffectFixtures.CompanionController started =
            new GameplayEffectFixtures.CompanionController(companionSpec());
        GameplayEffectHandle handle = GameplayEffectService.start(
            this.world.level(), parentSpec(), started
        );

        GameplayEffectService.stopAll(
            this.world.level(),
            GameplayEffectEndReason.LEVEL_UNLOAD,
            EffectRemovalPolicy.KEEP_FOR_RESTORE
        );
        VisualEffectService.clear(this.world.level());
        this.world.setGameTime(5L);
        this.budgets.standaloneVisuals(0);

        EffectRestoreService.restoreLevel(this.world.level());

        assertTrue(GameplayEffectService.isActive(this.world.level(), handle));
        assertEquals(0, VisualEffectService.activeCount(this.world.level()));
        assertNotNull(this.restoredController);
        assertEquals(
            EffectCompanion.Status.PENDING, this.restoredController.companion.status()
        );

        GameplayEffectService.tick(this.world.level());

        assertEquals(
            EffectCompanion.Status.UNAVAILABLE, this.restoredController.companion.status()
        );
        assertEquals(0, VisualEffectService.activeCount(this.world.level()));
    }

    private static CompanionSpec companionSpec() {
        return CompanionSpec.builder(EFFECT, EffectAudience.dimensionWide())
            .rotation(15.0F, 5.0F, 0.0F)
            .build();
    }

    private static GameplayEffectSpec parentSpec() {
        return new GameplayEffectSpec(
            GameplayEffectFixtures.TYPE,
            null,
            new Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("run", 40)),
            11L,
            EffectTickBudget.unlimited(),
            OwnerPolicy.CONTINUE,
            EffectAudioSpec.NONE
        );
    }
}
