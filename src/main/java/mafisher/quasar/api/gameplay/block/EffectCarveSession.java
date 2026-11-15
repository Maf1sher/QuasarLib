package mafisher.quasar.api.gameplay.block;

import java.util.Objects;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.world.EffectBlockCursor;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One scoped, resumable carve driven by an {@link EffectCarvePlan}.
 *
 * <p>The session owns one {@link EffectBulkBlockEditor} and an optional exact core cursor. It
 * advances the core first, then the bulk front, and never edits after {@link
 * EffectCarvePlan#workEnd()}. "Edits finished" is separate from the authored visual front: an
 * editor that finishes early keeps reporting the planned front until the work window closes.</p>
 *
 * <p>The session never force-loads a chunk. The editor and cursor treat unloaded columns as
 * deferred work. Protection, journaling, section swaps, light, and client resends remain the
 * editor's responsibility.</p>
 */
@SuppressWarnings({"PMD.GodClass", "PMD.TooManyMethods", "PMD.CognitiveComplexity"})
public final class EffectCarveSession implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectCarveSession.class);
    private static final int STATE_VERSION = 1;
    private static final int MAX_PENDING_HEADROOM = 12_000;
    private static final int MAX_CORE_BATCH = 4_096;

    private final GameplayEffectContext context;
    private final EffectCarveSpec spec;
    private final EffectCarvePlan plan;
    private final EffectBulkBlockEditor editor;

    @Nullable
    private EffectBlockCursor coreCursor;
    private boolean coreDone;
    private boolean coreSkipped;
    private boolean deadlineReached;
    private boolean incomplete;
    private boolean completed;
    private boolean syncReleased;
    private boolean closed;
    private float frontScale;
    private double frontY;
    private boolean coreSkipLogged;
    private boolean deadlineLogged;

    private EffectCarveSession(
        GameplayEffectContext context,
        EffectCarveSpec spec,
        EffectCarvePlan plan,
        EffectBulkBlockEditor editor
    ) {
        this.context = context;
        this.spec = spec;
        this.plan = plan;
        this.editor = editor;
        this.frontY = plan.frontTopY();
        this.coreDone = !plan.hasCoreWork();
        this.context.onClose(() -> {
            this.closed = true;
            this.coreCursor = null;
        });
    }

    public static EffectCarveSession start(
        GameplayEffectContext context,
        EffectCarveSpec spec,
        EffectCarvePlan plan
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(plan, "plan");
        if (!plan.requestedVolume().equals(spec.volume())) {
            throw new IllegalArgumentException("Carve plan was built for a different volume");
        }
        return new EffectCarveSession(
            context, spec, plan, createEditor(context, spec, plan)
        );
    }

    /** Convenience form that plans the authored volume before starting. */
    public static EffectCarveSession start(
        GameplayEffectContext context,
        EffectCarveSpec spec
    ) {
        return start(context, spec, EffectCarvePlan.plan(context, spec));
    }

    /**
     * Restores one session from its persisted state. Unknown versions, changed volumes, and
     * unreadable cursors fall back to a fresh, idempotent carve instead of corrupting the world.
     */
    public static EffectCarveSession restore(
        GameplayEffectContext context,
        EffectCarveSpec spec,
        CompoundTag tag
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(tag, "tag");
        if (tag.getInt("version") != STATE_VERSION) {
            LOGGER.warn("Ignoring an unknown carve-session schema; starting a fresh carve");
            return start(context, spec);
        }
        EffectCarvePlan plan = tag.contains("plan")
            ? CarvePlanNbt.restore(context, spec, tag.getCompound("plan"))
            : EffectCarvePlan.plan(context, spec);
        EffectBulkBlockEditor editor = null;
        if (tag.contains("editor")) {
            EffectBulkBlockEditor restored = EffectBulkBlockEditor.restore(
                context, tag.getCompound("editor"), spec.ordering()
            );
            if (restored.volume().equals(plan.volume())
                && restored.ordering() == spec.ordering()) {
                editor = restored;
            } else {
                restored.close(false);
            }
        }
        if (editor == null) {
            editor = createEditor(context, spec, plan);
        }

        EffectCarveSession session = new EffectCarveSession(context, spec, plan, editor);
        session.coreDone = !plan.hasCoreWork()
            || (tag.contains("core_done") && tag.getBoolean("core_done"));
        session.coreSkipped = tag.getBoolean("core_skipped");
        session.deadlineReached = tag.getBoolean("deadline_reached");
        session.incomplete = tag.getBoolean("incomplete");
        session.completed = tag.getBoolean("complete");
        session.syncReleased = tag.getBoolean("sync_released");
        session.frontScale = Math.max(0.0F, Math.min(1.0F, tag.getFloat("front_scale")));
        session.frontY = restoredFrontY(plan, context, tag);
        if (!session.coreDone
            && tag.contains("core_cursor")
            && plan.coreVolume() != null) {
            EffectBlockCursor cursor = EffectBlockCursor.restore(
                context.world(), plan.coreVolume(), tag.getCompound("core_cursor")
            );
            if (cursor.finished()) {
                session.coreDone = true;
            } else {
                session.coreCursor = cursor;
            }
        }
        if (session.syncReleased) {
            session.editor.releaseSync();
        }
        return session;
    }

    /**
     * Advances the core and the bulk front for this server tick. Once the work window closes no
     * block edits run; the remaining reveal is flushed and the session stays terminal.
     */
    public void tick() {
        if (this.closed) {
            return;
        }
        float progress = this.effectProgress();
        if (this.spec.ordering() == EffectBulkBlockEditor.Ordering.TOP_DOWN) {
            this.tickTopDown(progress);
        } else {
            this.tickRadial(progress);
        }
        this.updateCompletion();
    }

    private void tickRadial(float progress) {
        if (!this.coreDone && !this.coreSkipped) {
            if (progress >= this.plan.growthStart() && this.plan.hasCoreWork()) {
                this.skipCore(progress);
            } else if (progress >= this.spec.coreStartProgress()) {
                this.tickCore();
            }
        }
        if (progress < this.plan.growthStart()) {
            return;
        }
        if (progress < this.plan.workEnd()) {
            if (!this.deadlineReached) {
                double front = this.plan.scaleAt(progress);
                if (!this.editor.progress().finished()) {
                    this.editor.tickToScale(front, this.plan.sectionBudget());
                }
                this.frontScale = Math.max(this.frontScale, (float)front);
                if (this.editor.progress().finished()) {
                    this.releaseSync();
                }
            }
        } else {
            this.reachDeadline(progress);
            if (!this.incomplete) {
                this.frontScale = Math.max(this.frontScale, 1.0F);
            }
        }
    }

    private void tickTopDown(float progress) {
        if (progress < this.plan.growthStart()) {
            return;
        }
        if (progress < this.plan.workEnd()) {
            if (!this.deadlineReached) {
                double front = this.plan.frontYAt(progress);
                if (!this.editor.progress().finished()) {
                    this.editor.tickToFront(front, this.plan.sectionBudget());
                }
                this.frontY = Math.min(this.frontY, front);
                if (this.editor.progress().finished()) {
                    this.releaseSync();
                }
            }
        } else {
            this.reachDeadline(progress);
            if (!this.incomplete) {
                this.frontY = this.plan.frontBottomY();
            }
        }
    }

    /** Normalized radial visual front inside the effective volume. */
    public float frontScale() {
        return this.frontScale;
    }

    /** Absolute world height of the planned top-down front. */
    public double frontY() {
        return this.frontY;
    }

    /** Absolute world height of the editor cursor; meaningful for {@code TOP_DOWN} sessions. */
    public double carvedFrontY() {
        return this.editor.frontY();
    }

    /** Whether every planned edit and reveal has finished. */
    public boolean complete() {
        return this.completed;
    }

    /** Whether the deadline stopped edits before all planned solid sections were carved. */
    public boolean incomplete() {
        return this.incomplete;
    }

    /** Whether the optional exact core pre-pass finished. */
    public boolean coreComplete() {
        return this.coreDone;
    }

    /** Whether the core pre-pass ran out of time and was skipped. */
    public boolean coreSkipped() {
        return this.coreSkipped;
    }

    /** Whether the bulk editor has processed its whole section cursor. */
    public boolean editsFinished() {
        return this.editor.progress().finished();
    }

    /** Whether the deferred client reveal has been flushed. */
    public boolean syncReleased() {
        return this.syncReleased;
    }

    public EffectCarvePlan plan() {
        return this.plan;
    }

    public EffectBulkBlockEditor.Progress progress() {
        return this.editor.progress();
    }

    /** Flushes every column still waiting for its client resend and relight. */
    public void releaseSync() {
        if (this.syncReleased) {
            return;
        }
        this.syncReleased = true;
        this.editor.releaseSync();
    }

    /** Persists the plan, editor cursor, reveal throttles, core cursor, and terminal flags. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", STATE_VERSION);
        CompoundTag planTag = new CompoundTag();
        CarvePlanNbt.write(this.plan, planTag);
        tag.put("plan", planTag);
        tag.put("editor", this.editor.save());
        if (this.coreCursor != null && !this.coreCursor.finished()) {
            tag.put("core_cursor", this.coreCursor.save());
        }
        tag.putBoolean("core_done", this.coreDone);
        tag.putBoolean("core_skipped", this.coreSkipped);
        tag.putBoolean("deadline_reached", this.deadlineReached);
        tag.putBoolean("incomplete", this.incomplete);
        tag.putBoolean("complete", this.completed);
        tag.putBoolean("sync_released", this.syncReleased);
        tag.putFloat("front_scale", this.frontScale);
        tag.putDouble("front_y", this.frontY);
        return tag;
    }

    /**
     * Closes the session. {@code releaseSync} flushes pending reveals before the editor releases
     * its tickets, matching the closing phase of a cinematic.
     */
    @Override
    public void close() {
        this.close(true);
    }

    public void close(boolean releaseSync) {
        if (this.closed) {
            return;
        }
        if (releaseSync) {
            this.releaseSync();
        }
        this.coreCursor = null;
        this.editor.close(false);
        this.closed = true;
    }

    private void tickCore() {
        if (this.coreCursor == null) {
            this.coreCursor = this.context.world().cursor(this.plan.coreVolume());
        }
        if (this.coreCursor.finished()) {
            this.coreDone = true;
            this.coreCursor = null;
            return;
        }
        int headroom = (int)Math.max(
            0, MAX_PENDING_HEADROOM - this.context.pendingBlockChanges()
        );
        int batch = Math.min(
            Math.min(this.plan.coreBlockBudget(), MAX_CORE_BATCH), headroom
        );
        if (batch <= 0) {
            return;
        }
        this.coreCursor.visit(
            batch,
            state -> !state.isAir(),
            (position, state) -> this.queueCoreBlock(position)
        );
        if (this.coreCursor.finished()) {
            this.coreDone = true;
            this.coreCursor = null;
        }
    }

    private void queueCoreBlock(BlockPos position) {
        if (position.getY() <= this.context.level().getMinBuildHeight()) {
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean interior = this.isInteriorCoreBlock(position);
        this.context.queueBlockChange(
            interior
                ? BlockChange.airWithoutNeighbourUpdates(position)
                : new BlockChange(position, air)
        );
    }

    private boolean isInteriorCoreBlock(BlockPos position) {
        EffectBlockVolume core = this.plan.coreVolume();
        if (core == null) {
            return false;
        }
        double x = position.getX() + 0.5;
        double y = position.getY() + 0.5;
        double z = position.getZ() + 0.5;
        return insideX(core, x, y, z) && insideY(core, x, y, z) && insideZ(core, x, y, z);
    }

    private static boolean insideX(EffectBlockVolume core, double x, double y, double z) {
        return core.contains(x - 1.0, y, z) && core.contains(x + 1.0, y, z);
    }

    private static boolean insideY(EffectBlockVolume core, double x, double y, double z) {
        return core.contains(x, y - 1.0, z) && core.contains(x, y + 1.0, z);
    }

    private static boolean insideZ(EffectBlockVolume core, double x, double y, double z) {
        return core.contains(x, y, z - 1.0) && core.contains(x, y, z + 1.0);
    }

    private void skipCore(float progress) {
        this.coreSkipped = true;
        this.coreCursor = null;
        if (!this.coreSkipLogged) {
            this.coreSkipLogged = true;
            LOGGER.warn(
                "Carve core pre-pass did not finish by progress {}; continuing with the smaller "
                    + "main volume",
                progress
            );
        }
    }

    private void reachDeadline(float progress) {
        if (this.deadlineReached) {
            return;
        }
        this.deadlineReached = true;
        boolean pending = !this.editor.progress().finished()
            && this.editor.hasPendingSolidSections();
        if (pending) {
            this.incomplete = true;
            if (this.spec.ordering() == EffectBulkBlockEditor.Ordering.TOP_DOWN) {
                this.frontY = Math.max(this.frontY, this.editor.frontY());
            } else {
                this.frontScale = Math.min(this.frontScale, (float)this.editor.frontScale());
            }
            if (!this.deadlineLogged) {
                this.deadlineLogged = true;
                LOGGER.warn(
                    "Carve stopped at its reveal deadline at progress {}: {} of {} sections, "
                        + "{} blocks",
                    progress,
                    this.editor.progress().completedSections(),
                    this.editor.progress().totalSections(),
                    this.editor.progress().clearedBlocks()
                );
            }
        }
        this.releaseSync();
    }

    private void updateCompletion() {
        if (this.completed) {
            return;
        }
        if (this.editor.progress().finished()) {
            this.completed = true;
            this.incomplete = false;
            this.releaseSync();
        } else if (this.deadlineReached && !this.incomplete) {
            this.completed = true;
        }
    }

    private float effectProgress() {
        int duration = Math.max(1, this.context.spec().phases().totalDurationTicks());
        return Math.max(0.0F, Math.min(1.0F, this.context.ageTicks() / (float)duration));
    }

    private static EffectBulkBlockEditor createEditor(
        GameplayEffectContext context,
        EffectCarveSpec spec,
        EffectCarvePlan plan
    ) {
        return EffectBulkBlockEditor.create(
            context,
            new EffectBulkBlockEditor.Spec(
                plan.volume(),
                spec.replacement(),
                spec.removeBlockEntities(),
                spec.syncPolicy(),
                0.0,
                spec.journal()
            ),
            spec.ordering()
        );
    }

    private static double restoredFrontY(
        EffectCarvePlan plan,
        GameplayEffectContext context,
        CompoundTag tag
    ) {
        if (tag.contains("front_y")) {
            return clampFrontY(plan, tag.getDouble("front_y"));
        }
        if (plan.ordering() != EffectBulkBlockEditor.Ordering.TOP_DOWN) {
            return plan.frontTopY();
        }
        int duration = Math.max(1, context.spec().phases().totalDurationTicks());
        float progress = Math.max(0.0F, Math.min(1.0F, context.ageTicks() / (float)duration));
        return plan.frontYAt(progress);
    }

    private static double clampFrontY(EffectCarvePlan plan, double frontY) {
        if (!Double.isFinite(frontY)) {
            return plan.frontTopY();
        }
        return Math.max(plan.frontBottomY(), Math.min(plan.frontTopY(), frontY));
    }
}
