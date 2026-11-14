package mafisher.quasar.client.effect.render;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/** Caches the instance payload locations once per shader program. */
final class InstanceUniformLocationCache<K> {
    static final String INSTANCE_BUFFER = "InstanceBuffer";
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
            resolver.location(key, INSTANCE_BUFFER),
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

    record Locations(int instanceBuffer, int instances, int colors, int data) {
        boolean usesBuffer() {
            return this.instanceBuffer >= 0;
        }

        boolean usesUniformArrays() {
            return this.instances >= 0 && this.colors >= 0 && this.data >= 0;
        }

        boolean isComplete() {
            return usesBuffer() || usesUniformArrays();
        }
    }

    @FunctionalInterface
    interface Resolver<K> {
        int location(K key, String name);
    }
}
