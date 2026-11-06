package mafisher.quasar.effect.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.gameplay.EffectPhase;
import mafisher.quasar.effect.gameplay.EffectPhasePlan;
import mafisher.quasar.effect.gameplay.EffectTickBudget;
import mafisher.quasar.effect.gameplay.GameplayEffectNbt;
import mafisher.quasar.effect.gameplay.GameplayEffectSpec;
import mafisher.quasar.effect.gameplay.OwnerPolicy;
import mafisher.quasar.effect.scale.SegmentedVisualEffectService;
import mafisher.quasar.effect.scale.SegmentedVisualEffectSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectRestoreServiceTest {
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath("quasar_lib", "effect");
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    private ServerLevel level;
    private EffectSavedData store;
    private long gameTime;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        DimensionDataStorage storage = mock(DimensionDataStorage.class);
        this.store = new EffectSavedData();
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
        when(this.level.getDataStorage()).thenReturn(storage);
        doReturn(this.store).when(storage).computeIfAbsent(
            any(SavedData.Factory.class), eq(EffectSavedData.FILE_ID)
        );
        this.gameTime = 40L;
    }

    @AfterEach
    void tearDown() {
        VisualEffectService.clear(this.level);
        SegmentedVisualEffectService.stopAll(this.level);
    }

    @Test
    void restoresAVisualAtItsOriginalAge() {
        EffectDescriptor descriptor = visualDescriptor(0L, 200, DIMENSION);
        this.store.put(descriptor);

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.restored());
        assertEquals(1, VisualEffectService.activeCount(this.level));
        assertTrue(this.store.descriptors().isEmpty());
    }

    @Test
    void dropsExpiredDescriptors() {
        this.store.put(visualDescriptor(0L, 40, DIMENSION));

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.expired());
        assertEquals(0, VisualEffectService.activeCount(this.level));
        assertTrue(this.store.descriptors().isEmpty());
    }

    @Test
    void quarantinesDescriptorsForAnotherDimension() {
        ResourceLocation nether = ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");
        this.store.put(visualDescriptor(0L, 200, nether));

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.quarantined());
        assertEquals(1, this.store.quarantineCount());
        assertEquals(0, VisualEffectService.activeCount(this.level));
    }

    @Test
    void restoresSegmentedCoordinatorsWithEverySegment() {
        SegmentedVisualEffectSpec spec = new SegmentedVisualEffectSpec(
            EFFECT,
            new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0),
            0.0F, 0.0F, 0.0F,
            1.0F, 2, 2, 4.0,
            200,
            7L,
            EffectAudience.nearby(64.0)
        );
        SegmentedVisualEffectService.start(this.level, spec);
        int visualCount = VisualEffectService.activeCount(this.level);
        assertEquals(4, visualCount);

        SegmentedVisualEffectService.stopAll(this.level, false);
        VisualEffectService.clear(this.level);
        this.gameTime = 80L;

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.restored());
        assertEquals(1, SegmentedVisualEffectService.activeCount(this.level));
        assertEquals(4, VisualEffectService.activeCount(this.level));
    }

    @Test
    void quarantinesGameplayWithoutARegisteredController() {
        GameplayEffectSpec spec = new GameplayEffectSpec(
            EFFECT,
            null,
            new net.minecraft.world.phys.Vec3(0.0, 64.0, 0.0),
            EffectPhasePlan.of(new EffectPhase("strike", 100)),
            3L,
            EffectTickBudget.unlimited(),
            OwnerPolicy.CONTINUE
        );
        this.store.put(new EffectDescriptor(
            UUID.randomUUID(),
            EffectKind.GAMEPLAY,
            EFFECT,
            DIMENSION,
            new EffectTransform(new EffectPoint(0.0, 64.0, 0.0), 0.0F, 0.0F, 0.0F),
            1.0F,
            3L,
            0L,
            100,
            EffectAudience.dimensionWide(),
            EffectCapabilities.NONE,
            EffectPersistence.RESUMABLE,
            null,
            GameplayEffectNbt.saveDescriptorState(spec, new CompoundTag())
        ));

        EffectRecoveryReport report = EffectRestoreService.restoreLevel(this.level);

        assertEquals(1, report.quarantined());
        assertEquals(1, this.store.quarantineCount());
    }

    private static EffectDescriptor visualDescriptor(long startGameTime, int durationTicks,
        ResourceLocation dimension) {
        return new EffectDescriptor(
            UUID.randomUUID(),
            EffectKind.VISUAL,
            EFFECT,
            dimension,
            new EffectTransform(new EffectPoint(1.0, 64.0, 2.0), 0.0F, 0.0F, 0.0F),
            1.0F,
            11L,
            startGameTime,
            durationTicks,
            EffectAudience.nearby(64.0),
            EffectCapabilities.NONE,
            EffectPersistence.RESUMABLE,
            null,
            new CompoundTag()
        );
    }
}
