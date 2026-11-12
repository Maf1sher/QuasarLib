package mafisher.quasar.testing;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import mafisher.quasar.effect.persistence.EffectSavedData;
import mafisher.quasar.effect.runtime.EffectBudgetSource;
import mafisher.quasar.effect.runtime.EffectPacketSink;
import mafisher.quasar.effect.runtime.EffectStore;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import mafisher.quasar.effect.runtime.RecordingPacketSink;
import mafisher.quasar.effect.runtime.SavedDataEffectStore;
import mafisher.quasar.effect.runtime.TestBudgetSource;
import mafisher.quasar.effect.runtime.TestEffectRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.AABB;
import org.mockito.Answers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Stubbing;

/**
 * Small server-level fixture with an in-memory block map, one loaded chunk section, and a
 * fail-fast check for unstubbed {@link ServerLevel} calls.
 */
public final class TestServerLevel {
    private final ServerLevel level;
    private final DimensionDataStorage storage;
    private final EffectSavedData store;
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<UUID, Entity> entities = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private final ServerChunkCache chunkSource;
    private final LevelChunk chunk;
    private final LevelChunkSection section;
    private final LevelChunkSection[] sections;
    private final ChunkPos chunkPos = new ChunkPos(0, 0);
    private long gameTime;
    private int groundY = 15;
    private boolean ready;
    private LevelEffectRuntime runtime;

    public TestServerLevel() {
        this.level = mock(ServerLevel.class, this::answer);
        this.storage = mock(DimensionDataStorage.class);
        this.store = new EffectSavedData();
        this.chunkSource = mock(ServerChunkCache.class);
        this.chunk = mock(LevelChunk.class);
        PalettedContainer<BlockState> states = new PalettedContainer<>(
            Block.BLOCK_STATE_REGISTRY,
            Blocks.AIR.defaultBlockState(),
            PalettedContainer.Strategy.SECTION_STATES
        );
        PalettedContainerRO<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> biomes =
            mock(PalettedContainerRO.class);
        this.section = new LevelChunkSection(states, biomes);
        this.sections = new LevelChunkSection[]{this.section};

        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
        when(this.level.getDataStorage()).thenReturn(this.storage);
        when(this.level.getServer()).thenReturn(null);
        when(this.level.getBlockEntity(any(BlockPos.class))).thenReturn(null);
        when(this.storage.computeIfAbsent(
            any(SavedData.Factory.class), eq(EffectSavedData.FILE_ID)
        )).thenReturn(this.store);
        when(this.level.isInWorldBounds(any(BlockPos.class))).thenReturn(true);
        when(this.level.hasChunkAt(any(BlockPos.class))).thenReturn(true);
        when(this.level.hasChunkAt(anyInt(), anyInt())).thenReturn(true);
        when(this.level.getBlockState(any(BlockPos.class))).thenAnswer(
            invocation -> blockState(invocation.getArgument(0))
        );
        when(this.level.setBlockAndUpdate(any(BlockPos.class), any(BlockState.class))).thenAnswer(
            invocation -> {
                setBlock(invocation.getArgument(0), invocation.getArgument(1));
                return true;
            }
        );
        when(this.level.setBlock(any(BlockPos.class), any(BlockState.class), anyInt())).thenAnswer(
            invocation -> {
                setBlock(invocation.getArgument(0), invocation.getArgument(1));
                return true;
            }
        );
        doNothing().when(this.level).onBlockStateChange(any(), any(), any());
        doNothing().when(this.level).updateNeighborsAt(any(BlockPos.class), any(Block.class));
        doNothing().when(this.level).updateNeighbourForOutputSignal(any(BlockPos.class), any(Block.class));
        when(this.level.getMinBuildHeight()).thenReturn(-16);
        when(this.level.getMaxBuildHeight()).thenReturn(16);
        when(this.level.getMinSection()).thenReturn(-1);
        when(this.level.getMaxSection()).thenReturn(1);
        when(this.level.getSectionIndexFromSectionY(anyInt())).thenReturn(0);
        when(this.level.getHeight(any(Heightmap.Types.class), anyInt(), anyInt()))
            .thenAnswer(ignored -> this.groundY);
        when(this.level.getChunkAt(any(BlockPos.class))).thenReturn(this.chunk);
        when(this.level.getChunkSource()).thenReturn(this.chunkSource);
        when(this.chunkSource.getChunkNow(anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int z = invocation.getArgument(1);
            return x == this.chunkPos.x && z == this.chunkPos.z ? this.chunk : null;
        });
        when(this.chunk.getPos()).thenReturn(this.chunkPos);
        when(this.chunk.getSection(anyInt())).thenReturn(this.section);
        when(this.chunk.getSections()).thenReturn(this.sections);
        when(this.chunk.getBlockEntities()).thenReturn(Map.of());
        when(this.level.getEntitiesOfClass(any(), any(AABB.class))).thenReturn(List.of());
        DamageSources damageSources = mock(DamageSources.class);
        when(damageSources.fellOutOfWorld()).thenReturn(mock(DamageSource.class));
        when(this.level.damageSources()).thenReturn(damageSources);
        when(this.level.getEntity(any(UUID.class))).thenAnswer(
            invocation -> this.entities.get(invocation.getArgument(0))
        );
        this.ready = true;
    }

    public ServerLevel level() {
        return this.level;
    }

    public EffectSavedData store() {
        return this.store;
    }

    public DimensionDataStorage storage() {
        return this.storage;
    }

    public LevelEffectRuntime installRuntime() {
        if (this.runtime == null) {
            this.runtime = TestEffectRuntime.install(
                this.level,
                new SavedDataEffectStore(this.level),
                new RecordingPacketSink(),
                new TestBudgetSource()
            );
        }
        return this.runtime;
    }

    public LevelEffectRuntime installRuntime(
        EffectStore store,
        EffectPacketSink packets,
        EffectBudgetSource budgets
    ) {
        if (this.runtime != null) {
            throw new IllegalStateException("A test runtime is already installed");
        }
        this.runtime = TestEffectRuntime.install(this.level, store, packets, budgets);
        return this.runtime;
    }

    public void setGameTime(long gameTime) {
        this.gameTime = gameTime;
    }

    public void advanceGameTime() {
        this.gameTime++;
    }

    public void setGroundY(int groundY) {
        this.groundY = groundY;
    }

    public void setBlock(BlockPos position, BlockState state) {
        this.blocks.put(position, state);
        if (isInsideSection(position)) {
            this.section.setBlockState(
                position.getX() & 15,
                position.getY() & 15,
                position.getZ() & 15,
                state,
                false
            );
        }
    }

    public BlockState blockState(BlockPos position) {
        if (isInsideSection(position)) {
            return this.section.getBlockState(
                position.getX() & 15,
                position.getY() & 15,
                position.getZ() & 15
            );
        }
        return this.blocks.getOrDefault(position, Blocks.AIR.defaultBlockState());
    }

    public void fillSection(BlockState state) {
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    this.section.setBlockState(x, y, z, state, false);
                }
            }
        }
    }

    public void putEntity(UUID id, Entity entity) {
        this.entities.put(id, entity);
    }

    public void removeEntity(UUID id) {
        this.entities.remove(id);
    }

    public void assertNoUnstubbedCalls() {
        Set<String> stubbed = mockingDetails(this.level).getStubbings().stream()
            .map(TestServerLevel::stubbedMethod)
            .collect(Collectors.toSet());
        List<String> unstubbed = this.calls.stream()
            .filter(method -> !stubbed.contains(method))
            .distinct()
            .toList();
        assertTrue(unstubbed.isEmpty(), () -> "Unstubbed ServerLevel calls: " + unstubbed);
    }

    public void close() {
        if (this.runtime != null) {
            TestEffectRuntime.uninstall(this.level);
            this.runtime = null;
        }
        assertNoUnstubbedCalls();
    }

    private Object answer(InvocationOnMock invocation) throws Throwable {
        if (this.ready && invocation.getMethod().getDeclaringClass() != Object.class) {
            this.calls.add(invocation.getMethod().getName());
        }
        return Answers.RETURNS_DEFAULTS.answer(invocation);
    }

    private boolean isInsideSection(BlockPos position) {
        return position.getX() >= 0 && position.getX() < 16
            && position.getY() >= 0 && position.getY() < 16
            && position.getZ() >= 0 && position.getZ() < 16;
    }

    private static String stubbedMethod(Stubbing stubbing) {
        return stubbing.getInvocation().getMethod().getName();
    }
}
