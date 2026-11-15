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
import mafisher.quasar.effect.persistence.EffectParameterNbt;
import mafisher.quasar.effect.persistence.EffectRecoveryReport;
import mafisher.quasar.effect.persistence.EffectRestoreService;
import mafisher.quasar.effect.runtime.RecordingPacketSink;
import mafisher.quasar.effect.runtime.SavedDataEffectStore;
import mafisher.quasar.effect.runtime.TestBudgetSource;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
import net.minecraft.world.level.block.Blocks;
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

    @Test
    void resumesTheCarveFrontAndBeamDepth() {
        this.world.setBuildHeight(0, 16);
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectHandle handle = start(new TargetColumn(8, 8, 16));
        tick(80);
        float depthBefore = tipDepth();
        assertTrue(depthBefore > 0.0F);

        GameplayEffectService.stopAll(
            this.world.level(),
            GameplayEffectEndReason.LEVEL_UNLOAD,
            EffectRemovalPolicy.KEEP_FOR_RESTORE
        );
        VisualEffectService.clear(this.world.level());

        EffectRestoreService.restoreLevel(this.world.level());

        assertTrue(GameplayEffectService.isActive(this.world.level(), handle));
        assertEquals(1, VisualEffectService.activeCount(this.world.level()));
        this.world.advanceGameTime();
        GameplayEffectService.tick(this.world.level());
        VisualEffectService.tick(this.world.level());
        assertTrue(tipDepth() >= depthBefore - 0.1F);
    }

    private GameplayEffectHandle start() {
        return start(new TargetColumn(0, 0, 20));
    }

    private GameplayEffectHandle start(TargetColumn target) {
        GameplayEffectSpec spec = ContentOrbitalLaserSpecs.gameplay(
            null, target, 6, 42L
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

    private float tipDepth() {
        EffectDescriptor descriptor = this.world.store().find(visualDescriptorId());
        return EffectParameterNbt.loadFrom(descriptor)
            .value("TipDepth").x();
    }

    private void tick(int ticks) {
        for (int index = 0; index < ticks; index++) {
            this.world.advanceGameTime();
            GameplayEffectService.tick(this.world.level());
            VisualEffectService.tick(this.world.level());
        }
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
