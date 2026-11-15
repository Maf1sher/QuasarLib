package mafisher.quasar.client.effect.inspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InspectBufferThumbnailTest {
    private static final int BACKGROUND = 0xFF101010;

    @Test
    void emptyRowsProduceABackgroundImage() {
        int[] pixels = InspectBufferThumbnail.pixels(new float[0], 0);

        assertEquals(InspectBufferThumbnail.WIDTH * InspectBufferThumbnail.HEIGHT, pixels.length);
        assertEquals(BACKGROUND, pixels[0]);
    }

    @Test
    void positiveAndNegativeFloatsUseDifferentChannels() {
        float[] rows = new float[InspectBufferThumbnail.FLOATS_PER_ROW];
        rows[0] = 1.0F;
        rows[1] = -1.0F;

        int[] pixels = InspectBufferThumbnail.pixels(rows, 1);
        int positive = pixels[0];
        int negative = pixels[InspectBufferThumbnail.CELL_PIXELS];

        assertNotEquals(positive, negative);
        assertTrue((positive & 0xFF) < ((positive >> 8) & 0xFF));
        assertTrue((negative & 0xFF) > ((negative >> 8) & 0xFF));
    }

    @Test
    void invalidFloatsAreMagenta() {
        float[] rows = new float[InspectBufferThumbnail.FLOATS_PER_ROW];
        rows[0] = Float.NaN;

        int[] pixels = InspectBufferThumbnail.pixels(rows, 1);

        assertEquals(0xFFFF00FF, pixels[0]);
    }

    @Test
    void rowsAreCappedAndValidated() {
        float[] rows = new float[InspectBufferThumbnail.FLOATS_PER_ROW
            * (InspectBufferThumbnail.MAX_ROWS + 4)];
        int[] pixels = InspectBufferThumbnail.pixels(rows, InspectBufferThumbnail.MAX_ROWS + 4);

        assertEquals(InspectBufferThumbnail.WIDTH * InspectBufferThumbnail.HEIGHT, pixels.length);

        boolean rejected = false;
        try {
            InspectBufferThumbnail.pixels(new float[1], 2);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }
}
