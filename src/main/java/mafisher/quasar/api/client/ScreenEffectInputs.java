package mafisher.quasar.api.client;

/**
 * Optional full-frame captures a screen operation reads.
 *
 * <p>The pre-translucent depth copy is not optional: every screen-effect mask uses it so glass and water do not hide
 * the effect. These flags only cover the extra opaque-color and post-translucent depth copies.</p>
 */
public record ScreenEffectInputs(boolean opaqueColor, boolean foregroundDepth) {
    /** No optional captures. */
    public static final ScreenEffectInputs NONE = new ScreenEffectInputs(false, false);

    /** Both optional captures. */
    public static final ScreenEffectInputs ALL = new ScreenEffectInputs(true, true);

    /** Returns the union of this request and {@code other}. */
    public ScreenEffectInputs union(ScreenEffectInputs other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        if (ALL.equals(this) || ALL.equals(other)) {
            return ALL;
        }
        if (NONE.equals(this)) {
            return other;
        }
        if (NONE.equals(other)) {
            return this;
        }
        return new ScreenEffectInputs(
            this.opaqueColor || other.opaqueColor,
            this.foregroundDepth || other.foregroundDepth
        );
    }
}
