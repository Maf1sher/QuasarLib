package mafisher.quasar.api.gameplay;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectAttachment;
import mafisher.quasar.api.effect.EffectLifetimePolicy;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectRemovalPolicy;
import mafisher.quasar.api.effect.EffectStartStatus;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.VisualEffectHandle;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.effect.VisualEffectStartResult;
import mafisher.quasar.api.effect.budget.EffectLimits;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One scope-owned visual/audio child of a gameplay session.
 *
 * <p>The child is derived from the session: its duration and seed match the parent, its progress
 * starts at the parent age, and its scale is the parent scale times
 * {@link CompanionSpec#scaleMultiplier()}. A companion is server-thread only and there is exactly
 * one per gameplay session.</p>
 *
 * <p>{@link #start} starts the child once. {@link #restore} never starts a new child: the persisted
 * visual is restored independently and adopted on the first tick after gameplay resume, so a
 * restart cannot create a duplicate. A soft visual-budget cull leaves the gameplay session alive
 * and is reported once through {@link #status()} and {@link #rejection()}.</p>
 *
 * <p>The default parent frame is the session anchor with zero rotation. Call
 * {@link #tick(GameplayEffectContext, EffectTransform)} with a moving parent frame to keep the
 * child in sync; static parents send no transform traffic.</p>
 */
@SuppressWarnings("PMD.TooManyMethods")
public final class EffectCompanion {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectCompanion.class);
    private static final String STATE_KEY = "quasar_companion";
    private static final int STATE_VERSION = 1;
    private static final String CHILD_ID_SALT = "quasar_lib:companion/";

    private final GameplayEffectContext context;
    private final CompanionSpec spec;
    private final EffectTransform localTransform;
    private final EffectTransform defaultParentFrame;
    private final VisualEffectHandle childHandle;
    private final float childScale;
    private final boolean cancelWithScope;

    private Status status;
    @Nullable
    private EffectStartStatus rejection;
    @Nullable
    private EffectTransform lastParentFrame;
    @Nullable
    private EffectTransform lastChildTransform;
    private boolean rejectionLogged;
    private boolean lostLogged;
    private boolean closed;

    private EffectCompanion(
        GameplayEffectContext context,
        CompanionSpec spec,
        UUID childId,
        Status status,
        @Nullable EffectStartStatus rejection
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.localTransform = spec.localTransform();
        this.defaultParentFrame = new EffectTransform(
            new EffectPoint(
                context.spec().anchor().x,
                context.spec().anchor().y,
                context.spec().anchor().z
            ),
            0.0F,
            0.0F,
            0.0F
        );
        this.lastParentFrame = this.defaultParentFrame;
        this.childHandle = new VisualEffectHandle(Objects.requireNonNull(childId, "childId"));
        this.childScale = childScale(context, spec);
        this.cancelWithScope = spec.lifetime() == EffectLifetimePolicy.CANCEL_WITH_SCOPE;
        this.status = Objects.requireNonNull(status, "status");
        this.rejection = rejection;
        this.context.onClose(() -> this.closed = true);
    }

    /**
     * Starts the session's child immediately. A repeated call does not create a second child; it
     * returns a wrapper for the existing companion state instead.
     */
    public static EffectCompanion start(GameplayEffectContext context, CompanionSpec spec) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(spec, "spec");
        SavedState saved = readState(context);
        if (saved != null) {
            EffectCompanion existing = new EffectCompanion(
                context, spec, saved.childId(), saved.status(), saved.rejection()
            );
            existing.refreshExisting();
            return existing;
        }
        return startFresh(context, spec);
    }

    /**
     * Reattaches to a companion after gameplay resume. This never starts a child; the restored
     * visual is adopted and reported on the first tick.
     */
    public static EffectCompanion restore(GameplayEffectContext context, CompanionSpec spec) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(spec, "spec");
        SavedState saved = readState(context);
        if (saved == null) {
            return new EffectCompanion(
                context, spec, deterministicChildId(context.sessionId()), Status.PENDING, null
            );
        }
        Status restored = switch (saved.status()) {
            case STOPPED -> Status.STOPPED;
            case PENDING, ACTIVE, UNAVAILABLE -> Status.PENDING;
        };
        EffectCompanion companion = new EffectCompanion(
            context, spec, saved.childId(), restored, saved.rejection()
        );
        if (restored == Status.PENDING && isActive(companion)) {
            companion.activate(false);
        }
        return companion;
    }

    /** Advances the child state and applies the fixed session-anchor parent frame. */
    public void tick(GameplayEffectContext context) {
        this.tick(context, null);
    }

    /**
     * Advances the child state and synchronizes a moving parent frame. A transform update is sent
     * only when the frame or the composed child transform changed.
     */
    public void tick(GameplayEffectContext context, @Nullable EffectTransform parentFrame) {
        Objects.requireNonNull(context, "context");
        if (context != this.context) {
            throw new IllegalArgumentException("Companion belongs to a different gameplay session");
        }
        if (this.closed || this.status == Status.STOPPED) {
            return;
        }
        if (this.status == Status.PENDING) {
            this.resolvePending();
        }
        if (this.status == Status.ACTIVE && !isActive(this)) {
            this.markUnavailable();
        }
        if (this.status != Status.ACTIVE) {
            return;
        }
        EffectTransform frame = parentFrame == null ? this.defaultParentFrame : parentFrame;
        if (frame.equals(this.lastParentFrame)) {
            return;
        }
        EffectTransform child = EffectAttachment.resolve(
            frame, this.localTransform, this.spec.rotationInheritance()
        );
        if (this.lastChildTransform == null || !child.equals(this.lastChildTransform)) {
            this.context.scope().updateVisual(this.childHandle.instanceId(), child);
            this.lastChildTransform = child;
        }
        this.lastParentFrame = frame;
    }

    /** Current lifecycle state of the child. */
    public Status status() {
        return this.status;
    }

    /**
     * Why the child is unavailable, when the start or restore reported an explicit soft-budget or
     * attachment outcome.
     */
    @Nullable
    public EffectStartStatus rejection() {
        return this.rejection;
    }

    /** Stable identity the child visual and its audio plan use. */
    public UUID childId() {
        return this.childHandle.instanceId();
    }

    /** Stops the child and its audio on an explicit cancel. Scope close keeps its own policy. */
    public void stop() {
        if (this.closed || this.status == Status.STOPPED) {
            return;
        }
        this.status = Status.STOPPED;
        this.rejection = null;
        if (this.cancelWithScope) {
            this.context.scope().stopVisual(this.childHandle.instanceId());
        } else {
            VisualEffectService.stop(
                this.context.level(), this.childHandle, EffectRemovalPolicy.DELETE
            );
        }
        this.persistState();
    }

    /** Why a companion is no longer presenting. */
    public enum Status {
        /** Restored and waiting for the child visual to be adopted after resume. */
        PENDING,
        /** The child visual is active. */
        ACTIVE,
        /** The child was culled by the soft budget or is no longer active. */
        UNAVAILABLE,
        /** The caller explicitly stopped the child. */
        STOPPED
    }

    private static EffectCompanion startFresh(GameplayEffectContext context, CompanionSpec spec) {
        EffectCompanion companion = new EffectCompanion(
            context, spec, deterministicChildId(context.sessionId()), Status.PENDING, null
        );
        EffectTransform parent = companion.defaultParentFrame;
        EffectTransform child = EffectAttachment.resolve(
            parent, companion.localTransform, spec.rotationInheritance()
        );
        VisualEffectSpec visual = new VisualEffectSpec(
            spec.effectId(),
            new Vec3(child.position().x(), child.position().y(), child.position().z()),
            child.yaw(),
            child.pitch(),
            child.roll(),
            companion.childScale,
            context.spec().phases().totalDurationTicks(),
            context.spec().seed(),
            spec.audience(),
            spec.audio(),
            spec.priority()
        );
        long startGameTime = context.level().getGameTime() - context.ageTicks();
        EffectPersistenceOptions options = companion.cancelWithScope
            ? EffectPersistenceOptions.DEFAULT.withOwnerSessionId(context.sessionId())
            : EffectPersistenceOptions.DEFAULT;
        VisualEffectStartResult result = VisualEffectService.startWithId(
            context.level(), visual, companion.childId(), startGameTime, options
        );
        if (!result.active() || !VisualEffectService.isActive(context.level(), result.handle())) {
            companion.status = Status.UNAVAILABLE;
            companion.rejection = result.status();
            companion.reportRejection();
            companion.persistState();
            return companion;
        }
        if (companion.cancelWithScope) {
            context.scope().adoptVisual(companion.childId(), result.handle());
        }
        companion.lastChildTransform = child;
        companion.status = Status.ACTIVE;
        companion.rejection = null;
        companion.persistState();
        LOGGER.info(
            "Started companion visual {} for gameplay session {} at scene scale {}",
            companion.childId(),
            context.sessionId(),
            companion.childScale
        );
        return companion;
    }

    private void refreshExisting() {
        if (this.status == Status.STOPPED || this.status == Status.UNAVAILABLE) {
            return;
        }
        if (isActive(this)) {
            this.activate(false);
        } else if (this.status == Status.ACTIVE) {
            this.markUnavailable();
        } else {
            this.status = Status.PENDING;
        }
    }

    private void resolvePending() {
        if (isActive(this)) {
            this.activate(true);
        } else {
            this.markUnavailable();
        }
    }

    private void activate(boolean restored) {
        if (this.cancelWithScope) {
            this.context.scope().adoptVisual(
                this.childHandle.instanceId(), this.childHandle
            );
        }
        this.status = Status.ACTIVE;
        this.rejection = null;
        this.persistState();
        if (restored) {
            LOGGER.info(
                "Adopted restored companion visual {} for gameplay session {}",
                this.childHandle.instanceId(),
                this.context.sessionId()
            );
        }
    }

    private void markUnavailable() {
        this.status = Status.UNAVAILABLE;
        this.rejection = null;
        if (!this.lostLogged) {
            this.lostLogged = true;
            LOGGER.warn(
                "Companion visual {} for gameplay session {} is unavailable; "
                    + "the gameplay session continues without it",
                this.childHandle.instanceId(),
                this.context.sessionId()
            );
        }
        this.persistState();
    }

    private void reportRejection() {
        if (this.rejectionLogged) {
            return;
        }
        this.rejectionLogged = true;
        LOGGER.warn(
            "Companion visual {} for gameplay session {} was not started: {}; "
                + "the gameplay session continues without it",
            this.childHandle.instanceId(),
            this.context.sessionId(),
            this.rejection
        );
    }

    private void persistState() {
        if (this.closed) {
            return;
        }
        CompoundTag entry = new CompoundTag();
        entry.putInt("version", STATE_VERSION);
        entry.putUUID("child", this.childHandle.instanceId());
        entry.putString("effect", this.spec.effectId().toString());
        entry.putString("status", this.status.name());
        if (this.rejection != null) {
            entry.putString("rejection", this.rejection.name());
        }
        this.context.updatePersistentState(state -> state.put(STATE_KEY, entry));
    }

    @Nullable
    private static SavedState readState(GameplayEffectContext context) {
        CompoundTag state = context.persistentState();
        if (!state.contains(STATE_KEY)) {
            return null;
        }
        CompoundTag tag = state.getCompound(STATE_KEY);
        if (tag.getInt("version") != STATE_VERSION || !tag.contains("child")) {
            return null;
        }
        Status status = parseStatus(tag.getString("status"));
        EffectStartStatus rejection = parseRejection(tag.getString("rejection"));
        return new SavedState(tag.getUUID("child"), status, rejection);
    }

    private static Status parseStatus(String name) {
        try {
            return Status.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return Status.PENDING;
        }
    }

    @Nullable
    private static EffectStartStatus parseRejection(String name) {
        if (name.isEmpty()) {
            return null;
        }
        try {
            return EffectStartStatus.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static UUID deterministicChildId(UUID sessionId) {
        return UUID.nameUUIDFromBytes(
            (CHILD_ID_SALT + sessionId).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static float childScale(GameplayEffectContext context, CompanionSpec spec) {
        float scale = context.spec().scale() * spec.scaleMultiplier();
        if (!Float.isFinite(scale) || scale <= 0.0F || scale > EffectLimits.MAX_SCALE) {
            throw new IllegalArgumentException(
                "Companion scale must stay inside (0, " + EffectLimits.MAX_SCALE + "]"
            );
        }
        return scale;
    }

    private static boolean isActive(EffectCompanion companion) {
        return VisualEffectService.isActive(companion.context.level(), companion.childHandle);
    }

    private record SavedState(
        UUID childId,
        Status status,
        @Nullable EffectStartStatus rejection
    ) {
    }
}
