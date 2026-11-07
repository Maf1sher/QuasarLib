package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GpuMeshCacheTest {
    @Test
    void usesSixIndicesPerQuad() {
        assertEquals(6, GpuMeshCache.quadIndexCount(4));
        assertEquals(12, GpuMeshCache.quadIndexCount(8));
        assertEquals(6_144, GpuMeshCache.quadIndexCount(4_096));
    }
}
