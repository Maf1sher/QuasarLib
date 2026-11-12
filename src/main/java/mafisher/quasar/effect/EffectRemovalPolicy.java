package mafisher.quasar.effect;

/**
 * Whether closing an effect lifecycle deletes its persisted descriptor or keeps it for a later
 * restore. Explicit stops delete; level unload and server shutdown keep descriptors so the next
 * load can resume them.
 */
public enum EffectRemovalPolicy {
    DELETE,
    KEEP_FOR_RESTORE;

    /** True when the policy deletes the persisted descriptor. */
    public boolean deletesDescriptor() {
        return this == DELETE;
    }
}
