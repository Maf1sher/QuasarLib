package mafisher.quasar.api.compat;

/** Exposes the package-private compat reset to tests in other packages. */
public final class CompatTestReset {
    private CompatTestReset() {
    }

    public static void reset() {
        CompatRegistry.clearForTesting();
    }
}
