package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class ClientEffectSpatialIndexTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void findsEntriesAcrossCellsOnce() {
        ClientEffectSpatialIndex index = new ClientEffectSpatialIndex();
        ClientEffectSpatialIndex.Key key = ClientEffectSpatialIndex.Key.instance(FIRST);
        index.put(key, new AABB(-100.0, 60.0, -100.0, 100.0, 70.0, 100.0));

        List<ClientEffectSpatialIndex.Key> found = query(index, new AABB(-10.0, 0.0, -10.0, 10.0, 128.0, 10.0));

        assertEquals(List.of(key), found);
    }

    @Test
    void movingAnEntryReplacesItsOldCells() {
        ClientEffectSpatialIndex index = new ClientEffectSpatialIndex();
        ClientEffectSpatialIndex.Key key = ClientEffectSpatialIndex.Key.group(FIRST);
        index.put(key, new AABB(-5.0, 60.0, -5.0, 5.0, 70.0, 5.0));
        index.put(key, new AABB(95.0, 60.0, 95.0, 105.0, 70.0, 105.0));

        assertTrue(query(index, new AABB(-10.0, 0.0, -10.0, 10.0, 128.0, 10.0)).isEmpty());
        assertEquals(List.of(key), query(index, new AABB(90.0, 0.0, 90.0, 110.0, 128.0, 110.0)));
    }

    @Test
    void removesAndClearsEntries() {
        ClientEffectSpatialIndex index = new ClientEffectSpatialIndex();
        ClientEffectSpatialIndex.Key first = ClientEffectSpatialIndex.Key.instance(FIRST);
        ClientEffectSpatialIndex.Key second = ClientEffectSpatialIndex.Key.instance(SECOND);
        index.put(first, new AABB(-5.0, 60.0, -5.0, 5.0, 70.0, 5.0));
        index.put(second, new AABB(95.0, 60.0, 95.0, 105.0, 70.0, 105.0));

        index.remove(first);
        assertTrue(query(index, new AABB(-10.0, 0.0, -10.0, 10.0, 128.0, 10.0)).isEmpty());
        assertEquals(1, index.size());

        index.clear();
        assertEquals(0, index.size());
        assertTrue(query(index, AABB.INFINITE).isEmpty());
    }

    @Test
    void ignoresEntriesOutsideTheQuery() {
        ClientEffectSpatialIndex index = new ClientEffectSpatialIndex();
        index.put(ClientEffectSpatialIndex.Key.instance(FIRST),
            new AABB(-5.0, 60.0, -5.0, 5.0, 70.0, 5.0));

        assertTrue(query(index, new AABB(500.0, 0.0, 500.0, 600.0, 128.0, 600.0)).isEmpty());
    }

    @Test
    void largeQueriesFallBackToEntryScanWithoutHanging() {
        ClientEffectSpatialIndex index = new ClientEffectSpatialIndex();
        ClientEffectSpatialIndex.Key key = ClientEffectSpatialIndex.Key.instance(FIRST);
        index.put(key, new AABB(-5.0, 60.0, -5.0, 5.0, 70.0, 5.0));

        AABB query = new AABB(-1.0E9, Double.NEGATIVE_INFINITY, -1.0E9,
            1.0E9, Double.POSITIVE_INFINITY, 1.0E9);

        assertEquals(List.of(key), query(index, query));
    }

    @Test
    void queriesIgnoreTheVerticalAxis() {
        ClientEffectSpatialIndex index = new ClientEffectSpatialIndex();
        ClientEffectSpatialIndex.Key key = ClientEffectSpatialIndex.Key.instance(FIRST);
        index.put(key, new AABB(-5.0, 2000.0, -5.0, 5.0, 2100.0, 5.0));

        AABB query = new AABB(-10.0, Double.NEGATIVE_INFINITY, -10.0,
            10.0, Double.POSITIVE_INFINITY, 10.0);

        assertEquals(List.of(key), query(index, query));
    }

    @Test
    void acceptsOnlySaneIndexRadii() {
        assertTrue(ClientEffectSpatialIndex.isIndexableRadius(0.0));
        assertTrue(ClientEffectSpatialIndex.isIndexableRadius(64.0));
        assertTrue(ClientEffectSpatialIndex.isIndexableRadius(ClientEffectSpatialIndex.MAX_INDEX_RADIUS));
        assertFalse(ClientEffectSpatialIndex.isIndexableRadius(ClientEffectSpatialIndex.MAX_INDEX_RADIUS + 1.0));
        assertFalse(ClientEffectSpatialIndex.isIndexableRadius(Double.POSITIVE_INFINITY));
        assertFalse(ClientEffectSpatialIndex.isIndexableRadius(Double.NaN));
        assertFalse(ClientEffectSpatialIndex.isIndexableRadius(-1.0));
    }

    @Test
    void acceptsOnlySaneIndexBounds() {
        assertTrue(ClientEffectSpatialIndex.isIndexableBounds(
            new AABB(-100.0, 60.0, -100.0, 100.0, 70.0, 100.0)
        ));
        assertFalse(ClientEffectSpatialIndex.isIndexableBounds(
            new AABB(-10_000_000.0, 60.0, -10.0, 10_000_000.0, 70.0, 10.0)
        ));
        assertFalse(ClientEffectSpatialIndex.isIndexableBounds(AABB.INFINITE));
        assertThrows(IllegalArgumentException.class, () -> {
            ClientEffectSpatialIndex index = new ClientEffectSpatialIndex();
            index.put(ClientEffectSpatialIndex.Key.instance(FIRST),
                new AABB(-10_000_000.0, 60.0, -10.0, 10_000_000.0, 70.0, 10.0));
        });
    }

    private static List<ClientEffectSpatialIndex.Key> query(ClientEffectSpatialIndex index, AABB region) {
        List<ClientEffectSpatialIndex.Key> found = new ArrayList<>();
        index.query(region, entry -> found.add(entry.key()));
        return found;
    }
}
