package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.effect.EffectParameterState;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.gameplay.EffectPhase;
import mafisher.quasar.api.gameplay.EffectPhasePlan;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.gameplay.OwnerPolicy;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.content.orbital.ContentOrbitalLaserTarget.TargetColumn;
import mafisher.quasar.effect.persistence.EffectParameterNbt;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class ContentOrbitalLaserGameplayControllerTest {
    private static final int SURFACE_Y = 16;
    private static final int RADIUS = 6;

    private TestServerLevel world;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        this.world = new TestServerLevel();
        this.world.setBuildHeight(0, 16);
        this.world.installRuntime();
    }

    @AfterEach
    void tearDown() {
        GameplayEffectService.stopAll(
            this.world.level(), GameplayEffectEndReason.CANCELLED
        );
        VisualEffectService.clear(this.world.level());
        this.world.close();
        TestConfig.unload();
    }

    @Test
    void startsOneCompanionAndCarvesOnlyAfterImpact() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        ContentOrbitalLaserGameplayController controller =
            new ContentOrbitalLaserGameplayController();
        GameplayEffectHandle handle = start(controller);

        assertTrue(GameplayEffectService.isActive(this.world.level(), handle));
        assertEquals(1, VisualEffectService.activeCount(this.world.level()));
        assertEquals(1, descriptors(EffectKind.GAMEPLAY));
        assertEquals(1, descriptors(EffectKind.VISUAL));
        assertTrue(this.world.blockState(block(8, 15, 8)).is(Blocks.STONE));

        tick(phaseStart(ContentOrbitalLaserSettings.PHASE_IMPACT) - 1);
        assertTrue(this.world.blockState(block(8, 15, 8)).is(Blocks.STONE));

        tick(2);
        assertTrue(this.world.blockState(block(8, 15, 8)).isAir());

        tick(ContentOrbitalLaserSettings.TOTAL_TICKS);
        assertFalse(GameplayEffectService.isActive(this.world.level(), handle));
        assertEquals(0, VisualEffectService.activeCount(this.world.level()));
    }

    @Test
    void carvesTheFullRadiusToTheProtectedFloor() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        ContentOrbitalLaserGameplayController controller =
            new ContentOrbitalLaserGameplayController();
        start(controller);
        assertEquals(RADIUS, controller.effectiveRadius(), 1.0E-6);

        tick(ContentOrbitalLaserSettings.TOTAL_TICKS);

        assertTrue(this.world.blockState(block(8, 15, 8)).isAir());
        assertTrue(this.world.blockState(block(8, 1, 8)).isAir());
        assertTrue(this.world.blockState(block(8, 0, 8)).is(Blocks.STONE));
        assertTrue(this.world.blockState(block(13, 15, 8)).isAir());
        assertTrue(this.world.blockState(block(14, 15, 8)).isAir());
        assertTrue(this.world.blockState(block(15, 15, 8)).is(Blocks.STONE));
    }

    @Test
    void carvesTheFullColumnAboveTheAimedSurfaceAndKeepsTheFlashAtTheAim() {
        prepareSlopedColumn();
        ContentOrbitalLaserGameplayController controller =
            new ContentOrbitalLaserGameplayController();
        start(controller, new TargetColumn(8, 8, 12));

        EffectPoint marker = visualDescriptor().transform().position();
        assertEquals(8.5, marker.x(), 1.0E-6);
        assertEquals(12.0 + ContentOrbitalLaserSettings.MARKER_LIFT, marker.y(), 1.0E-6);
        assertEquals(8.5, marker.z(), 1.0E-6);

        tick(ContentOrbitalLaserSettings.TOTAL_TICKS);

        assertTrue(this.world.blockState(block(9, 12, 8)).isAir());
        assertTrue(this.world.blockState(block(9, 15, 8)).isAir());
        assertTrue(this.world.blockState(block(8, 0, 8)).is(Blocks.STONE));
    }

    @Test
    void destroysPlantsAboveTheMotionBlockingSurface() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        this.world.setBlock(block(8, 15, 8), Blocks.SHORT_GRASS.defaultBlockState());
        this.world.setColumnHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8, 15);
        this.world.setColumnHeight(Heightmap.Types.WORLD_SURFACE, 8, 8, 16);
        start(new ContentOrbitalLaserGameplayController(), new TargetColumn(8, 8, 15));

        tick(ContentOrbitalLaserSettings.TOTAL_TICKS);

        assertTrue(this.world.blockState(block(8, 15, 8)).isAir());
    }

    @Test
    void persistsTheCarveCeilingForResume() {
        prepareSlopedColumn();
        start(new ContentOrbitalLaserGameplayController(), new TargetColumn(8, 8, 12));
        tick(1);

        assertEquals(
            16.0,
            gameplayDescriptor().state().getCompound("state").getDouble("carve_top_y"),
            1.0E-6
        );
    }

    @Test
    void syncsBeamRadiusScaleAndActualFrontDepth() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        ContentOrbitalLaserGameplayController controller =
            new ContentOrbitalLaserGameplayController();
        start(controller);

        EffectDescriptor visual = visualDescriptor();
        EffectParameterState initial = EffectParameterNbt.loadFrom(visual);
        assertEquals(1.0F,
            initial.value(ContentOrbitalLaserCinematics.BEAM_RADIUS_SCALE).x(), 1.0E-6F);
        assertEquals(0.0F,
            initial.value(ContentOrbitalLaserCinematics.TIP_DEPTH).x(), 1.0E-6F);

        tick(phaseStart(ContentOrbitalLaserSettings.PHASE_SUSTAIN) + 8);

        EffectParameterState synced = EffectParameterNbt.loadFrom(visualDescriptor());
        assertTrue(controller.frontDepth() > 0.0F);
        assertEquals(controller.frontDepth(),
            synced.value(ContentOrbitalLaserCinematics.TIP_DEPTH).x(), 0.02F);
    }

    @Test
    void appliesTheImpactShockwaveOnce() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        Entity target = entityAt(8.5, SURFACE_Y - 0.5, 8.5);
        this.world.addWorldEntity(target);
        ContentOrbitalLaserGameplayController controller =
            new ContentOrbitalLaserGameplayController();
        start(controller);

        tick(phaseStart(ContentOrbitalLaserSettings.PHASE_IMPACT) + 2);
        verify(target, times(1)).push(any(Vec3.class));

        tick(20);
        verify(target, times(1)).push(any(Vec3.class));
    }

    @Test
    void damagesTargetsInTheBeamOnlyDuringSustain() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        LivingEntity target = livingAt(8.5, SURFACE_Y - 0.5, 8.5);
        this.world.addWorldEntity(target);
        ContentOrbitalLaserGameplayController controller =
            new ContentOrbitalLaserGameplayController();
        start(controller);

        tick(phaseStart(ContentOrbitalLaserSettings.PHASE_SUSTAIN) - 1);
        verify(target, never()).hurt(any(), anyFloat());

        tick(1);
        verify(target, times(1)).hurt(any(), anyFloat());

        tick(ContentOrbitalLaserSettings.SUSTAIN_TICKS);
        clearInvocations(target);
        tick(2);
        verify(target, never()).hurt(any(), anyFloat());
    }

    @Test
    void mapsLegacyResumePhaseIndicesByName() {
        EffectPhasePlan legacy = EffectPhasePlan.of(
            new EffectPhase("target", 40),
            new EffectPhase("descent", 20),
            new EffectPhase("impact", 4),
            new EffectPhase("sustain", 100),
            new EffectPhase("collapse", 30),
            new EffectPhase("aftermath", 26)
        );
        GameplayEffectSpec legacySpec = new GameplayEffectSpec(
            ContentEffectIds.ORBITAL_LASER,
            null,
            Vec3.ZERO,
            legacy,
            1L,
            ContentOrbitalLaserSettings.gameplayBudget(),
            OwnerPolicy.STOP_WHEN_UNAVAILABLE
        );
        GameplayEffectContext context = mock(GameplayEffectContext.class);
        when(context.spec()).thenReturn(legacySpec);

        when(context.phaseIndex()).thenReturn(3);
        assertTrue(ContentOrbitalLaserDestruction.damageActive(context));
        when(context.phaseIndex()).thenReturn(4);
        assertFalse(ContentOrbitalLaserDestruction.damageActive(context));

        assertTrue(ContentOrbitalLaserSettings.isInPhase(
            legacy, 2, ContentOrbitalLaserSettings.PHASE_NAME_IMPACT
        ));
        assertFalse(ContentOrbitalLaserSettings.isInPhase(
            legacy, 2, ContentOrbitalLaserSettings.PHASE_NAME_SUSTAIN
        ));
    }

    @Test
    void cancellationBeforeImpactLeavesTerrainUntouched() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        ContentOrbitalLaserGameplayController controller =
            new ContentOrbitalLaserGameplayController();
        GameplayEffectHandle handle = start(controller);

        tick(phaseStart(ContentOrbitalLaserSettings.PHASE_IMPACT) - 1);
        GameplayEffectService.stop(
            this.world.level(), handle, GameplayEffectEndReason.CANCELLED
        );
        tick(ContentOrbitalLaserSettings.TOTAL_TICKS);

        assertTrue(this.world.blockState(block(8, 15, 8)).is(Blocks.STONE));
        assertFalse(GameplayEffectService.isActive(this.world.level(), handle));
        assertEquals(0, VisualEffectService.activeCount(this.world.level()));
    }

    @Test
    void cancellationDuringSustainStopsTheChildAndDamage() {
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        LivingEntity target = livingAt(8.5, SURFACE_Y - 0.5, 8.5);
        this.world.addWorldEntity(target);
        ContentOrbitalLaserGameplayController controller =
            new ContentOrbitalLaserGameplayController();
        GameplayEffectHandle handle = start(controller);

        tick(phaseStart(ContentOrbitalLaserSettings.PHASE_SUSTAIN) + 2);
        verify(target, times(1)).hurt(any(), anyFloat());

        GameplayEffectService.stop(
            this.world.level(), handle, GameplayEffectEndReason.CANCELLED
        );
        assertFalse(GameplayEffectService.isActive(this.world.level(), handle));
        assertEquals(0, VisualEffectService.activeCount(this.world.level()));

        clearInvocations(target);
        tick(ContentOrbitalLaserSettings.SUSTAIN_TICKS
            + ContentOrbitalLaserSettings.COLLAPSE_TICKS);
        verify(target, never()).hurt(any(), anyFloat());
    }

    private static BlockPos block(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    private void prepareSlopedColumn() {
        this.world.setGroundY(16);
        this.world.fillSection(Blocks.STONE.defaultBlockState());
        for (int y = 12; y < 16; y++) {
            this.world.setBlock(block(8, y, 8), Blocks.AIR.defaultBlockState());
        }
        this.world.setColumnHeight(8, 8, 12);
    }

    private Entity entityAt(double x, double y, double z) {
        Entity entity = mock(Entity.class);
        when(entity.getBoundingBox()).thenReturn(new AABB(x - 0.5, y - 0.5, z - 0.5,
            x + 0.5, y + 0.5, z + 0.5));
        when(entity.isAlive()).thenReturn(true);
        when(entity.isRemoved()).thenReturn(false);
        when(entity.isSpectator()).thenReturn(false);
        when(entity.getUUID()).thenReturn(UUID.randomUUID());
        return entity;
    }

    private LivingEntity livingAt(double x, double y, double z) {
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

    private long descriptors(EffectKind kind) {
        return this.world.store().descriptors().stream()
            .filter(descriptor -> descriptor.kind() == kind)
            .filter(descriptor -> descriptor.typeId().equals(ContentEffectIds.ORBITAL_LASER))
            .count();
    }

    private EffectDescriptor visualDescriptor() {
        return this.world.store().descriptors().stream()
            .filter(descriptor -> descriptor.kind() == EffectKind.VISUAL)
            .filter(descriptor -> descriptor.typeId().equals(ContentEffectIds.ORBITAL_LASER))
            .findFirst()
            .orElseThrow();
    }

    private EffectDescriptor gameplayDescriptor() {
        return this.world.store().descriptors().stream()
            .filter(descriptor -> descriptor.kind() == EffectKind.GAMEPLAY)
            .filter(descriptor -> descriptor.typeId().equals(ContentEffectIds.ORBITAL_LASER))
            .findFirst()
            .orElseThrow();
    }

    private GameplayEffectHandle start(ContentOrbitalLaserGameplayController controller) {
        return start(controller, new TargetColumn(8, 8, SURFACE_Y));
    }

    private GameplayEffectHandle start(
        ContentOrbitalLaserGameplayController controller,
        TargetColumn target
    ) {
        GameplayEffectSpec spec = ContentOrbitalLaserSpecs.gameplay(
            null, target, RADIUS, 42L
        );
        return GameplayEffectService.start(
            this.world.level(),
            spec,
            controller,
            EffectPersistenceOptions.DEFAULT.withCapabilities(
                EffectCapabilities.destructiveCapability()
            )
        );
    }

    private static int phaseStart(int phaseIndex) {
        return ContentOrbitalLaserSettings.phasePlan().phaseStartTick(phaseIndex);
    }

    private void tick(int ticks) {
        for (int index = 0; index < ticks; index++) {
            this.world.advanceGameTime();
            GameplayEffectService.tick(this.world.level());
            VisualEffectService.tick(this.world.level());
        }
    }
}
