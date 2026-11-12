package mafisher.quasar.client.effect;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/** Reuses color-only scratch targets acquired by screen operations during a frame. */
final class ScreenEffectScratchTargetPool {
    private static final int NEAREST_FILTER = 0x2600;

    private final BiFunction<Integer, Integer, TextureTarget> allocator;
    private final Consumer<TextureTarget> releaser;
    private final List<Entry> entries = new ArrayList<>();
    private final Set<Entry> inUse = Collections.newSetFromMap(new IdentityHashMap<>());
    private int frame;

    ScreenEffectScratchTargetPool() {
        this(ScreenEffectScratchTargetPool::createTarget, ScreenEffectScratchTargetPool::releaseTarget);
    }

    ScreenEffectScratchTargetPool(BiFunction<Integer, Integer, TextureTarget> allocator,
        Consumer<TextureTarget> releaser) {
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.releaser = Objects.requireNonNull(releaser, "releaser");
    }

    /**
     * Starts a composition frame and releases targets that were not used in the current or previous
     * frame. The grace frame prevents a resize or quality change from accumulating stale targets while
     * still allowing alternating quality tiers to reuse their buffers.
     */
    void beginFrame() {
        this.frame++;
        int earliestRetainedFrame = this.frame - 1;
        Iterator<Entry> iterator = this.entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (!this.inUse.contains(entry) && entry.lastUsedFrame < earliestRetainedFrame) {
                iterator.remove();
                this.releaser.accept(entry.target);
            }
        }
    }

    TextureTarget acquire(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Scratch target dimensions must be positive");
        }
        for (Entry entry : this.entries) {
            if (!this.inUse.contains(entry) && entry.width == width && entry.height == height) {
                this.inUse.add(entry);
                entry.lastUsedFrame = this.frame;
                return entry.target;
            }
        }
        TextureTarget target = Objects.requireNonNull(this.allocator.apply(width, height), "scratch target");
        target.setFilterMode(NEAREST_FILTER);
        Entry entry = new Entry(target, width, height);
        entry.lastUsedFrame = this.frame;
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
            this.releaser.accept(target);
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

    private static TextureTarget createTarget(int width, int height) {
        return new TextureTarget(width, height, false, Minecraft.ON_OSX);
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
        private final int width;
        private final int height;
        private int lastUsedFrame;

        private Entry(TextureTarget target, int width, int height) {
            this.target = target;
            this.width = width;
            this.height = height;
        }
    }
}
