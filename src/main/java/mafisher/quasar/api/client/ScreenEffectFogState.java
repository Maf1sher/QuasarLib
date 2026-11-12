package mafisher.quasar.api.client;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable snapshot of the active fog state for a screen operation.
 *
 * <p>The values come from the active render stack; QuasarLib never implements
 * its own fog model. {@link #NONE} means that no usable fog was active.</p>
 */
public record ScreenEffectFogState(float start, float end, Vec3 color) {
    /** No active fog. */
    public static final ScreenEffectFogState NONE =
        new ScreenEffectFogState(Float.MAX_VALUE, Float.MAX_VALUE, Vec3.ZERO);

    public ScreenEffectFogState {
        Objects.requireNonNull(color, "color");
        if (!Float.isFinite(start) || !Float.isFinite(end)
            || !Double.isFinite(color.x) || !Double.isFinite(color.y) || !Double.isFinite(color.z)) {
            throw new IllegalArgumentException("Fog state values must be finite");
        }
    }

    /** Returns whether the state describes a usable distance range. */
    public boolean active() {
        return Float.isFinite(this.start) && Float.isFinite(this.end) && this.end > this.start
            && this.start < Float.MAX_VALUE;
    }

    /**
     * Returns a smooth 0..1 fog factor for {@code distance}. The factor is 0
     * when fog is inactive and grows toward 1 at {@link #end()}.
     */
    public float factor(double distance) {
        if (!this.active() || !Double.isFinite(distance)) {
            return 0.0F;
        }
        float value = (float)((distance - this.start) / Math.max(this.end - this.start, 1.0E-4));
        value = Math.max(0.0F, Math.min(1.0F, value));
        return value * value * (3.0F - 2.0F * value);
    }
}
