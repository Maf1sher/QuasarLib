package mafisher.quasar.effect.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * Explicit bounds for the largest effect payloads. The clientbound custom-payload packet enforces
 * a one-megabyte body limit; these caps keep QuasarLib well below it and reject pathological
 * resource identifiers before they can reach the wire.
 */
@ApiStatus.Internal
public final class EffectPayloadLimits {
    /** Maximum characters in an effect or dimension ID carried by a start or resume payload. */
    public static final int MAX_IDENTIFIER_LENGTH = 256;
    /** Maximum encoded body of one standalone visual start. */
    public static final int MAX_VISUAL_START_BYTES = 1_024;
    /** Maximum encoded body of one segmented coordinator start. */
    public static final int MAX_SEGMENTED_START_BYTES = 2_048;
    /** Maximum encoded body of a full resume snapshot. */
    public static final int MAX_RESUME_BYTES = 768 * 1_024;
    /** Maximum transform entries in one batch payload. */
    public static final int MAX_BATCH_UPDATES = 256;
    /** Maximum encoded body of one transform batch payload. */
    public static final int MAX_BATCH_BYTES = 16 * 1_024;

    private EffectPayloadLimits() {
    }

    /** Rejects identifiers that would make a start or resume payload unbounded. */
    public static void requireIdentifier(ResourceLocation identifier, String label) {
        Objects.requireNonNull(identifier, label);
        if (identifier.toString().length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(
                label + " must be at most " + MAX_IDENTIFIER_LENGTH + " characters"
            );
        }
    }

    /** Encodes {@code value} into a temporary buffer and returns its exact body size. */
    static <T> int encodedSize(StreamCodec<? super ByteBuf, T> codec, T value) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, value);
            return buffer.readableBytes();
        } finally {
            buffer.release();
        }
    }
}
