package mafisher.quasar.effect.block;

/**
 * Server-side protection hook for one block change. Callbacks run at application time and must be
 * read-only; returning {@code false} vetoes the change without applying it.
 */
@FunctionalInterface
public interface BlockProtection {
    /**
     * Decides whether one queued change may modify the world. An exception also vetoes the change
     * and is logged by the engine.
     */
    boolean allows(BlockProtectionContext context);
}
