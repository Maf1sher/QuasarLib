package mafisher.quasar.client.effect.inspect;

import org.jetbrains.annotations.ApiStatus;

/**
 * Pure pixel mapping for the instance-buffer thumbnail. One column is one float of the uploaded
 * payload (four matrix columns, one RGBA color, and one effect-data vector), one row is one
 * instance. The mapping is intentionally qualitative: it shows payload shape and outliers without
 * pretending to be a numeric readout. Pixels are packed ABGR for {@code NativeImage}.
 */
@ApiStatus.Internal
public final class InspectBufferThumbnail {
    public static final int FLOATS_PER_ROW = 24;
    public static final int MAX_ROWS = 32;
    public static final int CELL_PIXELS = 4;
    public static final int WIDTH = FLOATS_PER_ROW * CELL_PIXELS;
    public static final int HEIGHT = MAX_ROWS * CELL_PIXELS;
    private static final int BACKGROUND = 0xFF101010;
    private static final int ZERO = 0xFF505050;
    private static final int INVALID = 0xFFFF00FF;
    private static final double LOG_SCALE = Math.log1p(16.0);

    private InspectBufferThumbnail() {
    }

    /**
     * Maps {@code rowCount} rows of {@link #FLOATS_PER_ROW} floats into an ABGR image.
     *
     * @param rows source rows, row-major
     * @param rowCount rows to draw, clamped to {@link #MAX_ROWS}
     * @return a new {@code WIDTH * HEIGHT} ABGR image
     */
    public static int[] pixels(float[] rows, int rowCount) {
        if (rows == null) {
            throw new NullPointerException("rows");
        }
        int usedRows = Math.max(0, Math.min(MAX_ROWS, rowCount));
        if (rows.length < usedRows * FLOATS_PER_ROW) {
            throw new IllegalArgumentException("Instance thumbnail rows are shorter than the row count");
        }
        int[] pixels = new int[WIDTH * HEIGHT];
        java.util.Arrays.fill(pixels, BACKGROUND);
        for (int row = 0; row < usedRows; row++) {
            for (int column = 0; column < FLOATS_PER_ROW; column++) {
                int color = color(rows[row * FLOATS_PER_ROW + column]);
                fillCell(pixels, column, row, color);
            }
        }
        return pixels;
    }

    private static void fillCell(int[] pixels, int column, int row, int color) {
        int startX = column * CELL_PIXELS;
        int startY = row * CELL_PIXELS;
        for (int y = 0; y < CELL_PIXELS - 1; y++) {
            int offset = (startY + y) * WIDTH + startX;
            for (int x = 0; x < CELL_PIXELS - 1; x++) {
                pixels[offset + x] = color;
            }
        }
    }

    private static int color(float value) {
        if (!Float.isFinite(value)) {
            return INVALID;
        }
        if (Math.abs(value) < 1.0E-6F) {
            return ZERO;
        }
        double intensity = Math.min(1.0, Math.log1p(Math.abs(value)) / LOG_SCALE);
        int level = 48 + (int)Math.round(intensity * 207.0);
        if (value < 0.0F) {
            return 0xFF000000 | ((level / 4) << 16) | ((level / 3) << 8) | level;
        }
        return 0xFF000000 | ((level / 3) << 16) | (level << 8) | (level / 4);
    }
}
