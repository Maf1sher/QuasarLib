package mafisher.quasar.client.effect.render;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Caches the three instance uniform locations once per shader program. */
final class InstanceUniformLocationCache<K> {
    static final String INSTANCES = "Instances[0]";
    static final String INSTANCE_COLORS = "InstanceColors[0]";
    static final String INSTANCE_DATA = "InstanceData[0]";

    private final Map<K, Locations> locations = new IdentityHashMap<>();

    Locations get(K key, Resolver<K> resolver) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(resolver, "resolver");
        Locations cached = this.locations.get(key);
        if (cached != null) {
            return cached;
        }
        Locations resolved = new Locations(
            resolver.location(key, INSTANCES),
            resolver.location(key, INSTANCE_COLORS),
            resolver.location(key, INSTANCE_DATA)
        );
        this.locations.put(key, resolved);
        return resolved;
    }

    void clear() {
        this.locations.clear();
    }

    record Locations(int instances, int colors, int data) {
        boolean isComplete() {
            return this.instances >= 0 && this.colors >= 0 && this.data >= 0;
        }
    }

    @FunctionalInterface
    interface Resolver<K> {
        int location(K key, String name);
    }
}
