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
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
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

        tick(ContentOrbitalLaserSettings.TARGET_TICKS
            + ContentOrbitalLaserSettings.DESCENT_TICKS - 1);
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

        tick(ContentOrbitalLaserSettings.TARGET_TICKS
            + ContentOrbitalLaserSettings.DESCENT_TICKS
            + ContentOrbitalLaserSettings.IMPACT_TICKS + 8);

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

        tick(ContentOrbitalLaserSettings.TARGET_TICKS
            + ContentOrbitalLaserSettings.DESCENT_TICKS + 2);
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

        tick(ContentOrbitalLaserSettings.TARGET_TICKS
            + ContentOrbitalLaserSettings.DESCENT_TICKS
            + ContentOrbitalLaserSettings.IMPACT_TICKS - 1);
        verify(target, never()).hurt(any(), anyFloat());

        tick(1);
        verify(target, times(1)).hurt(any(), anyFloat());

        tick(ContentOrbitalLaserSettings.SUSTAIN_TICKS);
        clearInvocations(target);
        tick(2);
        verify(target, never()).hurt(any(), anyFloat());
    }

    private static BlockPos block(int x, int y, int z) {
        return new BlockPos(x, y, z);
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

    private GameplayEffectHandle start(ContentOrbitalLaserGameplayController controller) {
        GameplayEffectSpec spec = ContentOrbitalLaserSpecs.gameplay(
            null, new TargetColumn(8, 8, SURFACE_Y), RADIUS, 42L
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

    private void tick(int ticks) {
        for (int index = 0; index < ticks; index++) {
            this.world.advanceGameTime();
            GameplayEffectService.tick(this.world.level());
            VisualEffectService.tick(this.world.level());
        }
    }
}
