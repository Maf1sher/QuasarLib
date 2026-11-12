package mafisher.quasar.client.effect;

/** Gates one visibility scan per render tick and forces a rebuild after an effect-state mutation. */
final class FrameSnapshotCache {
    private int tick = Integer.MIN_VALUE;
    private boolean dirty = true;

    boolean shouldBuild(int tick) {
        if (!this.dirty && tick == this.tick) {
            return false;
        }
        this.tick = tick;
        this.dirty = false;
        return true;
    }

    int tick() {
        return this.tick;
    }

    void invalidate() {
        this.dirty = true;
    }
}
