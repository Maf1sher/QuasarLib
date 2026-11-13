package mafisher.quasar.apicheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalInt;
import mafisher.quasar.api.gameplay.world.EffectBlockHit;
import mafisher.quasar.api.gameplay.world.EffectBlockSample;
import mafisher.quasar.api.gameplay.world.EffectRaycastResult;
import mafisher.quasar.api.gameplay.world.EffectRaycastStatus;
import mafisher.quasar.api.gameplay.world.EffectScanResult;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.api.gameplay.tick.EffectTickCounters;
import mafisher.quasar.api.gameplay.world.EffectWorldQuery;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Compiles from outside the implementation package to pin the public world-query surface. */
class PublicWorldQueryApiVisibilityTest {
    @Test
    void exposesWorldQueryThroughTheContext() throws NoSuchMethodException {
        Method world = GameplayEffectContext.class.getMethod("world");

        assertEquals(EffectWorldQuery.class, world.getReturnType());
    }

    @Test
    void exposesPointQueriesAndRaycasts() throws NoSuchMethodException {
        assertNotNull(EffectWorldQuery.class.getMethod(
            "height", Heightmap.Types.class, int.class, int.class
        ));
        assertNotNull(EffectWorldQuery.class.getMethod("blockState", BlockPos.class));
        assertNotNull(EffectWorldQuery.class.getMethod("raycast", Vec3.class, Vec3.class));
        assertNotNull(EffectWorldQuery.class.getMethod(
            "scan", BlockPos.class, BlockPos.class, int.class
        ));
    }

    @Test
    void exposesResultTypesAndStatuses() throws NoSuchMethodException {
        assertEquals(OptionalInt.class, EffectWorldQuery.class.getMethod(
            "height", Heightmap.Types.class, int.class, int.class
        ).getReturnType());
        assertEquals(Optional.class, recordAccessor(EffectRaycastResult.class, "hit"));

        assertEquals(5, EffectRaycastStatus.values().length);
        assertEquals(EffectRaycastStatus.HIT, EffectRaycastStatus.valueOf("HIT"));
        assertEquals(5, EffectBlockHit.class.getRecordComponents().length);
        assertEquals(2, EffectBlockSample.class.getRecordComponents().length);
        assertEquals(3, EffectScanResult.class.getRecordComponents().length);
    }

    @Test
    void keepsBudgetAndCounterExtensionsVisible() throws NoSuchMethodException {
        assertTrue(EffectWorldQuery.MAX_RAYCAST_DISTANCE > 0.0);
        assertTrue(EffectWorldQuery.MAX_SCAN_BLOCKS > 0);
        assertNotNull(EffectTickBudget.class.getMethod("maxWorldSamples"));
        assertNotNull(EffectTickCounters.class.getMethod("worldSamples"));
    }

    private static Class<?> recordAccessor(Class<?> type, String name) {
        try {
            return type.getMethod(name).getReturnType();
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("Missing record accessor " + name, exception);
        }
    }
}
