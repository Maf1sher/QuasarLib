package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import mafisher.quasar.TestConfig;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.BlockRollbackService;
import mafisher.quasar.effect.persistence.EffectSavedData;
import mafisher.quasar.testing.GameplayEffectFixtures;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@GlobalStateTest
class GameplayEffectRollbackPersistenceTest {
    private TestServerLevel world;
    private BlockState oldState;
    private BlockState newState;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        this.world = new TestServerLevel();
        this.world.installRuntime();
        this.oldState = Blocks.STONE.defaultBlockState();
        this.newState = Blocks.AIR.defaultBlockState();
        this.world.setBlock(GameplayEffectFixtures.FIRST, this.oldState);
    }

    @AfterEach
    void tearDown() {
        GameplayEffectService.stopAll(this.world.level(), GameplayEffectEndReason.CANCELLED);
        this.world.close();
        TestConfig.unload();
    }

    @Test
    void rollsBackAppliedBlockChangesWhenCancelled() {
        GameplayEffectSpec spec = GameplayEffectFixtures.destroyerSpec(4, 20);
        GameplayEffectFixtures.QueueController controller =
            new GameplayEffectFixtures.QueueController(new BlockChange(
                GameplayEffectFixtures.FIRST, this.newState
            ));
        GameplayEffectHandle handle = GameplayEffectFixtures.startDestroyer(
            this.world, spec, controller
        );

        GameplayEffectService.tick(this.world.level());

        BlockJournal journal = this.world.store().journal(handle.sessionId());
        assertNotNull(journal);
        assertEquals(1, journal.size());
        verify(this.world.level()).setBlockAndUpdate(
            GameplayEffectFixtures.FIRST, this.newState
        );

        GameplayEffectService.stop(
            this.world.level(), handle, GameplayEffectEndReason.CANCELLED
        );

        assertTrue(this.world.store().contains(handle.sessionId()));
        assertTrue(this.world.store().journal(handle.sessionId()).isRollbackPending());

        BlockRollbackService.tick(this.world.level(), 10);

        verify(this.world.level()).setBlockAndUpdate(
            GameplayEffectFixtures.FIRST, this.oldState
        );
        assertFalse(this.world.store().contains(handle.sessionId()));
        assertNull(this.world.store().journal(handle.sessionId()));
    }

    @Test
    void keepsTheJournalDirtyWhenTheStoreRejectsTheWrite() {
        EffectSavedData rejecting = mock(EffectSavedData.class);
        when(this.world.storage().computeIfAbsent(
            any(SavedData.Factory.class), eq(EffectSavedData.FILE_ID)
        )).thenReturn(rejecting);
        when(rejecting.putJournal(any(), any())).thenReturn(false);

        GameplayEffectSpec spec = GameplayEffectFixtures.destroyerSpec(4, 20);
        GameplayEffectHandle handle = GameplayEffectFixtures.startDestroyer(
            this.world,
            spec,
            new GameplayEffectFixtures.QueueController(new BlockChange(
                GameplayEffectFixtures.FIRST, this.newState
            ))
        );
        GameplayEffectService.tick(this.world.level());

        ArgumentCaptor<BlockJournal> captor = ArgumentCaptor.forClass(BlockJournal.class);
        verify(rejecting, atLeastOnce()).putJournal(eq(handle.sessionId()), captor.capture());
        assertTrue(captor.getValue().isDirty());
    }

    @Test
    void keepsAFailedRollbackPersistedUntilItSucceeds() {
        GameplayEffectSpec spec = GameplayEffectFixtures.destroyerSpec(4, 20);
        GameplayEffectHandle handle = GameplayEffectFixtures.startDestroyer(
            this.world,
            spec,
            new GameplayEffectFixtures.QueueController(new BlockChange(
                GameplayEffectFixtures.FIRST, this.newState
            ))
        );
        GameplayEffectService.tick(this.world.level());
        verify(this.world.level()).setBlockAndUpdate(
            GameplayEffectFixtures.FIRST, this.newState
        );

        GameplayEffectService.stop(
            this.world.level(), handle, GameplayEffectEndReason.CANCELLED
        );
        doReturn(false).when(this.world.level()).setBlockAndUpdate(
            GameplayEffectFixtures.FIRST, this.oldState
        );

        BlockRollbackService.tick(this.world.level(), 10);

        assertTrue(this.world.store().contains(handle.sessionId()));
        BlockJournal journal = this.world.store().journal(handle.sessionId());
        assertNotNull(journal);
        assertTrue(journal.isRollbackPending());

        when(this.world.level().setBlockAndUpdate(
            GameplayEffectFixtures.FIRST, this.oldState
        )).thenAnswer(invocation -> {
            this.world.setBlock(GameplayEffectFixtures.FIRST, this.oldState);
            return true;
        });
        BlockRollbackService.tick(this.world.level(), 10);

        assertFalse(this.world.store().contains(handle.sessionId()));
        assertNull(this.world.store().journal(handle.sessionId()));
    }

    @Test
    void expiryKeepsBlockChangesAndDropsTheJournal() {
        GameplayEffectSpec spec = GameplayEffectFixtures.destroyerSpec(4, 1);
        GameplayEffectHandle handle = GameplayEffectFixtures.startDestroyer(
            this.world,
            spec,
            new GameplayEffectFixtures.QueueController(new BlockChange(
                GameplayEffectFixtures.FIRST, this.newState
            ))
        );

        GameplayEffectService.tick(this.world.level());

        verify(this.world.level()).setBlockAndUpdate(
            GameplayEffectFixtures.FIRST, this.newState
        );
        assertFalse(this.world.store().contains(handle.sessionId()));
        assertNull(this.world.store().journal(handle.sessionId()));
    }

    @Test
    void unloadPreservesTheJournalAndRestoreCanRollBack() {
        GameplayEffectSpec spec = GameplayEffectFixtures.destroyerSpec(4, 20);
        GameplayEffectHandle handle = GameplayEffectFixtures.startDestroyer(
            this.world,
            spec,
            new GameplayEffectFixtures.QueueController(new BlockChange(
                GameplayEffectFixtures.FIRST, this.newState
            ))
        );
        GameplayEffectService.tick(this.world.level());
        verify(this.world.level()).setBlockAndUpdate(
            GameplayEffectFixtures.FIRST, this.newState
        );

        GameplayEffectService.stopAll(
            this.world.level(),
            GameplayEffectEndReason.LEVEL_UNLOAD,
            EffectRemovalPolicy.KEEP_FOR_RESTORE
        );

        assertTrue(this.world.store().contains(handle.sessionId()));
        BlockJournal journal = this.world.store().journal(handle.sessionId());
        assertNotNull(journal);
        assertEquals(1, journal.size());
        assertFalse(journal.isRollbackPending());

        EffectDescriptor descriptor = this.world.store().find(handle.sessionId());
        GameplayEffectService.restore(
            this.world.level(),
            spec,
            handle.sessionId(),
            0L,
            descriptor,
            new GameplayEffectFixtures.RecordingController()
        );
        GameplayEffectService.stop(
            this.world.level(),
            new GameplayEffectHandle(handle.sessionId()),
            GameplayEffectEndReason.CANCELLED
        );

        BlockRollbackService.tick(this.world.level(), 10);

        verify(this.world.level()).setBlockAndUpdate(
            GameplayEffectFixtures.FIRST, this.oldState
        );
        assertFalse(this.world.store().contains(handle.sessionId()));
    }
}
