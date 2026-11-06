package mafisher.quasar.effect.scale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
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
    void rejectsIndexesOutsideTheGrid() {
        SegmentGridLayout layout = new SegmentGridLayout(2, 2, 8.0);

        assertThrows(IllegalArgumentException.class, () -> new SegmentLink(UUID.randomUUID(), -1, layout));
        assertThrows(IllegalArgumentException.class, () -> new SegmentLink(UUID.randomUUID(), 4, layout));
    }
}
