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
}
