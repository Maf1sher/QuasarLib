package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import mafisher.quasar.effect.scale.SegmentGridLayout;
import mafisher.quasar.effect.scale.SegmentLink;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class ClientSegmentGroupTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final UUID COORDINATOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final SegmentGridLayout LAYOUT = new SegmentGridLayout(4, 4, 8.0);

    @Test
    void derivesTheCoordinatorBaseFromEveryCell() {
        EffectTransform base = new EffectTransform(new EffectPoint(10.0, 64.0, -5.0), 37.0F, 12.0F, -8.0F);

        for (int row = 0; row < LAYOUT.rows(); row++) {
            for (int column = 0; column < LAYOUT.columns(); column++) {
                SegmentLink link = link(column, row);
                EffectTransform segment = LAYOUT.transform(base, column, row);
                EffectTransform derived = ClientSegmentGroup.baseFromSegment(link, segment);

                assertEquals(base.position().x(), derived.position().x(), 1.0E-9);
                assertEquals(base.position().y(), derived.position().y(), 1.0E-9);
                assertEquals(base.position().z(), derived.position().z(), 1.0E-9);
                assertEquals(base.yaw(), derived.yaw());
                assertEquals(base.pitch(), derived.pitch());
                assertEquals(base.roll(), derived.roll());
            }
        }
    }

    @Test
    void storesOneBaseHistoryForAllSegments() {
        EffectTransform first = base(0.0, 64.0, 0.0, 15.0F);
        EffectTransform second = base(6.0, 64.0, 3.0, 75.0F);
        ClientSegmentGroup group = group(first, 0L, 1.5);

        assertEquals(LAYOUT.segmentCount(), group.memberCount());

        group.applyBase(second, 20L);

        for (int row = 0; row < LAYOUT.rows(); row++) {
            for (int column = 0; column < LAYOUT.columns(); column++) {
                ClientSegmentGroup.SegmentMember member = member(column, row, first, 1.5);
                VisualEffectInstance instance = group.renderInstance(member);

                assertTransformEquals(LAYOUT.transform(first, column, row), instance.previousTransform());
                assertTransformEquals(LAYOUT.transform(second, column, row), instance.currentTransform());
            }
        }

        group.applyBase(first, 10L);
        VisualEffectInstance afterStale = group.renderInstance(member(0, 0, first, 1.5));
        assertTransformEquals(LAYOUT.transform(second, 0, 0), afterStale.currentTransform());

        group.applyBase(first, 20L);
        VisualEffectInstance afterEqual = group.renderInstance(member(0, 0, first, 1.5));
        assertTransformEquals(LAYOUT.transform(first, 0, 0), afterEqual.currentTransform());
    }

    @Test
    void interpolatesSegmentTransformsLikeStandaloneInstances() {
        EffectTransform first = base(0.0, 64.0, 0.0, 15.0F);
        EffectTransform second = base(6.0, 64.0, 3.0, 75.0F);
        ClientSegmentGroup group = group(first, 10L, 1.5);
        group.applyBase(second, 20L);
        ClientSegmentGroup.SegmentMember member = groupMember(group, 2, 1);

        VisualEffectInstance instance = group.renderInstance(member);
        EffectTransform actual = instance.interpolatedTransform(15L, 0.5F);
        EffectTransform expected = EffectTransform.interpolate(
            LAYOUT.transform(first, 2, 1),
            LAYOUT.transform(second, 2, 1),
            0.45F
        );

        assertTransformEquals(expected, actual);
    }

    @Test
    void indexBoundsCoverEverySegmentEndpoint() {
        EffectTransform first = base(0.0, 64.0, 0.0, 15.0F);
        EffectTransform second = base(6.0, 64.0, 3.0, 75.0F);
        ClientSegmentGroup group = group(first, 0L, 1.5);
        group.applyBase(second, 20L);

        AABB bounds = group.indexBounds();
        for (int row = 0; row < LAYOUT.rows(); row++) {
            for (int column = 0; column < LAYOUT.columns(); column++) {
                assertContainsSphere(bounds, LAYOUT.transform(first, column, row), 1.5);
                assertContainsSphere(bounds, LAYOUT.transform(second, column, row), 1.5);
            }
        }
    }

    @Test
    void cellRangeNeverMissesAnIntersectingPath() {
        EffectTransform first = base(0.0, 64.0, 0.0, 15.0F);
        EffectTransform second = base(6.0, 64.0, 3.0, 75.0F);
        ClientSegmentGroup group = group(first, 0L, 1.0);
        group.applyBase(second, 20L);

        Random random = new Random(0x5EEDL);
        for (int queryIndex = 0; queryIndex < 200; queryIndex++) {
            double centerX = random.nextDouble(-30.0, 30.0);
            double centerZ = random.nextDouble(-30.0, 30.0);
            double halfX = random.nextDouble(0.1, 7.0);
            double halfZ = random.nextDouble(0.1, 7.0);
            AABB query = new AABB(
                centerX - halfX, 60.0, centerZ - halfZ,
                centerX + halfX, 70.0, centerZ + halfZ
            );

            Set<UUID> visited = new HashSet<>();
            group.forEachCellIn(query, member -> visited.add(member.instanceId()));

            for (int row = 0; row < LAYOUT.rows(); row++) {
                for (int column = 0; column < LAYOUT.columns(); column++) {
                    ClientSegmentGroup.SegmentMember member = groupMember(group, column, row);
                    EffectTransform from = LAYOUT.transform(first, column, row);
                    EffectTransform to = LAYOUT.transform(second, column, row);
                    for (int step = 0; step <= 20; step++) {
                        double progress = step / 20.0;
                        double x = from.position().x() + (to.position().x() - from.position().x()) * progress;
                        double z = from.position().z() + (to.position().z() - from.position().z()) * progress;
                        if (distanceToAabbSqr(x, z, query) <= 1.0) {
                            assertTrue(visited.contains(member.instanceId()),
                                "Intersecting segment was not included in the cell range");
                        }
                    }
                }
            }
        }
    }

    @Test
    void recomputesMaxRadiusAfterRemoval() {
        EffectTransform base = base(0.0, 64.0, 0.0, 0.0F);
        ClientSegmentGroup group = group(base, 0L, 1.0);
        group.add(member(0, 0, base, 100.0));

        assertEquals(100.0, group.maxSpatialRadius());
        group.remove(link(0, 0));
        assertEquals(1.0, group.maxSpatialRadius());
    }

    @Test
    void removesMembersAndDropsEmptyState() {
        EffectTransform base = base(0.0, 64.0, 0.0, 0.0F);
        ClientSegmentGroup group = group(base, 0L, 1.0);
        SegmentLink link = link(2, 1);
        ClientSegmentGroup.SegmentMember removed = group.remove(link);

        assertTrue(removed != null);
        assertEquals(LAYOUT.segmentCount() - 1, group.memberCount());
        assertFalse(group.isEmpty());

        for (int row = 0; row < LAYOUT.rows(); row++) {
            for (int column = 0; column < LAYOUT.columns(); column++) {
                group.remove(link(column, row));
            }
        }
        assertTrue(group.isEmpty());
    }

    private static ClientSegmentGroup group(EffectTransform base, long gameTime, double radius) {
        ClientSegmentGroup group = new ClientSegmentGroup(LAYOUT, base, gameTime);
        for (int row = 0; row < LAYOUT.rows(); row++) {
            for (int column = 0; column < LAYOUT.columns(); column++) {
                group.add(member(column, row, base, radius));
            }
        }
        return group;
    }

    private static ClientSegmentGroup.SegmentMember groupMember(
        ClientSegmentGroup group,
        int column,
        int row
    ) {
        ClientSegmentGroup.SegmentMember[] found = new ClientSegmentGroup.SegmentMember[1];
        group.forEachCellIn(AABB.INFINITE, member -> {
            if (member.link().column() == column && member.link().row() == row) {
                found[0] = member;
            }
        });
        return found[0];
    }

    private static ClientSegmentGroup.SegmentMember member(
        int column,
        int row,
        EffectTransform base,
        double radius
    ) {
        SegmentLink link = link(column, row);
        EffectTransform transform = LAYOUT.transform(base, column, row);
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            EFFECT_ID,
            UUID.randomUUID(),
            DIMENSION,
            transform.position().x(),
            transform.position().y(),
            transform.position().z(),
            transform.yaw(),
            transform.pitch(),
            transform.roll(),
            2.0F,
            100,
            0L,
            1234L,
            link
        );
        return new ClientSegmentGroup.SegmentMember(payload.instanceId(), payload, link, radius);
    }

    private static SegmentLink link(int column, int row) {
        return new SegmentLink(COORDINATOR_ID, row * LAYOUT.columns() + column, LAYOUT);
    }

    private static EffectTransform base(double x, double y, double z, float yaw) {
        return new EffectTransform(new EffectPoint(x, y, z), yaw, 0.0F, 0.0F);
    }

    private static void assertContainsSphere(AABB bounds, EffectTransform transform, double radius) {
        EffectPoint position = transform.position();
        assertTrue(bounds.minX <= position.x() - radius + 1.0E-6);
        assertTrue(bounds.minY <= position.y() - radius + 1.0E-6);
        assertTrue(bounds.minZ <= position.z() - radius + 1.0E-6);
        assertTrue(bounds.maxX >= position.x() + radius - 1.0E-6);
        assertTrue(bounds.maxY >= position.y() + radius - 1.0E-6);
        assertTrue(bounds.maxZ >= position.z() + radius - 1.0E-6);
    }

    private static double distanceToAabbSqr(double x, double z, AABB box) {
        double dx = Math.max(Math.max(box.minX - x, 0.0), x - box.maxX);
        double dz = Math.max(Math.max(box.minZ - z, 0.0), z - box.maxZ);
        return dx * dx + dz * dz;
    }

    private static void assertTransformEquals(EffectTransform expected, EffectTransform actual) {
        assertEquals(expected.position().x(), actual.position().x(), 1.0E-9);
        assertEquals(expected.position().y(), actual.position().y(), 1.0E-9);
        assertEquals(expected.position().z(), actual.position().z(), 1.0E-9);
        assertEquals(expected.yaw(), actual.yaw(), 1.0E-6F);
        assertEquals(expected.pitch(), actual.pitch(), 1.0E-6F);
        assertEquals(expected.roll(), actual.roll(), 1.0E-6F);
    }
}
