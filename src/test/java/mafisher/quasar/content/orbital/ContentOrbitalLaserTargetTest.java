package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import mafisher.quasar.content.orbital.ContentOrbitalLaserTarget.TargetColumn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentOrbitalLaserTargetTest {
    private static final BlockPos HIT_POS = new BlockPos(0, 19, 0);
    private static final Vec3 HIT_LOCATION = new Vec3(0.5, 20.0, 0.5);

    @Test
    void resolvesTheAimedBlockColumn() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.clip(any(ClipContext.class))).thenReturn(new BlockHitResult(
            HIT_LOCATION, Direction.UP, HIT_POS, false
        ));
        when(level.isInWorldBounds(HIT_POS)).thenReturn(true);
        when(level.hasChunkAt(HIT_POS)).thenReturn(true);

        Optional<TargetColumn> resolved = ContentOrbitalLaserTarget.resolve(
            level, new Vec3(0.5, 24.0, 0.5), new Vec3(0.0, -1.0, 0.0), null
        );

        assertTrue(resolved.isPresent());
        assertEquals(new TargetColumn(0, 0, 20), resolved.get());
    }

    @Test
    void keepsTheMarkerOnTheAimedBlockUnderACanopy() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.clip(any(ClipContext.class))).thenReturn(new BlockHitResult(
            HIT_LOCATION, Direction.UP, HIT_POS, false
        ));
        when(level.isInWorldBounds(HIT_POS)).thenReturn(true);
        when(level.hasChunkAt(HIT_POS)).thenReturn(true);

        Optional<TargetColumn> resolved = ContentOrbitalLaserTarget.resolve(
            level, new Vec3(0.5, 24.0, 0.5), new Vec3(0.0, -1.0, 0.0), null
        );

        assertTrue(resolved.isPresent());
        assertEquals(new TargetColumn(0, 0, 20), resolved.get());
        verify(level, never()).getHeight(any(Heightmap.Types.class), anyInt(), anyInt());
    }

    @Test
    void samplesTheHighestLoadedSurfaceInsideTheRadius() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.hasChunkAt(anyInt(), anyInt())).thenReturn(true);
        when(level.hasChunkAt(1, 0)).thenReturn(false);
        when(level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0)).thenReturn(10);
        when(level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 1)).thenReturn(15);
        when(level.getHeight(Heightmap.Types.WORLD_SURFACE, 1, 1)).thenReturn(30);

        assertEquals(15, ContentOrbitalLaserTarget.highestFirstFreeY(level, 0, 0, 10, 1));
        assertEquals(30, ContentOrbitalLaserTarget.highestFirstFreeY(level, 0, 0, 10, 2));
        assertEquals(10, ContentOrbitalLaserTarget.highestFirstFreeY(level, 0, 0, 10, 0));
        verify(level, never()).getHeight(Heightmap.Types.WORLD_SURFACE, 1, 0);
    }

    @Test
    void usesWorldSurfaceSoPlantsRaiseTheCeiling() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.hasChunkAt(anyInt(), anyInt())).thenReturn(true);
        when(level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0)).thenReturn(15);
        when(level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0)).thenReturn(16);

        assertEquals(16, ContentOrbitalLaserTarget.highestFirstFreeY(level, 0, 0, 15, 0));
        verify(level, never()).getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
    }

    @Test
    void rejectsMissesUnloadedColumnsAndZeroRays() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.clip(any(ClipContext.class))).thenReturn(BlockHitResult.miss(
            HIT_LOCATION, Direction.UP, HIT_POS
        ));

        assertTrue(ContentOrbitalLaserTarget.resolve(
            level, new Vec3(0.5, 24.0, 0.5), new Vec3(0.0, -1.0, 0.0), null
        ).isEmpty());
        assertTrue(ContentOrbitalLaserTarget.resolve(
            level, new Vec3(0.5, 24.0, 0.5), Vec3.ZERO, null
        ).isEmpty());

        when(level.clip(any(ClipContext.class))).thenReturn(new BlockHitResult(
            HIT_LOCATION, Direction.UP, HIT_POS, false
        ));
        when(level.isInWorldBounds(HIT_POS)).thenReturn(true);
        when(level.hasChunkAt(HIT_POS)).thenReturn(false);

        assertTrue(ContentOrbitalLaserTarget.resolve(
            level, new Vec3(0.5, 24.0, 0.5), new Vec3(0.0, -1.0, 0.0), null
        ).isEmpty());
    }
}
