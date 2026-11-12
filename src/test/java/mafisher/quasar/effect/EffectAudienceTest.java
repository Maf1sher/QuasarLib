package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectAudienceTest {
    @Test
    void validatesNearbyRadiusAndExposesDimensionWidePolicy() {
        assertEquals(128.0, ((EffectAudience.Nearby)EffectAudience.nearby(128.0)).radius());
        assertThrows(IllegalArgumentException.class, () -> EffectAudience.nearby(-1.0));
        assertThrows(IllegalArgumentException.class, () -> EffectAudience.nearby(512.1));
        assertThrows(IllegalArgumentException.class, () -> EffectAudience.nearby(Double.NaN));
    }

    @Test
    void nearbyIncludesTheBoundaryAndExpandsByPadding() {
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer boundary = player(level, new Vec3(10.0, 64.0, 0.0));
        ServerPlayer outside = player(level, new Vec3(10.1, 64.0, 0.0));
        EffectAudience audience = EffectAudience.nearby(10.0);

        assertTrue(audience.includes(level, new Vec3(0.0, 64.0, 0.0), 0.0, boundary));
        assertFalse(audience.includes(level, new Vec3(0.0, 64.0, 0.0), 0.0, outside));
        assertTrue(audience.includes(level, new Vec3(0.0, 64.0, 0.0), 0.1, outside));
    }

    @Test
    void nearbyCollectUsesThePaddedRadius() {
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer near = player(level, new Vec3(10.0, 64.0, 0.0));
        ServerPlayer far = player(level, new Vec3(20.0, 64.0, 0.0));
        when(level.players()).thenReturn(List.of(near, far));
        Map<UUID, ServerPlayer> collected = new LinkedHashMap<>();

        EffectAudience.nearby(10.0).collect(level, new Vec3(0.0, 64.0, 0.0), 5.0, collected);

        assertEquals(Map.of(near.getUUID(), near), collected);
    }

    @Test
    void dimensionWideCollectsEveryPlayerAndIncludesOnlyTheSameLevel() {
        ServerLevel level = mock(ServerLevel.class);
        ServerLevel other = mock(ServerLevel.class);
        ServerPlayer first = player(level, Vec3.ZERO);
        ServerPlayer second = player(level, new Vec3(100.0, 64.0, 0.0));
        ServerPlayer elsewhere = player(other, Vec3.ZERO);
        when(level.players()).thenReturn(List.of(first, second));
        Map<UUID, ServerPlayer> collected = new LinkedHashMap<>();

        EffectAudience.dimensionWide().collect(level, Vec3.ZERO, 0.0, collected);

        assertEquals(2, collected.size());
        assertTrue(EffectAudience.dimensionWide().includes(level, Vec3.ZERO, 0.0, first));
        assertFalse(EffectAudience.dimensionWide().includes(level, Vec3.ZERO, 0.0, elsewhere));
    }

    private static ServerPlayer player(ServerLevel level, Vec3 position) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.level()).thenReturn(level);
        when(player.position()).thenReturn(position);
        return player;
    }
}
