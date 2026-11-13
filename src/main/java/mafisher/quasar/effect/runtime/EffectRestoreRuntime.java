package mafisher.quasar.effect.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.api.cinematic.CinematicTimeline;
import mafisher.quasar.api.effect.EffectAttachment;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.GameplayEffectController;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.cinematic.CinematicTimelineRegistry;
import mafisher.quasar.effect.gameplay.GameplayEffectNbt;
import mafisher.quasar.effect.gameplay.GameplayEffectTypeRegistry;
import mafisher.quasar.effect.persistence.BlockJournal;
import mafisher.quasar.effect.persistence.EffectAudioNbt;
import mafisher.quasar.effect.persistence.EffectRecoveryReport;
import mafisher.quasar.effect.persistence.EffectSavedData;
import mafisher.quasar.effect.scale.SegmentedEffectNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-level restore owner. Rebuilds runtime state from persisted descriptors in dependency order so
 * gameplay scopes exist before their child visuals, and never replays past cinematic cues.
 */
@ApiStatus.Internal
@SuppressWarnings("PMD.TooManyMethods")
public final class EffectRestoreRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectRestoreRuntime.class);
    private static final List<EffectKind> RESTORE_ORDER = List.of(
        EffectKind.CINEMATIC,
        EffectKind.GAMEPLAY,
        EffectKind.SEGMENTED,
        EffectKind.VISUAL
    );

    private final ServerLevel level;
    private final EffectStore store;
    private final GameplayEffectRuntime gameplay;
    private final VisualEffectRuntime visuals;
    private final SegmentedEffectRuntime segmented;
    private final CinematicEffectRuntime cinematics;
    private final BlockRollbackRuntime rollbacks;
    private EffectRecoveryReport report = EffectRecoveryReport.EMPTY;

    public EffectRestoreRuntime(
        ServerLevel level,
        EffectStore store,
        GameplayEffectRuntime gameplay,
        VisualEffectRuntime visuals,
        SegmentedEffectRuntime segmented,
        CinematicEffectRuntime cinematics,
        BlockRollbackRuntime rollbacks
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.store = Objects.requireNonNull(store, "store");
        this.gameplay = Objects.requireNonNull(gameplay, "gameplay");
        this.visuals = Objects.requireNonNull(visuals, "visuals");
        this.segmented = Objects.requireNonNull(segmented, "segmented");
        this.cinematics = Objects.requireNonNull(cinematics, "cinematics");
        this.rollbacks = Objects.requireNonNull(rollbacks, "rollbacks");
    }

    public EffectRecoveryReport report() {
        return this.report;
    }

    public EffectRecoveryReport restoreLevel() {
        if (this.store.isNewerSchema()) {
            String message = "Effect store schema " + this.store.schemaVersion()
                + " is newer than supported schema " + EffectSavedData.SCHEMA_VERSION
                + "; left untouched";
            LOGGER.warn(message);
            this.report = new EffectRecoveryReport(0, 0, 0, 0, 0, List.of(message));
            return this.report;
        }

        List<EffectDescriptor> descriptors = new ArrayList<>(this.store.descriptors());
        descriptors = orderDescriptors(descriptors);
        Map<UUID, EffectDescriptor> descriptorsById = new HashMap<>();
        for (EffectDescriptor descriptor : descriptors) {
            descriptorsById.put(descriptor.instanceId(), descriptor);
        }
        int restored = 0;
        int expired = 0;
        int quarantined = 0;
        int unsupported = 0;
        int rollbacks = 0;
        List<String> messages = new ArrayList<>();
        Set<UUID> handledRollbacks = new HashSet<>();

        for (EffectDescriptor descriptor : new ArrayList<>(this.store.descriptors())) {
            BlockJournal journal = this.store.journalOrNull(descriptor.instanceId());
            if (journal == null || !journal.isRollbackPending()) {
                continue;
            }
            handledRollbacks.add(descriptor.instanceId());
            int owned = this.store.removeOwnedBy(descriptor.instanceId());
            if (owned > 0) {
                messages.add("Removed " + owned + " child effect(s) owned by " + descriptor.instanceId());
            }
            if (!descriptor.dimensionId().equals(this.level.dimension().location())) {
                this.store.quarantine(descriptor.instanceId());
                quarantined++;
                messages.add("Quarantined pending rollback " + descriptor.instanceId()
                    + " in " + descriptor.dimensionId());
                continue;
            }
            if (this.rollbacks.schedule(descriptor.instanceId(), journal)) {
                rollbacks++;
                messages.add("Rolling back " + descriptor.instanceId());
            } else {
                messages.add("Skipped rollback " + descriptor.instanceId());
            }
        }

        for (EffectDescriptor descriptor : descriptors) {
            if (handledRollbacks.contains(descriptor.instanceId())
                || !this.store.contains(descriptor.instanceId())) {
                continue;
            }
            if (descriptor.isExpired(this.level.getGameTime())) {
                this.store.remove(descriptor.instanceId());
                expired++;
                messages.add("Expired " + descriptor.kind() + " " + descriptor.instanceId());
                continue;
            }
            if (!descriptor.dimensionId().equals(this.level.dimension().location())) {
                this.store.quarantine(descriptor.instanceId());
                quarantined++;
                messages.add("Wrong dimension for " + descriptor.instanceId() + ": " + descriptor.dimensionId());
                continue;
            }
            if (hasAttachmentCycle(descriptor, descriptorsById)) {
                this.store.quarantine(descriptor.instanceId());
                quarantined++;
                messages.add("Attachment cycle at " + descriptor.kind() + " " + descriptor.instanceId());
                continue;
            }
            try {
                if (restore(descriptor)) {
                    restored++;
                } else {
                    unsupported++;
                    messages.add("Deferred " + descriptor.kind() + " " + descriptor.instanceId());
                }
            } catch (RuntimeException exception) {
                this.store.quarantine(descriptor.instanceId());
                quarantined++;
                messages.add("Quarantined " + descriptor.kind() + " " + descriptor.instanceId() + ": "
                    + exception.getMessage());
                LOGGER.warn("Could not restore {} effect {}", descriptor.kind(), descriptor.instanceId(), exception);
            }
        }

        this.report = new EffectRecoveryReport(
            restored, expired, quarantined, unsupported, rollbacks, messages
        );
        if (restored > 0 || quarantined > 0 || unsupported > 0 || rollbacks > 0) {
            LOGGER.info(
                "Restored {} effect(s) in {}: expired {}, quarantined {}, deferred {}, rollbacks {}",
                restored,
                this.level.dimension().location(),
                expired,
                quarantined,
                unsupported,
                rollbacks
            );
        }
        return this.report;
    }

    private boolean restore(EffectDescriptor descriptor) {
        return switch (descriptor.kind()) {
            case CINEMATIC -> restoreCinematic(descriptor);
            case GAMEPLAY -> restoreGameplay(descriptor);
            case SEGMENTED -> restoreSegmented(descriptor);
            case VISUAL -> restoreVisual(descriptor);
        };
    }

    private boolean restoreCinematic(EffectDescriptor descriptor) {
        CinematicTimeline timeline = CinematicTimelineRegistry.find(descriptor.typeId()).orElseThrow(
            () -> new IllegalArgumentException("Unknown cinematic timeline " + descriptor.typeId())
        );
        return this.cinematics.restore(
            timeline,
            descriptor.instanceId(),
            descriptor.startGameTime(),
            descriptor
        ).active();
    }

    private boolean restoreGameplay(EffectDescriptor descriptor) {
        GameplayEffectSpec spec = GameplayEffectNbt.loadSpec(descriptor);
        GameplayEffectController controller = GameplayEffectTypeRegistry.create(descriptor).orElseThrow(
            () -> new IllegalArgumentException("No gameplay controller for " + descriptor.typeId())
        );
        return this.gameplay.restore(
            spec,
            descriptor.instanceId(),
            descriptor.startGameTime(),
            descriptor,
            controller
        ).isPresent();
    }

    private boolean restoreSegmented(EffectDescriptor descriptor) {
        SegmentedEffectNbt.Loaded loaded = SegmentedEffectNbt.load(descriptor);
        this.segmented.restore(
            loaded.spec(),
            descriptor.instanceId(),
            descriptor.startGameTime(),
            loaded.segmentIds(),
            descriptor,
            EffectPersistenceOptions.DEFAULT
        );
        return true;
    }

    private boolean restoreVisual(EffectDescriptor descriptor) {
        CompoundTag state = descriptor.stateView();
        VisualEffectSpec spec = new VisualEffectSpec(
            descriptor.typeId(),
            transformPosition(descriptor),
            descriptor.transform().yaw(),
            descriptor.transform().pitch(),
            descriptor.transform().roll(),
            descriptor.scale(),
            descriptor.durationTicks(),
            descriptor.seed(),
            descriptor.audience(),
            EffectAudioNbt.loadFrom(state),
            priority(state, descriptor),
            descriptor.attachment()
        );
        var result = this.visuals.restore(
            spec,
            descriptor.instanceId(),
            descriptor.startGameTime(),
            descriptor
        );
        if (!result.active()) {
            LOGGER.info(
                "Deferred visual restore {} in {}: the soft budget culled it",
                descriptor.instanceId(),
                this.level.dimension().location()
            );
            return false;
        }
        if (descriptor.ownerSessionId() != null) {
            EffectScope scope = this.gameplay.scope(
                new GameplayEffectHandle(descriptor.ownerSessionId())
            );
            if (scope != null) {
                scope.adoptVisual(descriptor.instanceId(), result.handle());
            }
        }
        return true;
    }

    private static boolean hasAttachmentCycle(
        EffectDescriptor descriptor,
        Map<UUID, EffectDescriptor> descriptorsById
    ) {
        Set<UUID> visited = new HashSet<>();
        UUID currentId = descriptor.instanceId();
        while (visited.add(currentId)) {
            EffectDescriptor current = descriptorsById.get(currentId);
            if (current == null || !(current.attachment() instanceof EffectAttachment attachment)
                || !(attachment.parent() instanceof EffectAttachment.Parent.Effect parent)) {
                return false;
            }
            currentId = parent.id();
        }
        return true;
    }

    private static List<EffectDescriptor> orderDescriptors(List<EffectDescriptor> source) {
        List<EffectDescriptor> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingInt(descriptor -> RESTORE_ORDER.indexOf(descriptor.kind())));
        Map<UUID, EffectDescriptor> byId = new HashMap<>();
        for (EffectDescriptor descriptor : sorted) {
            byId.put(descriptor.instanceId(), descriptor);
        }
        List<EffectDescriptor> visualOrder = new ArrayList<>();
        Set<UUID> visiting = new HashSet<>();
        Set<UUID> done = new HashSet<>();
        for (EffectDescriptor descriptor : sorted) {
            if (descriptor.kind() == EffectKind.VISUAL) {
                visitVisual(descriptor, byId, visiting, done, visualOrder);
            }
        }
        List<EffectDescriptor> result = new ArrayList<>(sorted.size());
        int visualIndex = 0;
        for (EffectDescriptor descriptor : sorted) {
            if (descriptor.kind() == EffectKind.VISUAL) {
                result.add(visualOrder.get(visualIndex++));
            } else {
                result.add(descriptor);
            }
        }
        return result;
    }

    private static void visitVisual(
        EffectDescriptor descriptor,
        Map<UUID, EffectDescriptor> byId,
        Set<UUID> visiting,
        Set<UUID> done,
        List<EffectDescriptor> ordered
    ) {
        UUID instanceId = descriptor.instanceId();
        if (done.contains(instanceId) || !visiting.add(instanceId)) {
            return;
        }
        EffectAttachment attachment = descriptor.attachment();
        if (attachment != null
            && attachment.parent() instanceof EffectAttachment.Parent.Effect effect) {
            EffectDescriptor parent = byId.get(effect.id());
            if (parent != null && parent.kind() == EffectKind.VISUAL) {
                visitVisual(parent, byId, visiting, done, ordered);
            }
        }
        visiting.remove(instanceId);
        if (done.add(instanceId)) {
            ordered.add(descriptor);
        }
    }

    private static Vec3 transformPosition(EffectDescriptor descriptor) {
        EffectPoint position = descriptor.transform().position();
        return new Vec3(position.x(), position.y(), position.z());
    }

    private static EffectPriority priority(CompoundTag state, EffectDescriptor descriptor) {
        String context = descriptor.kind().name() + " effect " + descriptor.instanceId();
        return EffectPriority.byName(state.getString("priority"), EffectPriority.NORMAL, context);
    }
}
