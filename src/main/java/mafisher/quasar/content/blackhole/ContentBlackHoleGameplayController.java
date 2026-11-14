package mafisher.quasar.content.blackhole;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectBounds;
import mafisher.quasar.api.effect.damage.EffectDamageShape;
import mafisher.quasar.api.effect.debris.EffectDebrisAttractor;
import mafisher.quasar.api.effect.debris.EffectDebrisSpawn;
import mafisher.quasar.api.effect.force.EffectForceField;
import mafisher.quasar.api.effect.damage.EffectHitbox;
import mafisher.quasar.api.effect.EffectLifetimePolicy;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectSpace;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.gameplay.block.EffectCarvePlan;
import mafisher.quasar.api.gameplay.block.EffectCarveSession;
import mafisher.quasar.api.gameplay.block.EffectCarveSpec;
import mafisher.quasar.api.gameplay.block.EffectSurfaceTear;
import mafisher.quasar.api.gameplay.block.EffectWriteFeedback;
import mafisher.quasar.api.gameplay.world.EffectBlockCursor;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import mafisher.quasar.api.gameplay.world.EffectSurfaceSampler;
import mafisher.quasar.api.gameplay.block.EffectBulkBlockEditor;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectController;
import mafisher.quasar.api.gameplay.GameplayEffectEndReason;
import mafisher.quasar.api.gameplay.GameplayEffectHandle;
import mafisher.quasar.api.gameplay.GameplayEffectService;
import mafisher.quasar.content.ContentEffectIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
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
@SuppressWarnings("PMD.GodClass")
public final class ContentBlackHoleGameplayController implements GameplayEffectController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentBlackHoleGameplayController.class);
    private static final ContentBlackHoleGameplaySettings SETTINGS =
        ContentBlackHoleGameplaySettings.DEFAULT;
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
    private static final String CARVE_SESSION = "carve_session";

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

    private boolean visualStarted;
    private boolean hasCrater;
    private boolean craterSyncReleased;
    private boolean craterLogged;
    private boolean terrainLogged;
    private boolean craterBudgetWarned;
    private boolean craterEndWarned;
    private boolean legacyCarveComplete;
    private boolean legacyCarveIncomplete;
    private boolean coreDoneReported;
    private boolean incompleteReported;
    private double craterGroundY;
    private double craterRadius;
    private double frontRadius;
    private EffectCarvePlan carvePlan;
    private EffectCarveSession carveSession;
    private EffectSurfaceTear surfaceTear;
    private EffectSurfaceSampler surfaceSampler;
    private EffectWriteFeedback tearFeedback;

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
        this.hasCrater = this.carvePlan != null
            && intersectsTerrain(this.carvePlan.volume(), this.craterGroundY, context);
        this.warnIfCratersStayUnderground();
        if (this.hasCrater) {
            this.carveSession = EffectCarveSession.start(
                context, this.carveSpec(context), this.carvePlan
            );
            this.coreDoneReported = this.carveSession.coreComplete();
        } else {
            this.coreDoneReported = true;
        }
        this.frontRadius = 0.0;
        this.configureTear(context);
        context.updatePersistentState(tag -> {
            tag.putFloat(YAW, this.yaw);
            tag.putFloat(PITCH, this.pitch);
            tag.putFloat(ROLL, this.roll);
        });
        context.updatePersistentState(this::putDestructionPlan);
        context.updatePersistentState(this::putCarveSession);
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
        this.restoreCarve(context);
        this.hasCrater = this.carvePlan != null
            && intersectsTerrain(this.carvePlan.volume(), this.craterGroundY, context);
        this.warnIfCratersStayUnderground();
        if (this.hasCrater && this.carveSession == null
            && !this.legacyCarveComplete && !this.legacyCarveIncomplete) {
            this.carveSession = EffectCarveSession.start(
                context, this.carveSpec(context), this.carvePlan
            );
            this.carvePlan = this.carveSession.plan();
            this.craterRadius = radiusOf(this.carvePlan.volume());
            this.frontRadius = this.carveSession.frontScale() * this.craterRadius;
        }
        if (this.carveSession != null) {
            this.frontRadius = Math.max(
                this.frontRadius,
                this.carveSession.frontScale() * this.craterRadius
            );
            this.coreDoneReported = this.carveSession.coreComplete();
        } else if (this.legacyCarveComplete) {
            this.frontRadius = this.craterRadius;
        }
        this.configureTear(context);
        this.visualStarted = true;
        this.track(context);
    }

    @Override
    public void onTick(GameplayEffectContext context) {
        int duration = context.spec().phases().totalDurationTicks();
        float progress = Math.min(1.0F, Math.max(0.0F, context.ageTicks() / (float)duration));
        float horizonScale = ContentBlackHoleCinematics.parameters()
            .sampleFloat("HorizonScale", progress);

        // The terrain front advances before tearing and debris read it. The session owns the
        // exact core pre-pass, the bulk front, and the no-edits-after-deadline rule.
        this.tickTerrain(context, progress);
        if (progress >= ContentBlackHoleCinematics.HORIZON_END
            && this.carvePlan != null
            && this.carveSession != null
            && progress < this.carvePlan.workEnd()
            && !this.carveSession.editsFinished()) {
            this.tickSurfaceTear(context);
        }
        if (progress >= ContentBlackHoleCinematics.HORIZON_END
            && progress < ContentBlackHoleCinematics.IMPLOSION) {
            this.tickForces(context, horizonScale);
        }
        if (progress >= ContentBlackHoleCinematics.IMPLOSION) {
            this.releaseCraterSync(context);
        }
        this.saveEditorState(context);
    }

    @Override
    public void onEnd(GameplayEffectContext context, GameplayEffectEndReason reason) {
        boolean releaseReveal = reason == GameplayEffectEndReason.EXPIRED
            || !context.canRollbackChanges();
        if (this.carveSession != null) {
            EffectBulkBlockEditor.Progress carved = this.carveSession.progress();
            if (!carved.finished() && !this.carveSession.complete()) {
                LOGGER.warn(
                    "Black-hole destruction stopped early: {} of {} sections, {} blocks",
                    carved.completedSections(),
                    carved.totalSections(),
                    carved.clearedBlocks()
                );
            }
            this.carveSession.close(releaseReveal);
            this.carveSession = null;
        }
        this.surfaceSampler = null;
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
        if (this.surfaceSampler == null) {
            this.surfaceSampler = new EffectSurfaceSampler(context);
        }
        int x = Mth.floor(this.center.x);
        int z = Mth.floor(this.center.z);
        EffectSurfaceSampler.Column column = this.surfaceSampler.sampleColumn(
            x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
        );
        if (!column.loaded()) {
            // Never force-load the cinematic's column; an unavailable sample keeps the visual
            // alive and falls back to the authored anchor height.
            LOGGER.debug(
                "Black-hole ground sample at {},{} is {}; using the anchor Y {}",
                x, z, column.status(), this.center.y
            );
            return this.center.y;
        }
        int groundY = column.height();
        if (groundY <= context.level().getMinBuildHeight() + 1) {
            return this.center.y;
        }
        return groundY;
    }

    private void tickTerrain(GameplayEffectContext context, float progress) {
        if (!this.hasCrater || this.carvePlan == null || this.carveSession == null) {
            return;
        }
        if (!this.terrainLogged && progress >= this.carvePlan.growthStart()) {
            this.terrainLogged = true;
            LOGGER.info(
                "Starting black-hole destruction at scale {}: requested radius {}, "
                    + "actual radius {}, core radius {}, window {}..{}, sections {} (solid {}), "
                    + "budget {}/tick, {}/tick needed",
                context.spec().scale(),
                radiusOf(this.carvePlan.requestedVolume()),
                this.craterRadius,
                this.coreRadius,
                this.carvePlan.growthStart(),
                this.carvePlan.workEnd(),
                this.carvePlan.estimatedSections(),
                this.carvePlan.estimatedSolidSections(),
                this.carvePlan.sectionBudget(),
                this.carvePlan.sectionsPerTick()
            );
            if (this.carvePlan.budgetClamped() && !this.craterBudgetWarned) {
                this.craterBudgetWarned = true;
                LOGGER.warn(
                    "Black-hole destruction scaled down from radius {} to radius {} "
                        + "to fit the effect window and budgets",
                    radiusOf(this.carvePlan.requestedVolume()),
                    this.craterRadius
                );
            }
        }
        this.carveSession.tick();
        this.frontRadius = this.carveSession.frontScale() * this.craterRadius;
        if (this.carveSession.coreComplete() && !this.coreDoneReported) {
            this.coreDoneReported = true;
            context.updatePersistentState(tag -> tag.putBoolean(CORE_DONE, true));
        }
        if (this.carveSession.complete() && !this.craterLogged) {
            this.craterLogged = true;
            EffectBulkBlockEditor.Progress finished = this.carveSession.progress();
            LOGGER.info(
                "Finished black-hole destruction: {} sections, {} blocks",
                finished.completedSections(),
                finished.clearedBlocks()
            );
        }
        if (this.carveSession.incomplete() && !this.craterEndWarned) {
            this.craterEndWarned = true;
            EffectBulkBlockEditor.Progress carved = this.carveSession.progress();
            LOGGER.warn(
                "Black-hole destruction stopped at its reveal deadline: {} of {} sections, "
                    + "{} blocks; no block edits continue into the closing phase",
                carved.completedSections(),
                carved.totalSections(),
                carved.clearedBlocks()
            );
        }
        if (this.carveSession.syncReleased()
            || this.carveSession.complete()
            || progress >= this.carvePlan.workEnd()) {
            this.releaseCraterSync(context);
        }
    }

    private void releaseCraterSync(GameplayEffectContext context) {
        boolean incomplete = this.currentIncomplete();
        if (this.craterSyncReleased) {
            if (incomplete != this.incompleteReported) {
                this.incompleteReported = incomplete;
                context.updatePersistentState(
                    tag -> tag.putBoolean(CRATER_INCOMPLETE, incomplete)
                );
            }
            return;
        }
        this.craterSyncReleased = true;
        this.incompleteReported = incomplete;
        if (this.carveSession != null) {
            this.carveSession.releaseSync();
        }
        context.updatePersistentState(tag -> {
            tag.putBoolean(CRATER_RELEASED, true);
            tag.putBoolean(CRATER_INCOMPLETE, incomplete);
        });
    }

    private boolean currentIncomplete() {
        if (this.carveSession != null) {
            return this.carveSession.incomplete();
        }
        return this.legacyCarveIncomplete;
    }

    private void saveEditorState(GameplayEffectContext context) {
        if (this.carveSession == null) {
            return;
        }
        if ((context.ageTicks() & 7) != 0) {
            return;
        }
        context.updatePersistentState(this::putCarveSession);
    }

    private void putCarveSession(CompoundTag tag) {
        if (this.carveSession != null) {
            tag.put(CARVE_SESSION, this.carveSession.save());
        }
    }

    private void configureTear(GameplayEffectContext context) {
        if (this.carvePlan == null || this.carveSession == null) {
            this.surfaceTear = null;
            this.tearFeedback = null;
            return;
        }
        this.surfaceTear = new EffectSurfaceTear(new EffectSurfaceTear.Settings(
            this.tearBand(),
            this.settings.tearRaysPerTick(),
            Math.max(2.0, this.coreRadius * 0.5),
            1
        ));
        EffectWriteFeedback debris = EffectSurfaceTear.debrisFeedback(
            context,
            new EffectSurfaceTear.Debris(
                this.debrisAudience,
                this.debrisAttractor,
                new EffectDebrisSpawn.Point(),
                this.settings.tearDebrisPieces(),
                this.settings.debrisFallSpeed(),
                ContentBlackHoleDebris.MIN_LIFETIME_TICKS,
                this.settings.debrisMaxLifetimeTicks(),
                this.settings.debrisPieceScale(),
                this.settings.tearDebrisBurstsPerTick(),
                ContentBlackHoleCinematics.IMPLOSION
            )
        );
        this.tearFeedback = sample -> {
            Vec3 origin = Vec3.atCenterOf(sample.position());
            double distance = origin.distanceTo(this.center);
            if (distance > this.coreRadius * 1.05 && distance <= this.craterRadius * 1.25) {
                debris.onWritten(sample);
            }
        };
    }

    private void tickSurfaceTear(GameplayEffectContext context) {
        if (this.surfaceTear == null || this.tearFeedback == null
            || this.carveSession == null || this.frontRadius <= 0.0) {
            return;
        }
        this.surfaceTear.tick(
            context,
            new EffectSurfaceTear.Geometry(
                this.carvePlan.volume(), this.center, this.frontRadius, this.craterRadius
            ),
            this.tearFeedback
        );
    }

    private double tearBand() {
        return Math.max(2.0, Math.min(this.craterRadius, this.coreRadius * 0.5));
    }

    private void tickForces(GameplayEffectContext context, float horizonScale) {
        double radius = this.attractionRadius * Math.max(0.0, horizonScale);
        if (radius >= 0.5) {
            double collapse = horizonScale < 1.0F ? 1.5 : 1.0;
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

        double consumeRadius = Math.max(0.75, this.coreRadius * Math.max(0.05, horizonScale));
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
        this.carvePlan = EffectCarvePlan.plan(context, this.carveSpec(context));
        this.craterRadius = radiusOf(this.carvePlan.volume());
        this.frontRadius = 0.0;
    }

    private EffectCarveSpec carveSpec(GameplayEffectContext context) {
        EffectPoint centerPoint = new EffectPoint(this.center.x, this.center.y, this.center.z);
        double requestedRadius = this.settings.craterRadius(context.spec().scale());
        return new EffectCarveSpec(
            EffectBlockVolume.sphere(centerPoint, requestedRadius),
            Blocks.AIR.defaultBlockState(),
            true,
            this.settings.craterJournal(),
            EffectBulkBlockEditor.SyncPolicy.PROGRESSIVE,
            ContentBlackHoleCinematics.HORIZON_END,
            ContentBlackHoleCinematics.DESTABILISE,
            EffectBlockVolume.sphere(centerPoint, this.coreRadius),
            ContentBlackHoleCinematics.IGNITION,
            this.craterGroundY
        );
    }

    private void restoreCarve(GameplayEffectContext context) {
        CompoundTag state = context.persistentState();
        EffectCarveSpec spec = this.carveSpec(context);
        CompoundTag sessionTag = state.getCompound(CARVE_SESSION);
        if (!sessionTag.isEmpty()) {
            this.carveSession = EffectCarveSession.restore(context, spec, sessionTag);
            this.carvePlan = this.carveSession.plan();
            this.craterRadius = radiusOf(this.carvePlan.volume());
            this.frontRadius = this.carveSession.frontScale() * this.craterRadius;
            this.craterSyncReleased = this.carveSession.syncReleased();
            this.coreDoneReported = this.carveSession.coreComplete();
            this.incompleteReported = this.carveSession.incomplete();
            this.craterEndWarned = this.incompleteReported;
            return;
        }

        this.planDestruction(context);
        this.craterSyncReleased = state.getBoolean(CRATER_RELEASED);
        this.coreDoneReported = state.getBoolean(CORE_DONE);
        this.incompleteReported = state.getBoolean(CRATER_INCOMPLETE);
        this.legacyCarveComplete = this.craterSyncReleased && !this.incompleteReported;
        this.legacyCarveIncomplete = this.incompleteReported;
        this.craterEndWarned = this.incompleteReported;
        if (state.contains(CRATER_RADIUS)) {
            double storedRadius = Math.max(
                state.getDouble(CRATER_RADIUS), state.getDouble(CRATER_DEPTH)
            );
            if (storedRadius > 0.0) {
                this.craterRadius = storedRadius;
            }
        }
        if (!this.legacyCarveComplete
            && (state.contains(CRATER_EDIT) || state.contains(CORE_CURSOR))) {
            EffectCarveSession restored = EffectCarveSession.restore(
                context, spec, legacySessionTag(state)
            );
            if (Math.abs(radiusOf(restored.plan().volume()) - this.craterRadius) <= 1.0E-6) {
                this.carveSession = restored;
                this.carvePlan = restored.plan();
                this.craterRadius = radiusOf(this.carvePlan.volume());
                this.frontRadius = restored.frontScale() * this.craterRadius;
                this.craterSyncReleased = restored.syncReleased();
                this.coreDoneReported = restored.coreComplete();
                this.incompleteReported = restored.incomplete();
            } else {
                // The new plan no longer matches the old effective radius. Restarting the carve
                // is idempotent; keeping the old cursor would write outside the new volume.
                restored.close(false);
            }
        }
        if (this.legacyCarveComplete) {
            this.frontRadius = this.craterRadius;
        }
    }

    private static CompoundTag legacySessionTag(CompoundTag state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1);
        if (state.contains(CRATER_EDIT)) {
            tag.put("editor", state.getCompound(CRATER_EDIT));
        }
        if (state.contains(CORE_CURSOR)) {
            tag.put("core_cursor", state.getCompound(CORE_CURSOR));
        }
        tag.putBoolean("core_done", state.getBoolean(CORE_DONE));
        tag.putBoolean("sync_released", state.getBoolean(CRATER_RELEASED));
        tag.putBoolean("deadline_reached", state.getBoolean(CRATER_RELEASED));
        tag.putBoolean("incomplete", state.getBoolean(CRATER_INCOMPLETE));
        tag.putBoolean(
            "complete",
            state.getBoolean(CRATER_RELEASED) && !state.getBoolean(CRATER_INCOMPLETE)
        );
        tag.putFloat("front_scale", 0.0F);
        return tag;
    }

    private void putDestructionPlan(CompoundTag tag) {
        if (this.carvePlan == null) {
            return;
        }
        tag.putDouble(CRATER_RADIUS, this.craterRadius);
        tag.putDouble(CRATER_DEPTH, this.craterRadius);
        tag.putDouble(CRATER_GROUND_Y, this.craterGroundY);
        tag.putFloat(CRATER_START, this.carvePlan.growthStart());
        tag.putFloat(CRATER_WORK_END, this.carvePlan.workEnd());
        tag.putInt(CRATER_SOLID_SECTIONS, this.carvePlan.estimatedSolidSections());
        tag.putInt(CRATER_ESTIMATED_SECTIONS, this.carvePlan.estimatedSections());
        tag.putBoolean(CORE_DONE, this.coreDoneReported);
        tag.putBoolean(CRATER_RELEASED, this.craterSyncReleased);
        tag.putBoolean(CRATER_INCOMPLETE, this.incompleteReported || this.legacyCarveIncomplete);
    }

    private void warnIfCratersStayUnderground() {
        if (!this.hasCrater || this.carvePlan == null
            || breaksSurface(this.carvePlan.volume(), this.craterGroundY)) {
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
        this.coreDoneReported = state.getBoolean(CORE_DONE);
        this.craterSyncReleased = state.getBoolean(CRATER_RELEASED);
        this.incompleteReported = state.getBoolean(CRATER_INCOMPLETE);
        if (state.contains(CRATER_GROUND_Y)) {
            this.craterGroundY = state.getDouble(CRATER_GROUND_Y);
        }
    }

    private static double radiusOf(EffectBlockVolume volume) {
        EffectBounds bounds = volume.bounds();
        return Math.max(
            bounds.maxX() - bounds.minX(),
            Math.max(bounds.maxY() - bounds.minY(), bounds.maxZ() - bounds.minZ())
        ) * 0.5;
    }

    private static boolean intersectsTerrain(
        EffectBlockVolume volume,
        double groundY,
        GameplayEffectContext context
    ) {
        EffectBounds bounds = volume.bounds();
        return bounds.maxY() >= context.level().getMinBuildHeight() + 1
            && bounds.minY() <= groundY;
    }

    private static boolean breaksSurface(EffectBlockVolume volume, double groundY) {
        EffectBounds bounds = volume.bounds();
        return bounds.minY() <= groundY && bounds.maxY() >= groundY;
    }

    private static Vec3 diskAxis(float yaw, float pitch, float roll) {
        Vector3f normal = EffectSpace.Basis.fromEuler(yaw, pitch, roll).up();
        return new Vec3(normal.x, normal.y, normal.z).normalize();
    }
}
