package mafisher.quasar.effect.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectScope;
import mafisher.quasar.effect.VisualEffectHandle;
import mafisher.quasar.effect.VisualEffectService;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import mafisher.quasar.effect.cinematic.CinematicEffectService;
import mafisher.quasar.effect.cinematic.CinematicTimeline;
import mafisher.quasar.effect.cinematic.CinematicTimelineRegistry;
import mafisher.quasar.effect.gameplay.GameplayEffectController;
import mafisher.quasar.effect.gameplay.GameplayEffectHandle;
import mafisher.quasar.effect.gameplay.GameplayEffectNbt;
import mafisher.quasar.effect.gameplay.GameplayEffectService;
import mafisher.quasar.effect.gameplay.GameplayEffectSpec;
import mafisher.quasar.effect.gameplay.GameplayEffectTypeRegistry;
import mafisher.quasar.effect.scale.SegmentedVisualEffectService;
import mafisher.quasar.effect.scale.SegmentedVisualEffectSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Restores persisted effects into their services after a level loads. Restoration is ordered so
 * gameplay scopes exist before their child visuals and never replays past cinematic cues.
 */
public final class EffectRestoreService {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final Map<ServerLevel, EffectRecoveryReport> REPORTS = new WeakHashMap<>();
    private static final List<EffectKind> RESTORE_ORDER = List.of(
        EffectKind.CINEMATIC,
        EffectKind.GAMEPLAY,
        EffectKind.SEGMENTED,
        EffectKind.VISUAL
    );

    private EffectRestoreService() {
    }

    public static EffectRecoveryReport restoreLevel(ServerLevel level) {
        EffectSavedData store = EffectPersistenceService.storeOrNull(level);
        if (store == null) {
            return EffectRecoveryReport.EMPTY;
        }

        List<EffectDescriptor> descriptors = new ArrayList<>(store.descriptors());
        descriptors.sort(Comparator.comparingInt(descriptor -> RESTORE_ORDER.indexOf(descriptor.kind())));
        int restored = 0;
        int expired = 0;
        int quarantined = 0;
        int unsupported = 0;
        int rollbacks = 0;
        List<String> messages = new ArrayList<>();
        Set<UUID> handledRollbacks = new HashSet<>();

        for (EffectDescriptor descriptor : new ArrayList<>(store.descriptors())) {
            BlockJournal journal = store.journal(descriptor.instanceId());
            if (journal == null || !journal.isRollbackPending()) {
                continue;
            }
            handledRollbacks.add(descriptor.instanceId());
            if (!descriptor.dimensionId().equals(level.dimension().location())) {
                store.removeOwnedBy(descriptor.instanceId());
                store.quarantine(descriptor.instanceId());
                quarantined++;
                messages.add("Quarantined pending rollback " + descriptor.instanceId()
                    + " in " + descriptor.dimensionId());
                continue;
            }
            store.removeOwnedBy(descriptor.instanceId());
            if (BlockRollbackService.schedule(level, descriptor.instanceId(), journal)) {
                rollbacks++;
                messages.add("Rolling back " + descriptor.instanceId());
            } else {
                messages.add("Skipped rollback " + descriptor.instanceId());
            }
        }

        for (EffectDescriptor descriptor : descriptors) {
            if (handledRollbacks.contains(descriptor.instanceId())
                || !store.contains(descriptor.instanceId())) {
                continue;
            }
            if (descriptor.isExpired(level.getGameTime())) {
                store.remove(descriptor.instanceId());
                expired++;
                messages.add("Expired " + descriptor.kind() + " " + descriptor.instanceId());
                continue;
            }
            if (!descriptor.dimensionId().equals(level.dimension().location())) {
                store.quarantine(descriptor.instanceId());
                quarantined++;
                messages.add("Wrong dimension for " + descriptor.instanceId() + ": " + descriptor.dimensionId());
                continue;
            }
            try {
                if (restore(level, descriptor)) {
                    restored++;
                } else {
                    unsupported++;
                    messages.add("Deferred " + descriptor.kind() + " " + descriptor.instanceId());
                }
            } catch (RuntimeException exception) {
                store.quarantine(descriptor.instanceId());
                quarantined++;
                messages.add("Quarantined " + descriptor.kind() + " " + descriptor.instanceId() + ": "
                    + exception.getMessage());
                LOGGER.warn("Could not restore {} effect {}", descriptor.kind(), descriptor.instanceId(), exception);
            }
        }

        store.setDirty();
        EffectRecoveryReport report = new EffectRecoveryReport(
            restored, expired, quarantined, unsupported, rollbacks, messages
        );
        REPORTS.put(level, report);
        if (restored > 0 || quarantined > 0 || unsupported > 0 || rollbacks > 0) {
            LOGGER.info(
                "Restored {} effect(s) in {}: expired {}, quarantined {}, deferred {}, rollbacks {}",
                restored,
                level.dimension().location(),
                expired,
                quarantined,
                unsupported,
                rollbacks
            );
        }
        return report;
    }

    public static EffectRecoveryReport report(ServerLevel level) {
        return REPORTS.getOrDefault(level, EffectRecoveryReport.EMPTY);
    }

    private static boolean restore(ServerLevel level, EffectDescriptor descriptor) {
        return switch (descriptor.kind()) {
            case CINEMATIC -> restoreCinematic(level, descriptor);
            case GAMEPLAY -> restoreGameplay(level, descriptor);
            case SEGMENTED -> restoreSegmented(level, descriptor);
            case VISUAL -> restoreVisual(level, descriptor);
        };
    }

    private static boolean restoreCinematic(ServerLevel level, EffectDescriptor descriptor) {
        CinematicTimeline timeline = CinematicTimelineRegistry.find(descriptor.typeId()).orElseThrow(
            () -> new IllegalArgumentException("Unknown cinematic timeline " + descriptor.typeId())
        );
        CompoundTag state = descriptor.state();
        if (state.getBoolean("anchored")) {
            Vec3 anchor = new Vec3(
                state.getDouble("anchor_x"),
                state.getDouble("anchor_y"),
                state.getDouble("anchor_z")
            );
            timeline = timeline.anchored(anchor);
        }
        return CinematicEffectService.restore(
            level,
            timeline,
            descriptor.instanceId(),
            descriptor.startGameTime(),
            descriptor
        ) != null;
    }

    private static boolean restoreGameplay(ServerLevel level, EffectDescriptor descriptor) {
        GameplayEffectSpec spec = GameplayEffectNbt.loadSpec(descriptor);
        GameplayEffectController controller = GameplayEffectTypeRegistry.create(descriptor).orElseThrow(
            () -> new IllegalArgumentException("No gameplay controller for " + descriptor.typeId())
        );
        return GameplayEffectService.restore(
            level,
            spec,
            descriptor.instanceId(),
            descriptor.startGameTime(),
            descriptor,
            controller
        ) != null;
    }

    private static boolean restoreSegmented(ServerLevel level, EffectDescriptor descriptor) {
        CompoundTag state = descriptor.state();
        EffectAudioSpec audio = EffectAudioNbt.loadFrom(state);
        SegmentedVisualEffectSpec spec = new SegmentedVisualEffectSpec(
            descriptor.typeId(),
            transformPosition(descriptor),
            descriptor.transform().yaw(),
            descriptor.transform().pitch(),
            descriptor.transform().roll(),
            descriptor.scale(),
            state.getInt("columns"),
            state.getInt("rows"),
            state.getDouble("spacing"),
            descriptor.durationTicks(),
            descriptor.seed(),
            descriptor.audience(),
            audio
        );
        List<UUID> segmentIds = segmentIds(descriptor);
        return SegmentedVisualEffectService.restore(
            level,
            spec,
            descriptor.instanceId(),
            descriptor.startGameTime(),
            segmentIds,
            descriptor
        ) != null;
    }

    private static boolean restoreVisual(ServerLevel level, EffectDescriptor descriptor) {
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
            EffectAudioNbt.loadFrom(descriptor.state())
        );
        VisualEffectHandle handle = VisualEffectService.restore(
            level,
            spec,
            descriptor.instanceId(),
            descriptor.startGameTime(),
            descriptor
        );
        if (handle == null) {
            return false;
        }
        if (descriptor.ownerSessionId() != null) {
            EffectScope scope = GameplayEffectService.scope(
                level,
                new GameplayEffectHandle(descriptor.ownerSessionId())
            );
            if (scope != null) {
                scope.adoptVisual(descriptor.instanceId(), handle);
            }
        }
        return true;
    }

    private static Vec3 transformPosition(EffectDescriptor descriptor) {
        EffectPoint position = descriptor.transform().position();
        return new Vec3(position.x(), position.y(), position.z());
    }

    private static List<UUID> segmentIds(EffectDescriptor descriptor) {
        CompoundTag state = descriptor.state();
        int count = Math.max(0, state.getInt("columns") * state.getInt("rows"));
        if (!state.contains("segment_ids")) {
            return SegmentedVisualEffectService.restoreSegmentIds(descriptor.instanceId(), count);
        }
        var stored = state.getList("segment_ids", Tag.TAG_STRING);
        List<UUID> ids = new ArrayList<>(stored.size());
        for (int index = 0; index < stored.size(); index++) {
            ids.add(UUID.fromString(stored.getString(index)));
        }
        return ids.size() == count
            ? List.copyOf(ids)
            : SegmentedVisualEffectService.restoreSegmentIds(descriptor.instanceId(), count);
    }
}
