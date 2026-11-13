package mafisher.quasar.content.blackhole;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.budget.EffectBudgets;
import mafisher.quasar.api.effect.damage.EffectDamageShape;
import mafisher.quasar.api.effect.debris.EffectDebrisAttractor;
import mafisher.quasar.api.effect.debris.EffectDebrisSpawn;
import mafisher.quasar.api.effect.debris.EffectDebrisSpec;
import mafisher.quasar.api.effect.force.EffectForceField;
import mafisher.quasar.api.effect.damage.EffectHitbox;
import mafisher.quasar.api.effect.EffectLifetimePolicy;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.block.BlockJournalMode;
import mafisher.quasar.api.gameplay.world.EffectBlockCursor;
import mafisher.quasar.api.gameplay.world.EffectBlockSample;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import mafisher.quasar.api.gameplay.block.EffectBulkBlockEditor;
import mafisher.quasar.api.gameplay.world.EffectRaycastResult;
import mafisher.quasar.api.gameplay.world.EffectRaycastStatus;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectController;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.content.ContentEffectIds;
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
 * Content-owned black-hole gameplay: an exact core carve, then an anchor-centred sphere whose
 * radius grows as removed terrain volume advances, plus accretion forces and horizon consumption.
 * Block edits end with a reveal reserve before the closing phase, leaving the outro visual-only.
 * Tearing rays sample the leading edge in every direction and emit straight radial debris from
 * the blocks they actually remove; the fragments vanish at the horizon. The visual child is owned
 * by the session scope and uses the same cinematic progress, so pausing, resume, and cleanup stay
 * in one place.
 */
public final class ContentBlackHoleGameplayController implements GameplayEffectController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentBlackHoleGameplayController.class);
    private static final ContentBlackHoleGameplaySettings SETTINGS =
        ContentBlackHoleGameplaySettings.DEFAULT;
    private static final double MAX_PENDING_HEADROOM = 12_000.0;
    private static final Map<ServerLevel, Set<UUID>> ACTIVE = new WeakHashMap<>();

    public static int stopAll(ServerLevel level) {
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
    private static final int TEAR_CLUSTER_RADIUS = 1;
    private static final int TEAR_CLUSTER_BLOCKS = 27;

    private final ContentBlackHoleGameplaySettings settings;
    private Vec3 center = Vec3.ZERO;
    private double coreRadius;
    private double attractionRadius;
    private EffectDebrisAttractor debrisAttractor;
    private EffectAudience debrisAudience;
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
    private boolean terrainLogged;
    private boolean craterBudgetWarned;
    private boolean craterEndWarned;
    private boolean destructionComplete;
    private boolean destructionIncomplete;
    private boolean craterEndChecked;
    private boolean coreDeadlineChecked;
    private double craterGroundY;
    private double craterRadius;
    private double frontRadius;
    private ContentBlackHoleDestructionPlan.Plan destruction;
    private EffectBulkBlockEditor terrainEditor;
    private int tearBurstsEmitted;

    public ContentBlackHoleGameplayController() {
        this(SETTINGS);
    }

    ContentBlackHoleGameplayController(ContentBlackHoleGameplaySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public void onStart(GameplayEffectContext context) {
        this.center = context.spec().anchor();
        this.coreRadius = this.settings.coreRadius(context.spec().scale());
        this.attractionRadius = this.settings.attractionRadius(context.spec().scale());
        this.readState(context);
        this.diskAxis = diskAxis(this.yaw, this.pitch, this.roll);
        this.debrisAttractor = ContentBlackHoleDebris.attractor(
            this.center, this.diskAxis, this.coreRadius
        );
        this.debrisAudience = EffectAudience.nearby(
            Math.min(512.0, this.attractionRadius + 128.0)
        );
        this.craterGroundY = this.sampleGroundY(context);
        this.planDestruction(context);
        this.hasCrater = this.destruction != null && ContentBlackHoleDestructionPlan.intersectsTerrain(
            this.center.y,
            this.craterRadius,
            this.craterGroundY,
            context.level().getMinBuildHeight()
        );
        this.warnIfCratersStayUnderground();
        if (!this.hasCrater || !ContentBlackHoleDestructionPlan.intersectsTerrain(
            this.center.y,
            this.coreRadius,
            this.craterGroundY,
            context.level().getMinBuildHeight()
        )) {
            // Nothing solid in the ignition sphere; skip the per-block core pass.
            this.coreDone = true;
        }
        if (this.terrainEditor == null && this.hasCrater && !this.destructionComplete) {
            this.terrainEditor = this.createTerrainEditor(context);
        }
        if (this.terrainEditor != null) {
            this.syncCraterVolumeFromEditor();
            this.frontRadius = this.terrainEditor.frontScale() * this.craterRadius;
        }
        if (this.coreStarted && !this.coreDone) {
            this.coreCursor = this.restoreCursor(context);
        }
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
        this.center = context.spec().anchor();
        this.coreRadius = this.settings.coreRadius(context.spec().scale());
        this.attractionRadius = this.settings.attractionRadius(context.spec().scale());
        this.readState(context);
        this.diskAxis = diskAxis(this.yaw, this.pitch, this.roll);
        this.debrisAttractor = ContentBlackHoleDebris.attractor(
            this.center, this.diskAxis, this.coreRadius
        );
        this.debrisAudience = EffectAudience.nearby(
            Math.min(512.0, this.attractionRadius + 128.0)
        );
        if (!context.persistentState().contains(CRATER_GROUND_Y)) {
            this.craterGroundY = this.sampleGroundY(context);
        }
        if (this.destruction == null || this.craterRadius <= 0.0) {
            this.planDestruction(context);
            context.updatePersistentState(this::putDestructionPlan);
        }
        this.hasCrater = this.destruction != null && ContentBlackHoleDestructionPlan.intersectsTerrain(
            this.center.y,
            this.craterRadius,
            this.craterGroundY,
            context.level().getMinBuildHeight()
        );
        this.warnIfCratersStayUnderground();
        if (!this.hasCrater || !ContentBlackHoleDestructionPlan.intersectsTerrain(
            this.center.y,
            this.coreRadius,
            this.craterGroundY,
            context.level().getMinBuildHeight()
        )) {
            // Nothing solid in the ignition sphere; skip the per-block core pass.
            this.coreDone = true;
        }
        if (this.terrainEditor == null && this.hasCrater && !this.destructionComplete) {
            this.terrainEditor = this.createTerrainEditor(context);
        }
        if (this.terrainEditor != null) {
            this.syncCraterVolumeFromEditor();
            this.frontRadius = Math.max(
                this.frontRadius,
                this.terrainEditor.frontScale() * this.craterRadius
            );
        }
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
        this.tearBurstsEmitted = 0;

        if (!this.coreDone) {
            if (this.destruction != null
                && progress >= this.destruction.workEndProgress()) {
                if (!this.coreDeadlineChecked) {
                    this.coreDeadlineChecked = true;
                    this.destructionIncomplete = true;
                    LOGGER.warn(
                        "Black-hole core carve did not finish before the block-edit deadline; "
                            + "the growing crater cannot start"
                    );
                    this.releaseCraterSync(context);
                }
            } else if (progress >= ContentBlackHoleCinematics.IGNITION) {
                this.tickCore(context);
            }
        }
        // The terrain front advances before tearing and debris read it.
        this.tickTerrain(context, progress);
        if (progress >= ContentBlackHoleCinematics.HORIZON_END
            && this.destruction != null
            && progress < this.destruction.workEndProgress()) {
            this.tickSurfaceTear(context);
        }
        if (progress >= ContentBlackHoleCinematics.HORIZON_END
            && progress < ContentBlackHoleCinematics.IMPLOSION) {
            this.tickForces(context, frame);
        }
        if (progress >= ContentBlackHoleCinematics.IMPLOSION) {
            this.releaseCraterSync(context);
        }
        this.saveEditorState(context);
    }

    @Override
    public void onEnd(GameplayEffectContext context, GameplayEffectEndReason reason) {
        this.coreCursor = null;
        boolean keepChanges = reason == GameplayEffectEndReason.EXPIRED
            || !context.canRollbackChanges();
        if (this.terrainEditor != null) {
            EffectBulkBlockEditor.Progress carved = this.terrainEditor.progress();
            if (!carved.finished() && !this.destructionComplete) {
                LOGGER.warn(
                    "Black-hole destruction stopped early: {} of {} sections, {} blocks",
                    carved.completedSections(),
                    carved.totalSections(),
                    carved.clearedBlocks()
                );
            }
            this.terrainEditor.close(keepChanges);
            this.terrainEditor = null;
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
        UUID visualId = context.scope().playVisual(new VisualEffectSpec(
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
        ), EffectLifetimePolicy.CANCEL_WITH_SCOPE).orElse(null);
        if (visualId == null) {
            LOGGER.warn(
                "Black-hole visual for gameplay session {} was not started; the visual budget rejected it",
                context.sessionId()
            );
            return;
        }
        LOGGER.info(
            "Started black-hole visual {} for gameplay session {} at scale {}",
            visualId,
            context.sessionId(),
            context.spec().scale()
        );
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

    private void tickTerrain(GameplayEffectContext context, float progress) {
        if (progress < ContentBlackHoleCinematics.IGNITION
            || !this.coreDone
            || !this.hasCrater
            || this.destruction == null
            || progress < this.destruction.startProgress()) {
            return;
        }
        if (this.destructionComplete) {
            this.frontRadius = this.craterRadius;
            return;
        }
        if (this.terrainEditor == null) {
            this.terrainEditor = this.createTerrainEditor(context);
        }
        if (!this.terrainLogged) {
            this.terrainLogged = true;
            LOGGER.info(
                "Starting black-hole destruction at scale {}: requested radius {}, "
                    + "actual radius {}, core radius {}, window {}..{}, sections {} (solid {}), "
                    + "budget {}/tick, {}/tick needed",
                context.spec().scale(),
                this.destruction.requestedRadius(),
                this.destruction.radius(),
                this.destruction.coreRadius(),
                this.destruction.startProgress(),
                this.destruction.workEndProgress(),
                this.destruction.estimatedSections(),
                this.destruction.solidSections(),
                this.destruction.sectionBudget(),
                this.destruction.sectionsPerTick()
            );
            if (this.destruction.budgetClamped()) {
                LOGGER.warn(
                    "Black-hole destruction scaled down from radius {} to radius {} "
                        + "to fit the effect window and budgets",
                    this.destruction.requestedRadius(),
                    this.destruction.radius()
                );
            }
            if (this.destruction.sectionsPerTick() > this.destruction.sectionBudget()
                && !this.craterBudgetWarned) {
                this.craterBudgetWarned = true;
                LOGGER.warn(
                    "Black-hole destruction needs {} sections/tick but the budget is {}; "
                        + "the front will finish as early as the budget allows",
                    this.destruction.sectionsPerTick(),
                    this.destruction.sectionBudget()
                );
            }
        }
        if (this.terrainEditor.progress().finished()) {
            this.finishCrater(context);
            return;
        }
        if (progress >= this.destruction.workEndProgress()) {
            if (!this.craterEndChecked) {
                this.craterEndChecked = true;
                // One last catch-up inside the work window, then no block edits in the closing phase.
                if (progress < ContentBlackHoleCinematics.DESTABILISE) {
                    if (this.terrainEditor.ordering() == EffectBulkBlockEditor.Ordering.CENTER_OUT) {
                        this.terrainEditor.tickToScale(1.0, this.destruction.sectionBudget());
                    } else {
                        this.terrainEditor.tick(this.destruction.sectionBudget());
                    }
                }
                this.frontRadius = Math.max(
                    this.frontRadius,
                    this.terrainEditor.frontScale() * this.craterRadius
                );
                if (this.terrainEditor.progress().finished()
                    || !this.terrainEditor.hasPendingSolidSections()) {
                    this.finishCrater(context);
                    return;
                }
                if (!this.craterEndWarned) {
                    this.craterEndWarned = true;
                    this.destructionIncomplete = true;
                    EffectBulkBlockEditor.Progress carved = this.terrainEditor.progress();
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

        double targetRadius = this.destruction.radiusAt(progress);
        if (targetRadius <= 0.0 || this.craterRadius <= 0.0) {
            return;
        }
        if (this.terrainEditor.ordering() == EffectBulkBlockEditor.Ordering.CENTER_OUT) {
            double targetScale = Math.min(1.0, targetRadius / this.craterRadius);
            this.terrainEditor.tickToScale(targetScale, this.destruction.sectionBudget());
        } else {
            // Legacy saves may still hold a TOP_DOWN cursor; keep it resumable.
            this.terrainEditor.tick(this.destruction.sectionBudget());
        }
        this.frontRadius = Math.max(
            this.frontRadius,
            this.terrainEditor.frontScale() * this.craterRadius
        );
        if (this.terrainEditor.progress().finished()) {
            this.finishCrater(context);
        }
    }

    private void finishCrater(GameplayEffectContext context) {
        this.frontRadius = this.craterRadius;
        this.craterEndChecked = true;
        this.destructionComplete = true;
        this.destructionIncomplete = false;
        if (!this.craterLogged && this.terrainEditor != null) {
            this.craterLogged = true;
            EffectBulkBlockEditor.Progress finished = this.terrainEditor.progress();
            LOGGER.info(
                "Finished black-hole destruction: {} sections, {} blocks",
                finished.completedSections(),
                finished.clearedBlocks()
            );
        }
        // Flush any remaining reveals while the horizon is still open.
        this.releaseCraterSync(context);
    }

    private EffectBulkBlockEditor createTerrainEditor(GameplayEffectContext context) {
        EffectBlockVolume volume = EffectBlockVolume.sphere(
            new EffectPoint(this.center.x, this.center.y, this.center.z),
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

    private void releaseCraterSync(GameplayEffectContext context) {
        if (this.craterSyncReleased) {
            context.updatePersistentState(
                tag -> tag.putBoolean(CRATER_INCOMPLETE, this.destructionIncomplete)
            );
            return;
        }
        this.craterSyncReleased = true;
        if (this.terrainEditor != null) {
            this.terrainEditor.releaseSync();
        }
        context.updatePersistentState(tag -> {
            tag.putBoolean(CRATER_RELEASED, true);
            tag.putBoolean(CRATER_INCOMPLETE, this.destructionIncomplete);
        });
    }

    private void saveEditorState(GameplayEffectContext context) {
        if (this.terrainEditor == null) {
            return;
        }
        if ((context.ageTicks() & 7) != 0) {
            return;
        }
        context.updatePersistentState(tag -> {
            tag.put(CRATER_EDIT, this.terrainEditor.save());
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
        int batch = Math.min(
            Math.min(
                Math.min(4_096, EffectBudgets.blockChangesPerLevelTick()),
                context.spec().budget().maxBlockChanges()
            ),
            headroom
        );
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
        if (position.getY() <= context.level().getMinBuildHeight()) {
            return;
        }
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

    private void tickSurfaceTear(GameplayEffectContext context) {
        if (this.destructionComplete
            || this.terrainEditor == null
            || this.terrainEditor.progress().finished()
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
        if (this.craterRadius <= 0.0) {
            return false;
        }
        double dx = x - this.center.x;
        double dy = y - this.center.y;
        double dz = z - this.center.z;
        return dx * dx + dy * dy + dz * dz <= this.craterRadius * this.craterRadius;
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

    private void queueTearCluster(GameplayEffectContext context, BlockPos hitPosition) {
        boolean emitsDebris = this.settings.tearDebrisBurstsPerTick() > 0;
        for (int x = -TEAR_CLUSTER_RADIUS; x <= TEAR_CLUSTER_RADIUS; x++) {
            for (int y = -TEAR_CLUSTER_RADIUS; y <= TEAR_CLUSTER_RADIUS; y++) {
                for (int z = -TEAR_CLUSTER_RADIUS; z <= TEAR_CLUSTER_RADIUS; z++) {
                    BlockPos position = hitPosition.offset(x, y, z);
                    if (!context.level().isInWorldBounds(position)
                        || position.getY() <= context.level().getMinBuildHeight()
                        || !this.isInsideCrater(
                            position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5
                        )) {
                        continue;
                    }
                    Consumer<EffectBlockSample> applied = emitsDebris
                        && position.equals(hitPosition)
                            ? sample -> this.emitTearDebris(context, sample)
                            : null;
                    context.queueBlockChange(
                        new BlockChange(position, Blocks.AIR.defaultBlockState()),
                        applied,
                        BlockJournalMode.SECTION
                    );
                }
            }
        }
    }

    /**
     * Emits one straight radial burst from the block that was actually removed. The callback only
     * runs after a successful write, so protection vetoes never show a block that stayed in place.
     */
    private void emitTearDebris(GameplayEffectContext context, EffectBlockSample sample) {
        if (this.tearBurstsEmitted >= this.settings.tearDebrisBurstsPerTick()
            || this.debrisAudience == null) {
            return;
        }
        BlockState state = sample.state();
        if (state.isAir()) {
            return;
        }
        Vec3 origin = Vec3.atCenterOf(sample.position());
        double distance = origin.distanceTo(this.center);
        if (distance <= this.coreRadius * 1.05 || distance > this.craterRadius * 1.25) {
            return;
        }
        int remaining = this.remainingTicksUntilImplosion(context);
        if (remaining < ContentBlackHoleDebris.MIN_LIFETIME_TICKS) {
            return;
        }
        int lifetime = Math.min(
            ContentBlackHoleDebris.lifetimeTicks(
                distance, this.settings.debrisFallSpeed(), this.settings.debrisMaxLifetimeTicks()
            ),
            remaining
        );
        this.tearBurstsEmitted++;
        context.spawnDebris(EffectDebrisSpec.infallField(
            origin,
            state,
            this.settings.tearDebrisPieces(),
            0.0,
            0.0,
            lifetime,
            this.debrisAudience,
            this.debrisAttractor,
            new EffectDebrisSpawn.Point(),
            this.settings.debrisPieceScale()
        ));
    }

    private int remainingTicksUntilImplosion(GameplayEffectContext context) {
        int duration = Math.max(1, context.spec().phases().totalDurationTicks());
        int implosionTick = Math.max(1, Math.round(
            ContentBlackHoleCinematics.IMPLOSION * duration
        ));
        return Math.max(0, implosionTick - context.ageTicks());
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
        int coreBlockBudget = Math.max(
            1,
            Math.min(
                Math.min(4_096, EffectBudgets.blockChangesPerLevelTick()),
                context.spec().budget().maxBlockChanges()
            )
        );
        double coreVolume = ContentBlackHoleDestructionPlan.terrainVolume(
            this.coreRadius,
            this.center.y,
            this.craterGroundY,
            context.level().getMinBuildHeight()
        );
        int coreTicks = (int)Math.ceil(coreVolume / coreBlockBudget);
        float coreFinish = ContentBlackHoleCinematics.IGNITION
            + coreTicks / (float)duration;
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
        this.frontRadius = 0.0;
    }

    private void syncCraterVolumeFromEditor() {
        if (this.terrainEditor == null) {
            return;
        }
        if (this.terrainEditor.volume() instanceof EffectBlockVolume.Sphere sphere) {
            this.craterRadius = sphere.radius();
        } else if (this.terrainEditor.volume() instanceof EffectBlockVolume.Ellipsoid ellipsoid) {
            // Legacy saves may still hold the old ellipsoid; treat it as one sphere for debris.
            this.craterRadius = Math.max(
                ellipsoid.radiusX(), Math.max(ellipsoid.radiusY(), ellipsoid.radiusZ())
            );
        }
    }

    private void putDestructionPlan(CompoundTag tag) {
        tag.putDouble(CRATER_RADIUS, this.craterRadius);
        tag.putDouble(CRATER_DEPTH, this.craterRadius);
        tag.putDouble(CRATER_GROUND_Y, this.craterGroundY);
        tag.putFloat(CRATER_START, this.destruction.startProgress());
        tag.putFloat(CRATER_WORK_END, this.destruction.workEndProgress());
        tag.putInt(CRATER_SOLID_SECTIONS, this.destruction.solidSections());
        tag.putInt(CRATER_ESTIMATED_SECTIONS, this.destruction.estimatedSections());
    }

    private void warnIfCratersStayUnderground() {
        if (!this.hasCrater || ContentBlackHoleDestructionPlan.breaksSurface(
            this.center.y, this.craterRadius, this.craterGroundY
        )) {
            return;
        }
        LOGGER.warn(
            "Black-hole destruction is fully underground at {}: the sphere does not break "
                + "the surface at y={} (core y={}, radius={}); no visible surface crater will form",
            this.center,
            this.craterGroundY,
            this.center.y,
            this.craterRadius
        );
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
        if (state.contains(CRATER_RADIUS)
            && state.contains(CRATER_START)
            && state.contains(CRATER_WORK_END)
            && state.contains(CRATER_GROUND_Y)) {
            double restoredRadius = state.getDouble(CRATER_RADIUS);
            if (state.contains(CRATER_DEPTH)) {
                // Legacy ellipsoid saves used a separate depth; a sphere takes the larger extent.
                restoredRadius = Math.max(restoredRadius, state.getDouble(CRATER_DEPTH));
            }
            this.craterRadius = restoredRadius;
            this.destruction = ContentBlackHoleDestructionPlan.restored(
                restoredRadius,
                this.coreRadius,
                state.getFloat(CRATER_START),
                state.getFloat(CRATER_WORK_END),
                EffectBudgets.sectionEditsPerLevelTick(),
                state.getInt(CRATER_SOLID_SECTIONS),
                state.getInt(CRATER_ESTIMATED_SECTIONS),
                context.spec().anchor().y(),
                this.craterGroundY,
                context.level().getMinBuildHeight()
            );
        }
        CompoundTag craterTag = state.getCompound(CRATER_EDIT);
        if (!craterTag.isEmpty()) {
            this.terrainEditor = EffectBulkBlockEditor.restore(
                context, craterTag, EffectBulkBlockEditor.Ordering.CENTER_OUT
            );
            this.syncCraterVolumeFromEditor();
            this.frontRadius = this.terrainEditor.frontScale() * this.craterRadius;
        }
        if (this.destruction != null && this.destruction.solidSections() <= 0
            && this.terrainEditor != null) {
            int total = this.terrainEditor.progress().totalSections();
            this.destruction = ContentBlackHoleDestructionPlan.restored(
                this.craterRadius,
                this.coreRadius,
                this.destruction.startProgress(),
                this.destruction.workEndProgress(),
                this.destruction.sectionBudget(),
                total,
                total,
                context.spec().anchor().y(),
                this.craterGroundY,
                context.level().getMinBuildHeight()
            );
            context.updatePersistentState(this::putDestructionPlan);
        }
    }

    /** Radius of the rim being torn out, always inside the final crater. */
    static double tearRadius(double frontRadius, double craterRadius, double band) {
        return Math.min(craterRadius, Math.max(0.0, frontRadius + band));
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
