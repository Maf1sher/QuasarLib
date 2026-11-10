package mafisher.quasar.content;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import mafisher.quasar.effect.EffectAudience;
import mafisher.quasar.effect.EffectBudgets;
import mafisher.quasar.effect.EffectDamageShape;
import mafisher.quasar.effect.EffectDebrisAttractor;
import mafisher.quasar.effect.EffectDebrisSpec;
import mafisher.quasar.effect.EffectDebrisSpawn;
import mafisher.quasar.effect.EffectForceField;
import mafisher.quasar.effect.EffectHitbox;
import mafisher.quasar.effect.EffectLifetimePolicy;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectPriority;
import mafisher.quasar.effect.VisualEffectSpec;
import mafisher.quasar.effect.audio.EffectAudioSpec;
import mafisher.quasar.effect.block.BlockChange;
import mafisher.quasar.effect.gameplay.BlockJournalMode;
import mafisher.quasar.effect.gameplay.EffectBlockCursor;
import mafisher.quasar.effect.gameplay.EffectBlockSample;
import mafisher.quasar.effect.gameplay.EffectBlockVolume;
import mafisher.quasar.effect.gameplay.EffectBulkBlockEditor;
import mafisher.quasar.effect.gameplay.EffectRaycastResult;
import mafisher.quasar.effect.gameplay.EffectRaycastStatus;
import mafisher.quasar.effect.gameplay.GameplayEffectContext;
import mafisher.quasar.effect.gameplay.GameplayEffectController;
import mafisher.quasar.effect.gameplay.GameplayEffectEndReason;
import mafisher.quasar.effect.gameplay.GameplayEffectHandle;
import mafisher.quasar.effect.gameplay.GameplayEffectService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Content-owned black-hole gameplay: a budgeted core carve, an anchor-centred final crater,
 * accretion forces, and horizon consumption. Block edits end with a reveal reserve before the
 * closing phase, leaving the outro visual-only. The visual child is owned by the session scope
 * and uses the same cinematic progress, so pausing, resume, and cleanup stay in one place.
 */
final class ContentBlackHoleGameplayController implements GameplayEffectController {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final ContentBlackHoleGameplaySettings SETTINGS =
        ContentBlackHoleGameplaySettings.DEFAULT;
    private static final double MAX_PENDING_HEADROOM = 12_000.0;
    private static final Map<ServerLevel, Set<UUID>> ACTIVE = new WeakHashMap<>();

    static int stopAll(ServerLevel level) {
        Set<UUID> sessions = ACTIVE.remove(level);
        if (sessions == null) {
            return 0;
        }
        int stopped = 0;
        for (UUID session : sessions) {
            GameplayEffectService.stop(
                level, new GameplayEffectHandle(session), GameplayEffectEndReason.CANCELLED
            );
            stopped++;
        }
        return stopped;
    }

    private static final String YAW = "yaw";
    private static final String PITCH = "pitch";
    private static final String ROLL = "roll";
    private static final String CORE_STARTED = "core_started";
    private static final String CORE_DONE = "core_done";
    private static final String CORE_CURSOR = EffectBlockCursor.stateKey();
    private static final String CRATER_EDIT = "crater_edit";
    private static final String CRATER_RELEASED = "crater_released";
    private static final String CRATER_RADIUS = "crater_radius";
    private static final String CRATER_DEPTH = "crater_depth";
    private static final String CRATER_START = "crater_start";
    private static final String CRATER_WORK_END = "crater_work_end";
    private static final String CRATER_GROUND_Y = "crater_ground_y";
    private static final String CRATER_SOLID_SECTIONS = "crater_solid_sections";
    private static final String CRATER_ESTIMATED_SECTIONS = "crater_estimated_sections";
    private static final String CRATER_INCOMPLETE = "crater_incomplete";
    private static final int MAX_DEBRIS_STATES = 12;
    private static final int TEAR_CLUSTER_RADIUS = 1;
    private static final int TEAR_CLUSTER_BLOCKS = 27;
    private static final int SURFACE_PIECES_PER_BURST = 32;
    private static final float SURFACE_PIECE_SCALE = 8.0F;
    private static final double SURFACE_LIFT = 0.45;

    private final ContentBlackHoleGameplaySettings settings;
    private Vec3 center = Vec3.ZERO;
    private double coreRadius;
    private double attractionRadius;
    private EffectDebrisAttractor debrisAttractor;
    private Vec3 diskAxis = new Vec3(0.0, 1.0, 0.0);
    private float yaw;
    private float pitch;
    private float roll;

    private EffectBlockCursor coreCursor;
    private boolean coreStarted;
    private boolean coreDone;
    private boolean visualStarted;
    private boolean hasCrater;
    private boolean craterSyncReleased;
    private boolean craterLogged;
    private boolean craterBudgetWarned;
    private boolean craterEndWarned;
    private boolean destructionComplete;
    private boolean destructionIncomplete;
    private boolean craterEndChecked;
    private double craterGroundY;
    private double craterRadius;
    private double craterDepth;
    private double frontRadius;
    private ContentBlackHoleDestructionPlan.Plan destruction;
    private EffectBulkBlockEditor craterEditor;
    private final List<BlockState> debrisStates = new ArrayList<>(MAX_DEBRIS_STATES);

    ContentBlackHoleGameplayController() {
        this(SETTINGS);
    }

    ContentBlackHoleGameplayController(ContentBlackHoleGameplaySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public void onStart(GameplayEffectContext context) {
        this.readState(context);
        this.center = context.spec().anchor();
        this.coreRadius = this.settings.coreRadius(context.spec().scale());
        this.attractionRadius = this.settings.attractionRadius(context.spec().scale());
        this.diskAxis = diskAxis(this.yaw, this.pitch, this.roll);
        this.debrisAttractor = new EffectDebrisAttractor(
            this.center, this.diskAxis, this.coreRadius, 1.35, 1.6
        );
        this.craterGroundY = this.sampleGroundY(context);
        this.planDestruction(context);
        this.hasCrater = ContentBlackHoleDestructionPlan.reachesTerrain(
            this.center.y, this.craterDepth, this.craterGroundY,
            context.level().getMinBuildHeight()
        );
        if (this.craterEditor != null) {
            this.syncCraterVolumeFromEditor();
            this.frontRadius = this.craterEditor.frontScale() * this.craterRadius;
        }
        this.ensureDebrisPalette();
        context.updatePersistentState(tag -> {
            tag.putFloat(YAW, this.yaw);
            tag.putFloat(PITCH, this.pitch);
            tag.putFloat(ROLL, this.roll);
        });
        context.updatePersistentState(this::putDestructionPlan);
        this.startVisual(context);
        this.track(context);
    }

    @Override
    public void onResume(GameplayEffectContext context) {
        this.readState(context);
        this.center = context.spec().anchor();
        this.coreRadius = this.settings.coreRadius(context.spec().scale());
        this.attractionRadius = this.settings.attractionRadius(context.spec().scale());
        this.diskAxis = diskAxis(this.yaw, this.pitch, this.roll);
        this.debrisAttractor = new EffectDebrisAttractor(
            this.center, this.diskAxis, this.coreRadius, 1.35, 1.6
        );
        if (!context.persistentState().contains(CRATER_GROUND_Y)) {
            this.craterGroundY = this.sampleGroundY(context);
        }
        if (this.destruction == null || this.craterRadius <= 0.0
            || (this.destruction.solidSections() <= 0 && this.craterEditor == null)) {
            this.planDestruction(context);
            context.updatePersistentState(this::putDestructionPlan);
        }
        this.hasCrater = ContentBlackHoleDestructionPlan.reachesTerrain(
            this.center.y, this.craterDepth, this.craterGroundY,
            context.level().getMinBuildHeight()
        );
        if (this.craterEditor != null) {
            this.syncCraterVolumeFromEditor();
            this.frontRadius = this.craterEditor.frontScale() * this.craterRadius;
        }
        this.ensureDebrisPalette();
        this.visualStarted = true;
        if (this.coreStarted && !this.coreDone) {
            this.coreCursor = this.restoreCursor(context);
        }
        this.track(context);
    }

    @Override
    public void onTick(GameplayEffectContext context) {
        int duration = context.spec().phases().totalDurationTicks();
        float progress = Math.min(1.0F, Math.max(0.0F, context.ageTicks() / (float)duration));
        ContentBlackHoleCinematics.Frame frame = ContentBlackHoleCinematics.frame(progress);

        if (progress >= ContentBlackHoleCinematics.IGNITION && !this.coreDone) {
            this.tickCore(context);
        }
        if (progress >= ContentBlackHoleCinematics.HORIZON_END
            && this.destruction != null
            && progress < this.destruction.workEndProgress()) {
            this.tickSurfaceTear(context);
        }
        this.tickCrater(context, progress);
        if (progress >= ContentBlackHoleCinematics.HORIZON_END
            && progress < ContentBlackHoleCinematics.IMPLOSION) {
            this.emitDebris(context, progress);
            this.tickForces(context, frame);
        }
        if (progress >= ContentBlackHoleCinematics.IMPLOSION) {
            this.debrisStates.clear();
            this.releaseCraterSync(context);
        }
        this.saveEditorState(context);
    }

    @Override
    public void onEnd(GameplayEffectContext context, GameplayEffectEndReason reason) {
        this.coreCursor = null;
        boolean keepChanges = reason == GameplayEffectEndReason.EXPIRED
            || !context.canRollbackChanges();
        if (this.craterEditor != null) {
            EffectBulkBlockEditor.Progress carved = this.craterEditor.progress();
            if (!carved.finished() && !this.destructionComplete) {
                LOGGER.warn(
                    "Black-hole destruction stopped early: {} of {} sections, {} blocks",
                    carved.completedSections(),
                    carved.totalSections(),
                    carved.clearedBlocks()
                );
            }
            this.craterEditor.close(keepChanges);
            this.craterEditor = null;
        }
        Set<UUID> sessions = ACTIVE.get(context.level());
        if (sessions != null) {
            sessions.remove(context.sessionId());
            if (sessions.isEmpty()) {
                ACTIVE.remove(context.level());
            }
        }
    }

    private void track(GameplayEffectContext context) {
        ACTIVE.computeIfAbsent(context.level(), ignored -> new LinkedHashSet<>())
            .add(context.sessionId());
    }

    private void startVisual(GameplayEffectContext context) {
        if (this.visualStarted) {
            return;
        }
        this.visualStarted = true;
        context.scope().playVisual(new VisualEffectSpec(
            ContentEffectIds.BLACK_HOLE,
            this.center,
            this.yaw,
            this.pitch,
            this.roll,
            context.spec().scale(),
            context.spec().phases().totalDurationTicks(),
            context.spec().seed(),
            EffectAudience.dimensionWide(),
            EffectAudioSpec.NONE,
            EffectPriority.CRITICAL
        ), EffectLifetimePolicy.CANCEL_WITH_SCOPE);
    }

    private double sampleGroundY(GameplayEffectContext context) {
        int x = Mth.floor(this.center.x);
        int z = Mth.floor(this.center.z);
        OptionalInt ground = context.world().height(
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z
        );
        if (ground.isEmpty()) {
            // The cinematic may sit just beyond the player's simulation distance; make sure the
            // crater column exists before reading its height.
            context.level().getChunkAt(new BlockPos(x, context.level().getMinBuildHeight(), z));
            ground = OptionalInt.of(context.level().getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z
            ));
        }
        int groundY = ground.getAsInt();
        if (groundY <= context.level().getMinBuildHeight() + 1) {
            return this.center.y;
        }
        return groundY;
    }

    private void ensureDebrisPalette() {
        if (!this.debrisStates.isEmpty()) {
            return;
        }
        this.debrisStates.add(Blocks.STONE.defaultBlockState());
        this.debrisStates.add(Blocks.DIRT.defaultBlockState());
        this.debrisStates.add(Blocks.GRASS_BLOCK.defaultBlockState());
    }

    private void tickCrater(GameplayEffectContext context, float progress) {
        if (!this.hasCrater || this.destruction == null) {
            return;
        }
        if (this.destructionComplete) {
            this.frontRadius = this.craterRadius;
            return;
        }
        if (this.craterEditor == null) {
            if (progress >= this.destruction.workEndProgress()) {
                if (!this.craterEndChecked) {
                    this.craterEndChecked = true;
                    this.destructionIncomplete = true;
                    if (!this.craterEndWarned) {
                        this.craterEndWarned = true;
                        LOGGER.warn(
                            "Black-hole destruction window ended before the crater could start; "
                                + "no block edits continue into the closing phase"
                        );
                    }
                    this.releaseCraterSync(context);
                }
                return;
            }
            if (!this.coreDone || progress < this.destruction.startProgress()) {
                return;
            }
            this.craterEditor = this.createCraterEditor(context);
            LOGGER.info(
                "Starting black-hole destruction at scale {}: requested radius {} depth {}, "
                    + "actual radius {} depth {}, window {}..{}, sections {} (solid {}), budget {}",
                context.spec().scale(),
                this.destruction.requestedRadius(),
                this.destruction.requestedDepth(),
                this.destruction.radius(),
                this.destruction.depth(),
                this.destruction.startProgress(),
                this.destruction.workEndProgress(),
                this.destruction.estimatedSections(),
                this.destruction.solidSections(),
                this.destruction.sectionBudget()
            );
            if (this.destruction.budgetClamped()) {
                LOGGER.warn(
                    "Black-hole destruction scaled down from radius {} depth {} to radius {} depth {} "
                        + "to fit the effect window and budgets",
                    this.destruction.requestedRadius(),
                    this.destruction.requestedDepth(),
                    this.destruction.radius(),
                    this.destruction.depth()
                );
            }
        }
        if (this.craterEditor.progress().finished()) {
            this.finishCrater(context);
            return;
        }
        if (progress >= this.destruction.workEndProgress()) {
            if (!this.craterEndChecked) {
                this.craterEndChecked = true;
                if (!this.craterEditor.hasPendingSolidSections()) {
                    this.finishCrater(context);
                    return;
                }
                if (!this.craterEndWarned) {
                    this.craterEndWarned = true;
                    this.destructionIncomplete = true;
                    EffectBulkBlockEditor.Progress carved = this.craterEditor.progress();
                    LOGGER.warn(
                        "Black-hole destruction stopped at its reveal deadline: {} of {} sections, {} blocks; "
                            + "no block edits continue into the closing phase",
                        carved.completedSections(),
                        carved.totalSections(),
                        carved.clearedBlocks()
                    );
                }
                this.releaseCraterSync(context);
            }
            return;
        }

        int totalSections = this.destruction.solidSections();
        int editedSections = this.craterEditor.editedSections();
        int target = this.destruction.targetSectionsAt(progress, totalSections);
        int duration = Math.max(1, context.spec().phases().totalDurationTicks());
        int remainingTicks = Math.max(1, Math.round(
            (this.destruction.workEndProgress() - progress) * duration
        ));
        int required = ContentBlackHoleDestructionPlan.requiredSectionsPerTick(
            Math.max(0, totalSections - editedSections),
            remainingTicks
        );
        int allowed = Math.min(
            this.destruction.sectionBudget(),
            Math.max(0, target - editedSections)
        );
        if (required > this.destruction.sectionBudget() && !this.craterBudgetWarned) {
            this.craterBudgetWarned = true;
            LOGGER.warn(
                "Black-hole destruction needs {} sections/tick but the budget is {}; "
                    + "the front will finish as early as the budget allows",
                required,
                this.destruction.sectionBudget()
            );
        }
        if (allowed <= 0 && editedSections >= totalSections) {
            if (this.craterEditor.hasPendingSolidSections()) {
                allowed = 1;
            } else {
                this.finishCrater(context);
                return;
            }
        }
        if (allowed > 0) {
            this.craterEditor.tick(allowed);
        }
        this.mergeClearedStates(this.craterEditor);
        this.frontRadius = Math.max(
            this.frontRadius,
            this.craterEditor.frontScale() * this.craterRadius
        );
        if (this.craterEditor.progress().finished()) {
            this.finishCrater(context);
        }
    }

    private void finishCrater(GameplayEffectContext context) {
        this.frontRadius = this.craterRadius;
        this.craterEndChecked = true;
        this.destructionComplete = true;
        this.destructionIncomplete = false;
        if (!this.craterLogged) {
            this.craterLogged = true;
            EffectBulkBlockEditor.Progress finished = this.craterEditor.progress();
            LOGGER.info(
                "Finished black-hole destruction: {} sections, {} blocks",
                finished.completedSections(),
                finished.clearedBlocks()
            );
        }
        // Flush any remaining reveals while the horizon is still open.
        this.releaseCraterSync(context);
    }

    private EffectBulkBlockEditor createCraterEditor(GameplayEffectContext context) {
        EffectBlockVolume volume = EffectBlockVolume.ellipsoid(
            new EffectPoint(this.center.x, this.center.y, this.center.z),
            this.craterRadius,
            this.craterDepth,
            this.craterRadius
        );
        return EffectBulkBlockEditor.create(context, new EffectBulkBlockEditor.Spec(
            volume,
            Blocks.AIR.defaultBlockState(),
            true,
            EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
            0.0,
            this.settings.craterJournal()
        ), EffectBulkBlockEditor.Ordering.CENTER_OUT);
    }

    private void mergeClearedStates(EffectBulkBlockEditor editor) {
        if (editor == null) {
            return;
        }
        for (BlockState state : editor.clearedStates()) {
            if (this.debrisStates.size() >= MAX_DEBRIS_STATES) {
                return;
            }
            if (!this.debrisStates.contains(state)) {
                this.debrisStates.add(state);
            }
        }
    }

    private void releaseCraterSync(GameplayEffectContext context) {
        if (this.craterSyncReleased) {
            context.updatePersistentState(
                tag -> tag.putBoolean(CRATER_INCOMPLETE, this.destructionIncomplete)
            );
            return;
        }
        this.craterSyncReleased = true;
        if (this.craterEditor != null) {
            this.craterEditor.releaseSync();
        }
        context.updatePersistentState(tag -> {
            tag.putBoolean(CRATER_RELEASED, true);
            tag.putBoolean(CRATER_INCOMPLETE, this.destructionIncomplete);
        });
    }

    private void saveEditorState(GameplayEffectContext context) {
        if (this.craterEditor == null) {
            return;
        }
        if ((context.ageTicks() & 7) != 0) {
            return;
        }
        context.updatePersistentState(tag -> {
            tag.put(CRATER_EDIT, this.craterEditor.save());
            tag.putBoolean(CRATER_RELEASED, this.craterSyncReleased);
        });
    }

    private void tickCore(GameplayEffectContext context) {
        if (!this.coreStarted) {
            this.coreStarted = true;
            this.coreCursor = this.restoreCursor(context);
            context.updatePersistentState(tag -> tag.putBoolean(CORE_STARTED, true));
        }
        if (this.coreCursor == null) {
            this.coreCursor = this.restoreCursor(context);
        }
        int headroom = (int)Math.max(0.0, MAX_PENDING_HEADROOM - context.pendingBlockChanges());
        int batch = Math.min(this.settings.coreBlocksPerTick(), headroom);
        if (batch <= 0) {
            return;
        }
        this.coreCursor.visit(
            batch,
            state -> !state.isAir(),
            (position, state) -> this.queueCoreBlock(context, position)
        );
        if (!this.coreCursor.finished()) {
            context.updatePersistentState(tag -> tag.put(CORE_CURSOR, this.coreCursor.save()));
            return;
        }
        this.coreDone = true;
        this.coreCursor = null;
        context.updatePersistentState(tag -> {
            tag.putBoolean(CORE_DONE, true);
            tag.put(CORE_CURSOR, new CompoundTag());
        });
    }

    private void queueCoreBlock(GameplayEffectContext context, BlockPos position) {
        double dx = position.getX() + 0.5 - this.center.x;
        double dy = position.getY() + 0.5 - this.center.y;
        double dz = position.getZ() + 0.5 - this.center.z;
        double boundary = Math.max(0.0, this.coreRadius - 1.5);
        boolean interior = dx * dx + dy * dy + dz * dz <= boundary * boundary;
        BlockChange change = interior
            ? BlockChange.airWithoutNeighbourUpdates(position)
            : new BlockChange(position, Blocks.AIR.defaultBlockState());
        context.queueBlockChange(change);
    }

    private void tickSurfaceTear(GameplayEffectContext context) {
        if (this.destructionComplete
            || this.craterEditor == null
            || this.craterEditor.progress().finished()
            || this.frontRadius <= 0.0) {
            return;
        }
        int headroom = (int)Math.max(0.0, MAX_PENDING_HEADROOM - context.pendingBlockChanges());
        int maxRays = headroom / TEAR_CLUSTER_BLOCKS;
        if (maxRays <= 0) {
            return;
        }
        double band = this.tearBand();
        double tearRadius = tearRadius(this.frontRadius, this.craterRadius, band);
        if (tearRadius <= 0.0) {
            return;
        }
        int rays = Math.min(this.tearRayCount(tearRadius), maxRays);
        RandomSource random = context.random();
        double innerSquared = Math.max(0.0, this.frontRadius - band);
        innerSquared *= innerSquared;
        for (int index = 0; index < rays; index++) {
            Vec3 direction = randomDirection(random);
            Vec3 from = this.center.add(direction.scale(tearRadius));
            EffectRaycastResult result = context.world().raycast(
                from,
                this.center,
                state -> !state.isAir() && state.getFluidState().isEmpty()
            );
            if (result.status() == EffectRaycastStatus.BUDGET_EXHAUSTED
                || result.status() == EffectRaycastStatus.UNLOADED) {
                break;
            }
            if (!result.isHit()) {
                continue;
            }
            BlockPos position = result.hit().orElseThrow().position();
            double dx = position.getX() + 0.5 - this.center.x;
            double dy = position.getY() + 0.5 - this.center.y;
            double dz = position.getZ() + 0.5 - this.center.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < innerSquared
                || !this.isInsideCrater(position.getX() + 0.5, position.getY() + 0.5,
                    position.getZ() + 0.5)) {
                continue;
            }
            this.queueTearCluster(context, position);
        }
    }

    private boolean isInsideCrater(double x, double y, double z) {
        if (this.craterRadius <= 0.0 || this.craterDepth <= 0.0) {
            return false;
        }
        double dx = (x - this.center.x) / this.craterRadius;
        double dy = (y - this.center.y) / this.craterDepth;
        double dz = (z - this.center.z) / this.craterRadius;
        return dx * dx + dy * dy + dz * dz <= 1.0;
    }

    private double tearBand() {
        return Math.max(2.0, Math.min(this.craterRadius, this.coreRadius * 0.5));
    }

    private int tearRayCount(double radius) {
        int maxRays = this.settings.tearRaysPerTick();
        if (maxRays <= 0) {
            return 0;
        }
        double spacing = Math.max(2.0, this.coreRadius * 0.5);
        int wanted = (int)Math.ceil(2.0 * Math.PI * radius / spacing);
        return Math.max(1, Math.min(maxRays, wanted));
    }

    private void queueTearCluster(GameplayEffectContext context, BlockPos center) {
        for (int x = -TEAR_CLUSTER_RADIUS; x <= TEAR_CLUSTER_RADIUS; x++) {
            for (int y = -TEAR_CLUSTER_RADIUS; y <= TEAR_CLUSTER_RADIUS; y++) {
                for (int z = -TEAR_CLUSTER_RADIUS; z <= TEAR_CLUSTER_RADIUS; z++) {
                    BlockPos position = center.offset(x, y, z);
                    if (!context.level().isInWorldBounds(position)
                        || !this.isInsideCrater(
                            position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5
                        )) {
                        continue;
                    }
                    context.queueBlockChange(
                        new BlockChange(position, Blocks.AIR.defaultBlockState()),
                        this::recordTearState,
                        BlockJournalMode.SECTION
                    );
                }
            }
        }
    }

    private void recordTearState(EffectBlockSample sample) {
        BlockState state = sample.state();
        if (state.isAir() || this.debrisStates.size() >= MAX_DEBRIS_STATES
            || this.debrisStates.contains(state)) {
            return;
        }
        this.debrisStates.add(state);
    }

    private void emitDebris(GameplayEffectContext context, float progress) {
        try {
            this.ensureDebrisPalette();
            EffectAudience audience = EffectAudience.nearby(
                Math.min(512.0, this.attractionRadius + 128.0)
            );
            boolean active = this.hasCrater
                && this.craterEditor != null
                && this.frontRadius > 0.0
                && progress >= ContentBlackHoleCinematics.HORIZON_END
                && progress < ContentBlackHoleCinematics.DESTABILISE;
            if (active) {
                double band = this.tearBand();
                double groundFront = groundFrontRadius(
                    this.frontRadius,
                    this.craterRadius,
                    this.craterDepth,
                    this.center.y,
                    this.craterGroundY
                );
                if (groundFront > 0.0) {
                    double inner = Math.max(0.0, groundFront - band);
                    double maxGround = groundFrontRadius(
                        this.craterRadius,
                        this.craterRadius,
                        this.craterDepth,
                        this.center.y,
                        this.craterGroundY
                    );
                    double outer = Math.min(maxGround, groundFront + band);
                    if (outer > inner) {
                        this.emitSurface(
                            context,
                            this.center,
                            inner,
                            outer,
                            this.settings.surfaceBurstsPerTick(),
                            audience
                        );
                    }
                }
            }
            this.emitAccretion(context, audience);
        } catch (RuntimeException exception) {
            // Debris is cosmetic: a bad burst must never cancel the gameplay session.
            LOGGER.warn("Could not emit black-hole debris", exception);
        }
    }

    /**
     * Whole-block chunks that leave the terrain surface and spiral in. The client projects each
     * candidate onto its local heightmap, so pieces never float and use the real block texture.
     */
    private void emitSurface(
        GameplayEffectContext context,
        Vec3 center,
        double innerRadius,
        double outerRadius,
        int bursts,
        EffectAudience audience
    ) {
        if (bursts <= 0 || outerRadius <= 0.0) {
            return;
        }
        double inner = Math.min(Math.max(0.0, innerRadius), outerRadius);
        int states = this.debrisStates.size();
        for (int index = 0; index < bursts; index++) {
            BlockState state = this.debrisStates.get(
                (int)((context.ageTicks() + index) % states)
            );
            context.spawnDebris(EffectDebrisSpec.infallField(
                center,
                state,
                SURFACE_PIECES_PER_BURST,
                0.35,
                0.65,
                this.settings.debrisLifetimeTicks(),
                audience,
                this.debrisAttractor,
                new EffectDebrisSpawn.Surface(center, inner, outerRadius, SURFACE_LIFT),
                SURFACE_PIECE_SCALE
            ));
        }
    }

    private void emitAccretion(GameplayEffectContext context, EffectAudience audience) {
        double inflowRadius = this.inflowRadius(context);
        EffectDebrisSpawn shell = new EffectDebrisSpawn.Shell(
            this.center,
            this.coreRadius * 1.1,
            Math.max(this.coreRadius * 1.11, inflowRadius)
        );
        double groundFront = this.hasCrater
            ? groundFrontRadius(
                this.frontRadius,
                this.craterRadius,
                this.craterDepth,
                this.center.y,
                this.craterGroundY
            )
            : 0.0;
        double groundRadius = groundFront > 0.0
            ? Math.min(inflowRadius, groundFront)
            : Math.min(inflowRadius, this.coreRadius * 1.2);
        double diskMin = debrisDiskInnerRadius(this.coreRadius, groundRadius);
        boolean diskActive = this.hasCrater && groundRadius > diskMin + 0.5;
        double diskY = Math.min(this.center.y, this.craterGroundY) + 6.0;
        EffectDebrisSpawn disk = diskActive
            ? new EffectDebrisSpawn.Disk(
                new Vec3(this.center.x, diskY, this.center.z),
                diskMin,
                groundRadius,
                5.0
            )
            : shell;
        float pieceScale = Math.min(8.0F, Math.max(3.0F, context.spec().scale() / 1.2F));
        BlockState state = this.debrisStates.get(
            context.ageTicks() % this.debrisStates.size()
        );
        EffectDebrisSpawn spawn = (context.ageTicks() % 4 == 0) ? shell : disk;
        context.spawnDebris(EffectDebrisSpec.infallField(
            this.center,
            state,
            this.settings.debrisPiecesPerBurst(),
            0.35,
            0.65,
            this.settings.debrisLifetimeTicks(),
            audience,
            this.debrisAttractor,
            spawn,
            pieceScale
        ));
    }

    private double inflowRadius(GameplayEffectContext context) {
        double fullRadius = Math.min(1_024.0, Math.max(this.coreRadius, this.attractionRadius * 0.9));
        double startRadius = this.coreRadius * 1.2;
        int duration = Math.max(1, context.spec().phases().totalDurationTicks());
        double openTick = ContentBlackHoleCinematics.HORIZON_END * duration;
        double t = Math.max(0.0, Math.min(1.0, (context.ageTicks() - openTick) / 24.0));
        double smooth = t * t * (3.0 - 2.0 * t);
        return startRadius + (fullRadius - startRadius) * smooth;
    }

    private void tickForces(GameplayEffectContext context, ContentBlackHoleCinematics.Frame frame) {
        double radius = this.attractionRadius * Math.max(0.0, frame.horizonScale());
        if (radius >= 0.5) {
            double collapse = frame.horizonScale() < 1.0F ? 1.5 : 1.0;
            EffectForceField field = EffectForceField.accretion(
                new EffectPoint(this.center.x, this.center.y, this.center.z),
                this.diskAxis,
                radius,
                this.settings.inwardStrength() * collapse,
                this.settings.tangentialStrength(),
                0.0,
                this.settings.falloff(),
                this.settings.maxForceTargets()
            );
            context.applyForceField(field, this::mayAffect);
        }

        double consumeRadius = Math.max(0.75, this.coreRadius * Math.max(0.05, frame.horizonScale()));
        EffectDamageShape.Sphere horizon = new EffectDamageShape.Sphere(
            new EffectPoint(this.center.x, this.center.y, this.center.z),
            consumeRadius
        );
        context.applyHitbox(
            EffectHitbox.removal(horizon, this.settings.maxConsumeTargets()),
            context.level().damageSources().fellOutOfWorld(),
            this::mayAffectLiving
        );
    }

    private boolean mayAffect(net.minecraft.world.entity.Entity entity) {
        if (!entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        return !(entity instanceof Player player)
            || this.settings.affectsCreativePlayers()
            || !player.isCreative();
    }

    private boolean mayAffectLiving(LivingEntity entity) {
        return this.mayAffect(entity);
    }

    private void planDestruction(GameplayEffectContext context) {
        int duration = Math.max(1, context.spec().phases().totalDurationTicks());
        int configuredBudget = EffectBudgets.sectionEditsPerLevelTick();
        int effectBudget = context.spec().budget().maxSectionEdits();
        int sectionBudget = Math.max(1, Math.min(
            effectBudget > 0 ? effectBudget : configuredBudget,
            configuredBudget
        ));
        double coreRadiusCubed = this.coreRadius * this.coreRadius * this.coreRadius;
        double coreFullVolume = (4.0 / 3.0) * Math.PI * coreRadiusCubed;
        double coreTop = Math.min(this.center.y + this.coreRadius, this.craterGroundY);
        double coreBottom = Math.max(
            this.center.y - this.coreRadius, context.level().getMinBuildHeight()
        );
        double coreFraction = coreTop <= coreBottom
            ? 0.0
            : Math.min(1.0, (coreTop - coreBottom) / (2.0 * this.coreRadius));
        double coreVolume = coreFullVolume * coreFraction;
        int coreBlocks = Math.max(1, Math.min(
            this.settings.coreBlocksPerTick(),
            EffectBudgets.blockChangesPerLevelTick()
        ));
        int coreTicks = (int)Math.ceil(coreVolume / coreBlocks);
        float coreFinish = ContentBlackHoleCinematics.IGNITION + coreTicks / (float)duration;
        this.destruction = ContentBlackHoleDestructionPlan.plan(
            this.settings,
            context.spec().scale(),
            duration,
            sectionBudget,
            coreFinish,
            this.center.y,
            this.craterGroundY,
            context.level().getMinBuildHeight()
        );
        this.craterRadius = this.destruction.radius();
        this.craterDepth = this.destruction.depth();
        this.frontRadius = 0.0;
    }

    private void syncCraterVolumeFromEditor() {
        if (this.craterEditor == null) {
            return;
        }
        if (this.craterEditor.volume() instanceof EffectBlockVolume.Ellipsoid ellipsoid) {
            this.craterRadius = ellipsoid.radiusX();
            this.craterDepth = ellipsoid.radiusY();
        }
    }

    private void putDestructionPlan(CompoundTag tag) {
        tag.putDouble(CRATER_RADIUS, this.craterRadius);
        tag.putDouble(CRATER_DEPTH, this.craterDepth);
        tag.putDouble(CRATER_GROUND_Y, this.craterGroundY);
        tag.putFloat(CRATER_START, this.destruction.startProgress());
        tag.putFloat(CRATER_WORK_END, this.destruction.workEndProgress());
        tag.putInt(CRATER_SOLID_SECTIONS, this.destruction.solidSections());
        tag.putInt(CRATER_ESTIMATED_SECTIONS, this.destruction.estimatedSections());
    }

    private EffectBlockCursor restoreCursor(GameplayEffectContext context) {
        CompoundTag state = context.persistentState();
        EffectBlockVolume volume = EffectBlockVolume.sphere(
            new EffectPoint(this.center.x, this.center.y, this.center.z),
            this.coreRadius
        );
        if (state.contains(CORE_CURSOR)) {
            return EffectBlockCursor.restore(context.world(), volume, state.getCompound(CORE_CURSOR));
        }
        return context.world().cursor(volume);
    }

    private void readState(GameplayEffectContext context) {
        CompoundTag state = context.persistentState();
        this.yaw = state.getFloat(YAW);
        this.pitch = state.getFloat(PITCH);
        this.roll = state.getFloat(ROLL);
        this.coreStarted = state.getBoolean(CORE_STARTED);
        this.coreDone = state.getBoolean(CORE_DONE);
        this.craterSyncReleased = state.getBoolean(CRATER_RELEASED);
        this.destructionIncomplete = state.getBoolean(CRATER_INCOMPLETE);
        this.destructionComplete = this.craterSyncReleased && !this.destructionIncomplete;
        this.craterEndWarned = this.destructionIncomplete;
        if (state.contains(CRATER_GROUND_Y)) {
            this.craterGroundY = state.getDouble(CRATER_GROUND_Y);
        }
        if (state.contains(CRATER_RADIUS) && state.contains(CRATER_DEPTH)
            && state.contains(CRATER_START) && state.contains(CRATER_WORK_END)) {
            this.craterRadius = state.getDouble(CRATER_RADIUS);
            this.craterDepth = state.getDouble(CRATER_DEPTH);
            this.destruction = ContentBlackHoleDestructionPlan.restored(
                this.craterRadius,
                this.craterDepth,
                state.getFloat(CRATER_START),
                state.getFloat(CRATER_WORK_END),
                EffectBudgets.sectionEditsPerLevelTick(),
                state.getInt(CRATER_SOLID_SECTIONS),
                state.getInt(CRATER_ESTIMATED_SECTIONS)
            );
        }
        CompoundTag craterTag = state.getCompound(CRATER_EDIT);
        if (!craterTag.isEmpty()) {
            this.craterEditor = EffectBulkBlockEditor.restore(
                context, craterTag, EffectBulkBlockEditor.Ordering.TOP_DOWN
            );
            this.syncCraterVolumeFromEditor();
            this.frontRadius = this.craterEditor.frontScale() * this.craterRadius;
        }
        if (this.destruction != null && this.destruction.solidSections() <= 0
            && this.craterEditor != null) {
            int total = this.craterEditor.progress().totalSections();
            this.destruction = ContentBlackHoleDestructionPlan.restored(
                this.craterRadius,
                this.craterDepth,
                this.destruction.startProgress(),
                this.destruction.workEndProgress(),
                this.destruction.sectionBudget(),
                total,
                total
            );
            context.updatePersistentState(this::putDestructionPlan);
        }
    }

    static double debrisDiskInnerRadius(double coreRadius, double groundRadius) {
        // Keep the hole in the middle of the ground disc without ever exceeding its outer radius.
        return Math.min(groundRadius * 0.15, coreRadius);
    }

    /** Radius of the rim being torn out, always inside the final crater. */
    static double tearRadius(double frontRadius, double craterRadius, double band) {
        return Math.min(craterRadius, Math.max(0.0, frontRadius + band));
    }

    /**
     * Projects the core-centred front onto the ground plane. A hole hovering above the surface
     * opens a smaller ground ring than its full 3D radius; a core below the surface behaves the
     * same way with a negative height.
     */
    static double groundFrontRadius(
        double frontRadius,
        double craterRadius,
        double craterDepth,
        double centerY,
        double groundY
    ) {
        if (frontRadius <= 0.0 || craterRadius <= 0.0 || craterDepth <= 0.0) {
            return 0.0;
        }
        double normalizedRadius = frontRadius / craterRadius;
        double normalizedHeight = (centerY - groundY) / craterDepth;
        double groundSquared = normalizedRadius * normalizedRadius
            - normalizedHeight * normalizedHeight;
        return groundSquared <= 0.0 ? 0.0 : craterRadius * Math.sqrt(groundSquared);
    }

    private static Vec3 randomDirection(RandomSource random) {
        double x;
        double y;
        double z;
        double lengthSquared;
        do {
            x = random.nextDouble() * 2.0 - 1.0;
            y = random.nextDouble() * 2.0 - 1.0;
            z = random.nextDouble() * 2.0 - 1.0;
            lengthSquared = x * x + y * y + z * z;
        } while (lengthSquared <= 1.0E-6 || lengthSquared > 1.0);
        double scale = 1.0 / Math.sqrt(lengthSquared);
        return new Vec3(x * scale, y * scale, z * scale);
    }

    private static Vec3 diskAxis(float yaw, float pitch, float roll) {
        Vector3f normal = ContentBlackHoleGeometry.basis(yaw, pitch, roll).normal();
        return new Vec3(normal.x, normal.y, normal.z).normalize();
    }
}
