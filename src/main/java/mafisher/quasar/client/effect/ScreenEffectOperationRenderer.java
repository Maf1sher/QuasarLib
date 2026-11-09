package mafisher.quasar.client.effect;

/**
 * Client-only renderer for one registered screen-space operation.
 *
 * <p>Callbacks run on the client render thread after the compositor has
 * captured scene color and rendered the effect mask. A renderer must write its
 * final result to {@link ScreenEffectOperationContext#destination()}.</p>
 */
@FunctionalInterface
public interface ScreenEffectOperationRenderer {
    void render(ScreenEffectOperationContext context);

    /**
     * Minimum scene-composition scale this operation needs, in {@code (0, 1]}.
     * The compositor uses the largest request in the frame so a screen-space
     * effect is not fed a heavily downscaled scene. Returning 0 keeps the
     * quality-tier scale.
     */
    default float compositionScaleFloor() {
        return 0.0F;
    }
}
