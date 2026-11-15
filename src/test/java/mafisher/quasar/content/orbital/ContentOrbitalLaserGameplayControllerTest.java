package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import mafisher.quasar.TestConfig;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.content.ContentEffectIds;
import mafisher.quasar.content.orbital.ContentOrbitalLaserTarget.TargetColumn;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class ContentOrbitalLaserGameplayControllerTest {
    private TestServerLevel world;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        this.world = new TestServerLevel();
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
    void startsOneMarkerAndMakesNoBlockEdits() {
        ContentOrbitalLaserGameplayController controller =
            new ContentOrbitalLaserGameplayController();
        GameplayEffectHandle handle = start(controller);
        assertTrue(GameplayEffectService.isActive(this.world.level(), handle));
        assertEquals(1, VisualEffectService.activeCount(this.world.level()));
        assertEquals(1, descriptors(EffectKind.GAMEPLAY));
        assertEquals(1, descriptors(EffectKind.VISUAL));

        tick(ContentOrbitalLaserSettings.TOTAL_TICKS);

        assertFalse(GameplayEffectService.isActive(this.world.level(), handle));
        assertEquals(0, VisualEffectService.activeCount(this.world.level()));
        verify(this.world.level(), never()).setBlockAndUpdate(
            any(BlockPos.class), any(BlockState.class)
        );
        verify(this.world.level(), never()).setBlock(
            any(BlockPos.class), any(BlockState.class), anyInt()
        );
    }

    @Test
    void cancelStopsTheMarkerWithoutBlockEdits() {
        ContentOrbitalLaserGameplayController controller =
            new ContentOrbitalLaserGameplayController();
        GameplayEffectHandle handle = start(controller);

        GameplayEffectService.stop(
            this.world.level(), handle, GameplayEffectEndReason.CANCELLED
        );

        assertFalse(GameplayEffectService.isActive(this.world.level(), handle));
        assertEquals(0, VisualEffectService.activeCount(this.world.level()));
        verify(this.world.level(), never()).setBlockAndUpdate(
            any(BlockPos.class), any(BlockState.class)
        );
        verify(this.world.level(), never()).setBlock(
            any(BlockPos.class), any(BlockState.class), anyInt()
        );
    }

    private long descriptors(EffectKind kind) {
        return this.world.store().descriptors().stream()
            .filter(descriptor -> descriptor.kind() == kind)
            .filter(descriptor -> descriptor.typeId().equals(ContentEffectIds.ORBITAL_LASER))
            .count();
    }

    private GameplayEffectHandle start(ContentOrbitalLaserGameplayController controller) {
        GameplayEffectSpec spec = ContentOrbitalLaserSpecs.gameplay(
            null, new TargetColumn(0, 0, 20), 6, 42L
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
