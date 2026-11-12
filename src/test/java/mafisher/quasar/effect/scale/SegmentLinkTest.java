package mafisher.quasar.effect.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import mafisher.quasar.api.scale.SegmentGridLayout;
import mafisher.quasar.api.scale.SegmentLink;
import org.junit.jupiter.api.Test;

class SegmentLinkTest {
    @Test
    void resolvesColumnAndRowFromTheFlatIndex() {
        SegmentGridLayout layout = new SegmentGridLayout(4, 3, 8.0);
        SegmentLink link = new SegmentLink(UUID.randomUUID(), 6, layout);

        assertEquals(2, link.column());
        assertEquals(1, link.row());
    }

    @Test
    void resolvesTheLayerFromTheFlatIndex() {
        SegmentGridLayout layout = new SegmentGridLayout(2, 2, 3, 8.0, 4.0);
        SegmentLink link = new SegmentLink(UUID.randomUUID(), 9, layout);

        assertEquals(1, link.column());
        assertEquals(0, link.row());
        assertEquals(2, link.layer());
    }

    @Test
    void rejectsIndexesOutsideTheGrid() {
        SegmentGridLayout layout = new SegmentGridLayout(2, 2, 8.0);

        assertThrows(IllegalArgumentException.class, () -> new SegmentLink(UUID.randomUUID(), -1, layout));
        assertThrows(IllegalArgumentException.class, () -> new SegmentLink(UUID.randomUUID(), 4, layout));
    }
}
