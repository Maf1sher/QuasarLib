package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
    void resolvesTheSurfaceColumnFromTheAimedBlock() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.clip(any(ClipContext.class))).thenReturn(new BlockHitResult(
            HIT_LOCATION, Direction.UP, HIT_POS, false
        ));
        when(level.isInWorldBounds(HIT_POS)).thenReturn(true);
        when(level.hasChunkAt(HIT_POS)).thenReturn(true);
        when(level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0)).thenReturn(21);
        when(level.getMinBuildHeight()).thenReturn(-64);

        Optional<TargetColumn> resolved = ContentOrbitalLaserTarget.resolve(
            level, new Vec3(0.5, 24.0, 0.5), new Vec3(0.0, -1.0, 0.0), null
        );

        assertTrue(resolved.isPresent());
        assertEquals(new TargetColumn(0, 0, 21), resolved.get());
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
