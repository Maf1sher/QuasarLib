package mafisher.quasar.client.effect;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Render-thread cache keyed by object identity and a stable string name. Used for shader-owned resources that stay
 * valid until the shader instance is replaced or the cache is cleared.
 */
final class IdentityStringCache<K, V> {
    private final Map<K, Map<String, V>> values = new IdentityHashMap<>();

    V get(K key, String name, BiFunction<K, String, V> resolver) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(resolver, "resolver");
        Map<String, V> perKey = this.values.computeIfAbsent(key, ignored -> new HashMap<>());
        V cached = perKey.get(name);
        if (cached != null) {
            return cached;
        }
        V resolved = resolver.apply(key, name);
        if (resolved != null) {
            perKey.put(name, resolved);
        }
        return resolved;
    }

    void clear() {
        this.values.clear();
    }
}
