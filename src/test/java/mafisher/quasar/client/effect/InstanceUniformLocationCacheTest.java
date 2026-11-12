package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InstanceUniformLocationCacheTest {
    @Test
    void resolvesAllThreeLocationsOncePerShader() {
        InstanceUniformLocationCache<Object> cache = new InstanceUniformLocationCache<>();
        Object shader = new Object();
        AtomicInteger calls = new AtomicInteger();

        InstanceUniformLocationCache.Locations first = cache.get(shader, (key, name) -> {
            calls.incrementAndGet();
            return switch (name) {
                case InstanceUniformLocationCache.INSTANCES -> 4;
                case InstanceUniformLocationCache.INSTANCE_COLORS -> 5;
                default -> 6;
            };
        });
        InstanceUniformLocationCache.Locations second = cache.get(shader, (key, name) -> {
            calls.incrementAndGet();
            return -1;
        });

        assertEquals(first, second);
        assertEquals(3, calls.get());
        assertTrue(first.isComplete());
        assertEquals(4, first.instances());
        assertEquals(5, first.colors());
        assertEquals(6, first.data());
    }

    @Test
    void cachesMissingLocationsAndKeepsShadersIndependent() {
        InstanceUniformLocationCache<Object> cache = new InstanceUniformLocationCache<>();
        Object firstShader = new Object();
        Object secondShader = new Object();

        InstanceUniformLocationCache.Locations missing = cache.get(firstShader, (key, name) -> -1);
        assertFalse(missing.isComplete());

        InstanceUniformLocationCache.Locations found = cache.get(secondShader, (key, name) -> 7);
        assertTrue(found.isComplete());
        assertEquals(7, found.instances());
    }

    @Test
    void clearForgetsCachedLocations() {
        InstanceUniformLocationCache<Object> cache = new InstanceUniformLocationCache<>();
        Object shader = new Object();

        cache.get(shader, (key, name) -> 1);
        cache.clear();
        InstanceUniformLocationCache.Locations resolved = cache.get(shader, (key, name) -> 2);

        assertEquals(2, resolved.instances());
        assertEquals(2, resolved.colors());
        assertEquals(2, resolved.data());
    }
}
