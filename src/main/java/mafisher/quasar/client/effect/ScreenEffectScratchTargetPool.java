package mafisher.quasar.client.effect;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;

/** Reuses color-only scratch targets acquired by screen operations during a frame. */
final class ScreenEffectScratchTargetPool {
    private static final int NEAREST_FILTER = 0x2600;

    private final List<Entry> entries = new ArrayList<>();
    private final Set<Entry> inUse = Collections.newSetFromMap(new IdentityHashMap<>());

    TextureTarget acquire(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Scratch target dimensions must be positive");
        }
        for (Entry entry : this.entries) {
            if (!this.inUse.contains(entry) && entry.target.width == width && entry.target.height == height) {
                this.inUse.add(entry);
                return entry.target;
            }
        }
        TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        target.setFilterMode(NEAREST_FILTER);
        Entry entry = new Entry(target);
        this.entries.add(entry);
        this.inUse.add(entry);
        return target;
    }

    void release(TextureTarget target) {
        Entry entry = this.find(target);
        if (entry == null) {
            throw new IllegalArgumentException("Target was not acquired from this compositor");
        }
        this.inUse.remove(entry);
    }

    /** Marks every target free after an operation, including targets the operation did not release. */
    void releaseOperation() {
        this.inUse.clear();
    }

    void releaseAll() {
        List<TextureTarget> targets = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries) {
            targets.add(entry.target);
        }
        this.entries.clear();
        this.inUse.clear();
        for (TextureTarget target : targets) {
            releaseTarget(target);
        }
    }

    private Entry find(TextureTarget target) {
        for (Entry entry : this.entries) {
            if (entry.target == target) {
                return entry;
            }
        }
        return null;
    }

    private static void releaseTarget(TextureTarget target) {
        if (RenderSystem.isOnRenderThreadOrInit()) {
            target.destroyBuffers();
        } else {
            RenderSystem.recordRenderCall(target::destroyBuffers);
        }
    }

    private static final class Entry {
        private final TextureTarget target;

        private Entry(TextureTarget target) {
            this.target = target;
        }
    }
}
