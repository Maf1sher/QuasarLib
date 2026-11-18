package mafisher.quasar.effect.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.gameplay.RegisterGameplayEffectTypesEvent;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.content.ContentGameplayEffects;
import mafisher.quasar.content.orbital.ContentOrbitalLaserGameplayController;
import mafisher.quasar.content.orbital.ContentOrbitalLaserSettings;
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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class ContentOrbitalLaserRestoreTest {
    private TestServerLevel world;
    private SavedDataEffectStore store;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        this.world = new TestServerLevel();
        this.store = new SavedDataEffectStore(this.world.level());
        this.world.installRuntime(
            this.store,
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
    void restoresThePhaseScorePlan() {
        GameplayEffectHandle handle = start();
        EffectAudioPlan before = EffectAudioService.planOrNull(
            this.world.level(), handle.sessionId()
        );
        assertNotNull(before);
        assertEquals(10, before.cueCount());
        long startGameTime = before.startGameTime();

        GameplayEffectService.stopAll(
            this.world.level(),
            GameplayEffectEndReason.LEVEL_UNLOAD,
            EffectRemovalPolicy.KEEP_FOR_RESTORE
        );
        VisualEffectService.clear(this.world.level());
        EffectRestoreService.restoreLevel(this.world.level());

        EffectAudioPlan restored = EffectAudioService.planOrNull(
            this.world.level(), handle.sessionId()
        );
        assertNotNull(restored);
        assertEquals(10, restored.cueCount());
        assertEquals(startGameTime, restored.startGameTime());
    }

    @Test
    void resumesTheCarveFrontAndBeamDepth() {
        this.world.setBuildHeight(0, 16);
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        GameplayEffectHandle handle = start(new TargetColumn(8, 8, 16));
        tick(ContentOrbitalLaserSettings.phasePlan()
            .phaseStartTick(ContentOrbitalLaserSettings.PHASE_SUSTAIN) + 8);
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

    @Test
    void resumesLegacyImpactByPhaseName() {
        this.world.setBuildHeight(0, 16);
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        Entity target = entityAt(8.5, 15.5, 8.5);
        this.world.addWorldEntity(target);

        restoreLegacyAt(60L);

        verify(target, times(1)).push(any(Vec3.class));
    }

    @Test
    void resumesLegacySustainDamageByPhaseName() {
        this.world.setBuildHeight(0, 16);
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        LivingEntity target = livingAt(8.5, 15.5, 8.5);
        this.world.addWorldEntity(target);

        restoreLegacyAt(100L);
        verify(target, never()).hurt(any(), anyFloat());

        for (int tick = 0; tick < 3; tick++) {
            GameplayEffectService.tick(this.world.level());
        }
        verify(target, never()).hurt(any(), anyFloat());

        GameplayEffectService.tick(this.world.level());
        verify(target, times(1)).hurt(any(), anyFloat());
    }

    @Test
    void restoresThePersistedCarveCeilingInsteadOfResamplingTheTerrain() {
        this.world.setBuildHeight(0, 16);
        this.world.setGroundY(16);
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        for (int y = 12; y < 16; y++) {
            this.world.setBlock(block(8, y, 8), Blocks.AIR.defaultBlockState());
        }
        this.world.setColumnHeight(8, 8, 12);
        GameplayEffectHandle handle = start(new TargetColumn(8, 8, 12));
        tick(1);

        GameplayEffectService.stopAll(
            this.world.level(),
            GameplayEffectEndReason.LEVEL_UNLOAD,
            EffectRemovalPolicy.KEEP_FOR_RESTORE
        );
        VisualEffectService.clear(this.world.level());
        this.world.setGroundY(12);

        EffectRestoreService.restoreLevel(this.world.level());
        assertTrue(GameplayEffectService.isActive(this.world.level(), handle));
        tick(ContentOrbitalLaserSettings.TOTAL_TICKS);

        assertTrue(this.world.blockState(block(9, 15, 8)).isAir());
        assertTrue(this.world.blockState(block(8, 0, 8)).is(Blocks.STONE));
    }

    private GameplayEffectHandle restoreLegacyAt(long gameTime) {
        EffectPhasePlan legacy = EffectPhasePlan.of(
            new EffectPhase("target", 40),
            new EffectPhase("descent", 20),
            new EffectPhase("impact", 4),
            new EffectPhase("sustain", 100),
            new EffectPhase("collapse", 30),
            new EffectPhase("aftermath", 26)
        );
        Vec3 anchor = new Vec3(8.5, 16.0, 8.5);
        GameplayEffectSpec spec = new GameplayEffectSpec(
            ContentEffectIds.ORBITAL_LASER,
            null,
            anchor,
            legacy,
            42L,
            ContentOrbitalLaserSettings.gameplayBudget(),
            OwnerPolicy.STOP_WHEN_UNAVAILABLE
        );
        UUID sessionId = UUID.randomUUID();
        EffectDescriptor descriptor = EffectDescriptor.builder(
                sessionId,
                EffectKind.GAMEPLAY,
                ContentEffectIds.ORBITAL_LASER,
                this.world.level().dimension().location()
            )
            .placement(
                new EffectTransform(
                    new EffectPoint(anchor.x, anchor.y, anchor.z), 0.0F, 0.0F, 0.0F
                ),
                1.0F
            )
            .timing(42L, 0L, legacy.totalDurationTicks())
            .audience(EffectAudience.dimensionWide())
            .capabilities(EffectCapabilities.destructiveCapability())
            .persistence(EffectPersistence.RESUMABLE)
            .state(GameplayEffectNbt.saveDescriptorState(spec, new CompoundTag()))
            .build();
        this.store.recordStart(descriptor);
        this.world.setGameTime(gameTime);
        return GameplayEffectService.restore(
            this.world.level(),
            spec,
            sessionId,
            0L,
            descriptor,
            new ContentOrbitalLaserGameplayController()
        ).orElseThrow();
    }

    private static Entity entityAt(double x, double y, double z) {
        Entity entity = mock(Entity.class);
        when(entity.getBoundingBox()).thenReturn(new AABB(x - 0.5, y - 0.5, z - 0.5,
            x + 0.5, y + 0.5, z + 0.5));
        when(entity.isAlive()).thenReturn(true);
        when(entity.isRemoved()).thenReturn(false);
        when(entity.isSpectator()).thenReturn(false);
        when(entity.getUUID()).thenReturn(UUID.randomUUID());
        return entity;
    }

    private static LivingEntity livingAt(double x, double y, double z) {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getBoundingBox()).thenReturn(new AABB(x - 0.5, y - 0.5, z - 0.5,
            x + 0.5, y + 0.5, z + 0.5));
        when(entity.isAlive()).thenReturn(true);
        when(entity.isRemoved()).thenReturn(false);
        when(entity.isSpectator()).thenReturn(false);
        when(entity.getUUID()).thenReturn(UUID.randomUUID());
        when(entity.hurt(any(), anyFloat())).thenReturn(true);
        return entity;
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

    private static BlockPos block(int x, int y, int z) {
        return new BlockPos(x, y, z);
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
