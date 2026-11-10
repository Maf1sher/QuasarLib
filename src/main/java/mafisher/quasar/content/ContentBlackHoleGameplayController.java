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
 * Content-owned black-hole gameplay: a budgeted core carve, visible surface tearing, accretion
 * forces, and horizon consumption. The visual child is owned by the session scope and uses the
 * same cinematic progress, so pausing, resume, and cleanup stay in one place.
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
    private static final String SCRAPE_EDIT = "scrape_edit";
    private static final String CRATER_RELEASED = "crater_released";
    private static final String CRATER_X = "crater_x";
    private static final String CRATER_Y = "crater_y";
    private static final String CRATER_Z = "crater_z";

    private final ContentBlackHoleGameplaySettings settings;
    private Vec3 center = Vec3.ZERO;
    private Vec3 craterCenter = Vec3.ZERO;
    private double coreRadius;
    private double attractionRadius;
    private EffectDebrisAttractor debrisAttractor;
    private Vec3 diskAxis = new Vec3(0.0, 1.0, 0.0);
    private float yaw;
    private float pitch;
    private float roll;

    private static final int TEAR_CLUSTER_RADIUS = 1;
    private static final int TEAR_CLUSTER_BLOCKS = 27;
    private static final int MAX_TEAR_STATES = 12;

    private EffectBlockCursor coreCursor;
    private boolean coreStarted;
    private boolean coreDone;
    private boolean visualStarted;
    private boolean hasCrater;
    private boolean craterSyncReleased;
    private EffectBulkBlockEditor craterEditor;
    private EffectBulkBlockEditor scrapeEditor;
    private final List<BlockState> tearStates = new ArrayList<>(MAX_TEAR_STATES);

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
        this.craterCenter = this.computeCraterCenter(context);
        this.hasCrater = this.craterCenter.y > context.level().getMinBuildHeight() + 1.0;
        this.ensureDebrisPalette();
        if (!context.persistentState().contains(CRATER_Y)) {
            context.updatePersistentState(tag -> {
                tag.putFloat(YAW, this.yaw);
                tag.putFloat(PITCH, this.pitch);
                tag.putFloat(ROLL, this.roll);
                putCraterCenter(tag, this.craterCenter);
            });
        }
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
        if (!context.persistentState().contains(CRATER_Y)) {
            this.craterCenter = this.computeCraterCenter(context);
        }
        this.hasCrater = this.craterCenter.y > context.level().getMinBuildHeight() + 1.0;
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
            && progress < ContentBlackHoleCinematics.DESTABILISE) {
            this.tickSurfaceTear(context, progress);
        }
        this.tickCrater(context, progress);
        if (progress >= ContentBlackHoleCinematics.HORIZON_END
            && progress < ContentBlackHoleCinematics.IMPLOSION) {
            this.emitTearDebris(context, this.tearRadius(progress));
            this.tickForces(context, frame);
        }
        if (progress >= ContentBlackHoleCinematics.IMPLOSION) {
            this.tearStates.clear();
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
            this.craterEditor.close(keepChanges);
            this.craterEditor = null;
        }
        if (this.scrapeEditor != null) {
            this.scrapeEditor.close(keepChanges);
            this.scrapeEditor = null;
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

    private Vec3 computeCraterCenter(GameplayEffectContext context) {
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
            return this.center;
        }
        return new Vec3(this.center.x, Math.min(this.center.y, groundY), this.center.z);
    }

    private void ensureDebrisPalette() {
        if (!this.tearStates.isEmpty()) {
            return;
        }
        this.tearStates.add(Blocks.STONE.defaultBlockState());
        this.tearStates.add(Blocks.DIRT.defaultBlockState());
        this.tearStates.add(Blocks.GRASS_BLOCK.defaultBlockState());
    }

    private void tickCrater(GameplayEffectContext context, float progress) {
        if (!this.hasCrater) {
            return;
        }
        boolean open = progress >= ContentBlackHoleCinematics.HORIZON_END
            && progress < ContentBlackHoleCinematics.IMPLOSION;
        if (open && this.scrapeEditor == null) {
            this.scrapeEditor = this.createScrapeEditor(context);
        }
        if (progress >= ContentBlackHoleCinematics.DESTABILISE
            && progress < ContentBlackHoleCinematics.IMPLOSION
            && this.craterEditor == null) {
            this.craterEditor = this.createCraterEditor(context);
        }
        if (this.craterEditor != null) {
            this.craterEditor.tick();
        }
        if (this.scrapeEditor != null) {
            this.scrapeEditor.tick();
        }
        this.mergeClearedStates(this.scrapeEditor);
        this.mergeClearedStates(this.craterEditor);
    }

    private EffectBulkBlockEditor createCraterEditor(GameplayEffectContext context) {
        double radius = this.settings.craterRadius(context.spec().scale());
        double depth = Math.min(
            this.settings.craterDepth(context.spec().scale()),
            Math.max(1.0, this.craterCenter.y - context.level().getMinBuildHeight() - 1.0)
        );
        EffectBlockVolume volume = EffectBlockVolume.ellipsoid(
            new EffectPoint(this.craterCenter.x, this.craterCenter.y, this.craterCenter.z),
            radius,
            depth,
            radius
        );
        return EffectBulkBlockEditor.create(context, new EffectBulkBlockEditor.Spec(
            volume,
            Blocks.AIR.defaultBlockState(),
            true,
            EffectBulkBlockEditor.SyncPolicy.MANUAL,
            this.settings.craterImmediateSyncRadius(),
            this.settings.craterJournal()
        ));
    }

    private EffectBulkBlockEditor createScrapeEditor(GameplayEffectContext context) {
        double radius = this.settings.craterRadius(context.spec().scale())
            * this.settings.surfaceScrapeRadiusFactor();
        double bottom = this.craterCenter.y - this.settings.surfaceScrapeDepth();
        double top = this.craterCenter.y + 12.0;
        EffectBlockVolume volume = EffectBlockVolume.cylinder(
            new EffectPoint(this.craterCenter.x, this.craterCenter.y, this.craterCenter.z),
            radius,
            bottom,
            top
        );
        return EffectBulkBlockEditor.create(context, new EffectBulkBlockEditor.Spec(
            volume,
            Blocks.AIR.defaultBlockState(),
            true,
            EffectBulkBlockEditor.SyncPolicy.MANUAL,
            this.settings.craterImmediateSyncRadius(),
            this.settings.craterJournal()
        ));
    }

    private void mergeClearedStates(EffectBulkBlockEditor editor) {
        if (editor == null) {
            return;
        }
        for (BlockState state : editor.clearedStates()) {
            if (this.tearStates.size() >= MAX_TEAR_STATES) {
                return;
            }
            if (!this.tearStates.contains(state)) {
                this.tearStates.add(state);
            }
        }
    }

    private void releaseCraterSync(GameplayEffectContext context) {
        if (this.craterSyncReleased) {
            return;
        }
        this.craterSyncReleased = true;
        if (this.scrapeEditor != null) {
            this.scrapeEditor.releaseSync();
        }
        if (this.craterEditor != null) {
            this.craterEditor.releaseSync();
        }
        context.updatePersistentState(tag -> tag.putBoolean(CRATER_RELEASED, true));
    }

    private void saveEditorState(GameplayEffectContext context) {
        if (this.craterEditor == null && this.scrapeEditor == null) {
            return;
        }
        if ((context.ageTicks() & 7) != 0) {
            return;
        }
        context.updatePersistentState(tag -> {
            if (this.craterEditor != null) {
                tag.put(CRATER_EDIT, this.craterEditor.save());
            }
            if (this.scrapeEditor != null) {
                tag.put(SCRAPE_EDIT, this.scrapeEditor.save());
            }
            tag.putBoolean(CRATER_RELEASED, this.craterSyncReleased);
            putCraterCenter(tag, this.craterCenter);
        });
    }

    private static void putCraterCenter(CompoundTag tag, Vec3 center) {
        tag.putDouble(CRATER_X, center.x);
        tag.putDouble(CRATER_Y, center.y);
        tag.putDouble(CRATER_Z, center.z);
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

    private void tickSurfaceTear(GameplayEffectContext context, float progress) {
        int headroom = (int)Math.max(0.0, MAX_PENDING_HEADROOM - context.pendingBlockChanges());
        int maxRays = headroom / TEAR_CLUSTER_BLOCKS;
        if (maxRays <= 0) {
            return;
        }
        double tearRadius = this.tearRadius(progress);

        int rays = Math.min(this.settings.tearRaysPerTick(), maxRays);
        RandomSource random = context.random();
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
            if (dx * dx + dy * dy + dz * dz <= this.coreRadius * this.coreRadius) {
                continue;
            }
            this.queueTearCluster(context, position);
        }
    }

    private void queueTearCluster(GameplayEffectContext context, BlockPos center) {
        for (int x = -TEAR_CLUSTER_RADIUS; x <= TEAR_CLUSTER_RADIUS; x++) {
            for (int y = -TEAR_CLUSTER_RADIUS; y <= TEAR_CLUSTER_RADIUS; y++) {
                for (int z = -TEAR_CLUSTER_RADIUS; z <= TEAR_CLUSTER_RADIUS; z++) {
                    BlockPos position = center.offset(x, y, z);
                    if (!context.level().isInWorldBounds(position)) {
                        continue;
                    }
                    context.queueBlockChange(
                        new BlockChange(position, Blocks.AIR.defaultBlockState()),
                        this::recordTearState
                    );
                }
            }
        }
    }

    private void recordTearState(EffectBlockSample sample) {
        BlockState state = sample.state();
        if (state.isAir() || this.tearStates.size() >= MAX_TEAR_STATES
            || this.tearStates.contains(state)) {
            return;
        }
        this.tearStates.add(state);
    }

    private void emitTearDebris(GameplayEffectContext context, double tearRadius) {
        try {
            this.ensureDebrisPalette();
            int bursts = this.settings.debrisBurstsPerTick();
            double outerRadius = Math.min(
                this.attractionRadius * 0.9, Math.max(this.coreRadius * 1.15, tearRadius)
            );
            EffectDebrisSpawn shell = new EffectDebrisSpawn.Shell(
                this.center, this.coreRadius * 1.1, outerRadius
            );
            double craterRadius = this.settings.craterRadius(context.spec().scale());
            double diskMin = debrisDiskMinRadius(craterRadius, outerRadius);
            EffectDebrisSpawn disk = this.hasCrater
                ? new EffectDebrisSpawn.Disk(this.craterCenter, diskMin, outerRadius, 2.5)
                : shell;
            EffectAudience audience = EffectAudience.nearby(
                Math.min(512.0, this.attractionRadius + 128.0)
            );
            for (int index = 0; index < bursts; index++) {
                BlockState state = this.tearStates.get(index % this.tearStates.size());
                EffectDebrisSpawn spawn = (index & 1) == 0 ? disk : shell;
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
                    4.5F
                ));
            }
        } catch (RuntimeException exception) {
            // Debris is cosmetic: a bad burst must never cancel the gameplay session.
            LOGGER.warn("Could not emit black-hole debris", exception);
        }
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

    private double tearRadius(float progress) {
        double start = Math.max(this.coreRadius * 1.05, this.coreRadius * this.settings.tearStartFactor());
        double end = this.attractionRadius;
        double window = Math.max(1.0E-6, ContentBlackHoleCinematics.DESTABILISE
            - ContentBlackHoleCinematics.HORIZON_END);
        double t = Math.max(0.0, Math.min(1.0,
            (progress - ContentBlackHoleCinematics.HORIZON_END) / (window * 0.55)));
        double eased = 1.0 - Math.pow(1.0 - t, 3.0);
        return start + (end - start) * eased;
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
        if (state.contains(CRATER_Y)) {
            this.craterCenter = new Vec3(
                state.getDouble(CRATER_X), state.getDouble(CRATER_Y), state.getDouble(CRATER_Z)
            );
        }
        CompoundTag craterTag = state.getCompound(CRATER_EDIT);
        if (!craterTag.isEmpty()) {
            this.craterEditor = EffectBulkBlockEditor.restore(context, craterTag);
        }
        CompoundTag scrapeTag = state.getCompound(SCRAPE_EDIT);
        if (!scrapeTag.isEmpty()) {
            this.scrapeEditor = EffectBulkBlockEditor.restore(context, scrapeTag);
        }
    }

    static double debrisDiskMinRadius(double craterRadius, double outerRadius) {
        // The disk must shrink with the early attraction shell; a fixed crater-rim inner radius
        // would exceed the outer radius during the first debris ticks and fail the session.
        return Math.min(craterRadius * 0.8, outerRadius * 0.55);
    }

    private static Vec3 diskAxis(float yaw, float pitch, float roll) {
        Vector3f normal = ContentBlackHoleGeometry.basis(yaw, pitch, roll).normal();
        return new Vec3(normal.x, normal.y, normal.z).normalize();
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
}
