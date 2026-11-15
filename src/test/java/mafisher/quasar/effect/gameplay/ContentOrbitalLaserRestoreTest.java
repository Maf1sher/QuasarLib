package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.RegisterGameplayEffectTypesEvent;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.content.ContentGameplayEffects;
import mafisher.quasar.content.orbital.ContentOrbitalLaserGameplayController;
import mafisher.quasar.content.orbital.ContentOrbitalLaserSpecs;
import mafisher.quasar.content.orbital.ContentOrbitalLaserTarget.TargetColumn;
import mafisher.quasar.effect.persistence.EffectRecoveryReport;
import mafisher.quasar.effect.persistence.EffectRestoreService;
import mafisher.quasar.effect.runtime.RecordingPacketSink;
import mafisher.quasar.effect.runtime.SavedDataEffectStore;
import mafisher.quasar.effect.runtime.TestBudgetSource;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class ContentOrbitalLaserRestoreTest {
    private TestServerLevel world;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        this.world = new TestServerLevel();
        this.world.installRuntime(
            new SavedDataEffectStore(this.world.level()),
            new RecordingPacketSink(),
            new TestBudgetSource()
        );
        GameplayEffectTypeRegistry.clearForTesting();
        ContentGameplayEffects.registerTypes(new RegisterGameplayEffectTypesEvent());
    }

    @AfterEach
    void tearDown() {
        GameplayEffectService.stopAll(
            this.world.level(), GameplayEffectEndReason.CANCELLED
        );
        VisualEffectService.clear(this.world.level());
        this.world.close();
        GameplayEffectTypeRegistry.clearForTesting();
        TestConfig.unload();
    }

    @Test
    void resumesTheExistingMarkerInsteadOfStartingAnother() {
        GameplayEffectHandle handle = start();
        UUID childId = visualDescriptorId();

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
        assertEquals(childId, visualDescriptorId());

        this.world.advanceGameTime();
        GameplayEffectService.tick(this.world.level());
        VisualEffectService.tick(this.world.level());

        assertEquals(1, VisualEffectService.activeCount(this.world.level()));
        assertEquals(childId, visualDescriptorId());
    }

    private GameplayEffectHandle start() {
        GameplayEffectSpec spec = ContentOrbitalLaserSpecs.gameplay(
            null, new TargetColumn(0, 0, 20), 6, 42L
        );
        return GameplayEffectService.start(
            this.world.level(),
            spec,
            new ContentOrbitalLaserGameplayController(),
            EffectPersistenceOptions.DEFAULT.withCapabilities(
                EffectCapabilities.destructiveCapability()
            )
        );
    }

    private UUID visualDescriptorId() {
        return this.world.store().descriptors().stream()
            .filter(descriptor -> descriptor.kind() == EffectKind.VISUAL)
            .filter(descriptor -> descriptor.typeId().equals(ContentEffectIds.ORBITAL_LASER))
            .map(EffectDescriptor::instanceId)
            .findFirst()
            .orElseThrow();
    }
}
