package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdentityStringCacheTest {
    @Test
    void resolvesEachKeyAndNameOnce() {
        IdentityStringCache<Object, String> cache = new IdentityStringCache<>();
        Object shader = new Object();
        AtomicInteger resolutions = new AtomicInteger();

        String first = cache.get(shader, "uniform", (key, name) -> {
            resolutions.incrementAndGet();
            return "second";
        });
        String second = cache.get(shader, "uniform", (key, name) -> {
            resolutions.incrementAndGet();
            return "third";
        });

        assertEquals("second", first);
        assertEquals(first, second);
        assertEquals(1, resolutions.get());
    }

    @Test
    void keepsDifferentKeysAndNamesIndependent() {
        IdentityStringCache<Object, String> cache = new IdentityStringCache<>();
        Object first = new Object();
        Object second = new Object();
        AtomicInteger resolutions = new AtomicInteger();

        cache.get(first, "a", (key, name) -> resolved(resolutions, "first:a"));
        cache.get(first, "b", (key, name) -> resolved(resolutions, "first:b"));
        cache.get(second, "a", (key, name) -> resolved(resolutions, "second:a"));

        assertEquals(3, resolutions.get());
        assertEquals("first:a", cache.get(first, "a", (key, name) -> "wrong"));
        assertEquals("first:b", cache.get(first, "b", (key, name) -> "wrong"));
        assertEquals("second:a", cache.get(second, "a", (key, name) -> "wrong"));
    }

    @Test
    void clearForgetsEveryValue() {
        IdentityStringCache<Object, String> cache = new IdentityStringCache<>();
        Object shader = new Object();

        cache.get(shader, "uniform", (key, name) -> "before");
        cache.clear();

        assertEquals("after", cache.get(shader, "uniform", (key, name) -> "after"));
    }

    private static String resolved(AtomicInteger resolutions, String value) {
        resolutions.incrementAndGet();
        return value;
    }
}
