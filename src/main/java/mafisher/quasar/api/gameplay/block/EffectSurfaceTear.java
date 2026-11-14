package mafisher.quasar.api.gameplay.block;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mafisher.quasar.api.block.BlockChange;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.debris.EffectDebrisAttractor;
import mafisher.quasar.api.effect.debris.EffectDebrisSpawn;
import mafisher.quasar.api.effect.debris.EffectDebrisSpec;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.world.EffectBlockHit;
import mafisher.quasar.api.gameplay.world.EffectBlockSample;
import mafisher.quasar.api.gameplay.world.EffectBlockVolume;
import mafisher.quasar.api.gameplay.world.EffectRaycastResult;
import mafisher.quasar.api.gameplay.world.EffectRaycastStatus;
import mafisher.quasar.api.gameplay.world.EffectWorldQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Budgeted surface tearing along the leading edge of a destructive volume.
 *
 * <p>One tick casts a deterministic, stratified batch of rays from a growing ring toward the
 * centre. A hit is expanded into one bounded cluster of block edits, and only the block actually
 * struck by the ray feeds the supplied {@link EffectWriteFeedback}. Cluster neighbours are silent
 * cleanup; this keeps debris and secondary effects tied to the visible tear instead of every
 * removed block.</p>
 *
 * <p>The tear never loads or generates a chunk. Unloaded columns and exhausted world samples are
 * soft failures: the ray loop stops instead of firing until the budget is gone. Edits share the
 * structural rules of {@link EffectBulkBlockEditor}: the authored volume, the protected bottom
 * layer, air/replacement, and the indestructible block set. Protection, journaling, light, and
 * client resends stay with the engine write path.</p>
 */
@SuppressWarnings({"PMD.GodClass", "PMD.TooManyMethods", "checkstyle:ParameterNumber"})
public final class EffectSurfaceTear {
    private static final int MAX_RAYS_PER_TICK = 512;
    private static final int MAX_CLUSTER_RADIUS = 3;
    private static final int MAX_PENDING_HEADROOM = 12_000;
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long LONGITUDE_SALT = 0xD1B54A32D192ED03L;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final int MAX_DEBRIS_BURSTS = 16;
    private static final Predicate<BlockState> SOLID_SURFACE =
        state -> !state.isAir() && state.getFluidState().isEmpty();

    private final Settings settings;
    private final BlockPos[] clusterOffsets;

    public EffectSurfaceTear(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clusterOffsets = clusterOffsets(settings.clusterRadius());
    }

    /**
     * Casts at most one ray batch for this server tick.
     *
     * @return per-tick counters; {@link TickResult#stoppedBy()} is non-null when a ray budget or
     *     unloaded chunk ended the batch early
     */
    public TickResult tick(
        GameplayEffectContext context,
        Geometry geometry,
        EffectWriteFeedback feedback
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(feedback, "feedback");
        if (geometry.frontRadius() <= 0.0 || this.settings.raysPerTick() <= 0) {
            return TickResult.EMPTY;
        }
        double ringRadius = ringRadius(
            geometry.frontRadius(), geometry.craterRadius(), this.settings.band()
        );
        if (ringRadius <= 0.0) {
            return TickResult.EMPTY;
        }
        int rays = rayBudget(context, ringRadius);
        if (rays <= 0) {
            return TickResult.EMPTY;
        }
        double innerRadius = Math.max(0.0, geometry.frontRadius() - this.settings.band());
        return castRays(
            context, geometry, feedback, ringRadius, innerRadius * innerRadius, rays
        );
    }

    private int rayBudget(GameplayEffectContext context, double ringRadius) {
        int headroom = (int)Math.max(
            0, MAX_PENDING_HEADROOM - context.pendingBlockChanges()
        );
        return Math.min(
            rayCount(ringRadius, this.settings.raysPerTick(), this.settings.raySpacing()),
            headroom / this.clusterOffsets.length
        );
    }

    private TickResult castRays(
        GameplayEffectContext context,
        Geometry geometry,
        EffectWriteFeedback feedback,
        double ringRadius,
        double innerSquared,
        int rays
    ) {
        long seed = context.spec().seed();
        int ageTicks = context.ageTicks();
        int cast = 0;
        int hits = 0;
        int queued = 0;
        int rejected = 0;
        EffectRaycastStatus stoppedBy = null;
        for (int index = 0; index < rays; index++) {
            Vec3 direction = direction(seed, ageTicks, index, rays);
            Vec3 from = geometry.center().add(direction.scale(ringRadius));
            EffectRaycastResult result = context.world().raycast(
                from, geometry.center(), SOLID_SURFACE
            );
            cast++;
            if (isSoftStop(result.status())) {
                stoppedBy = result.status();
                break;
            }
            if (!result.isHit()) {
                continue;
            }
            hits++;
            HitOutcome outcome = handleHit(
                context, geometry, result.hit().orElseThrow(), innerSquared, feedback
            );
            queued += outcome.queued();
            rejected += outcome.rejected();
            if (outcome.stop()) {
                break;
            }
        }
        return new TickResult(cast, hits, queued, rejected, stoppedBy);
    }

    private HitOutcome handleHit(
        GameplayEffectContext context,
        Geometry geometry,
        EffectBlockHit hit,
        double innerSquared,
        EffectWriteFeedback feedback
    ) {
        if (!BlockEditRules.insideVolume(geometry.volume(), hit.position())
            || distanceSquared(hit.position(), geometry.center()) < innerSquared) {
            return new HitOutcome(0, 1, false);
        }
        ClusterResult cluster = queueCluster(context, geometry, hit, feedback);
        return new HitOutcome(cluster.queued(), cluster.rejected(), cluster.queueFull());
    }

    private static boolean isSoftStop(EffectRaycastStatus status) {
        return status == EffectRaycastStatus.BUDGET_EXHAUSTED
            || status == EffectRaycastStatus.UNLOADED
            || status == EffectRaycastStatus.TOO_LONG;
    }

    /**
     * Built-in infall debris. Every successful hit write emits at most one burst; the per-tick
     * burst cap, world lifetime, sample lifetime, and scale all clamp before the payload is sent.
     * Use one feedback instance per session.
     */
    public static EffectWriteFeedback debrisFeedback(
        GameplayEffectContext context,
        Debris settings
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(settings, "settings");
        return new SurfaceTearDebrisFeedback(context, settings);
    }

    private ClusterResult queueCluster(
        GameplayEffectContext context,
        Geometry geometry,
        EffectBlockHit hit,
        EffectWriteFeedback feedback
    ) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int queued = 0;
        int rejected = 0;
        for (BlockPos offset : this.clusterOffsets) {
            BlockPos position = hit.position().offset(offset);
            boolean primary = offset.getX() == 0
                && offset.getY() == 0
                && offset.getZ() == 0;
            BlockState current;
            if (primary) {
                current = hit.state();
            } else {
                Optional<BlockState> sampled = context.world().blockState(position);
                if (sampled.isEmpty()) {
                    rejected++;
                    continue;
                }
                current = sampled.get();
            }
            if (!BlockEditRules.canEdit(
                context.level(), geometry.volume(), position, current, air
            )) {
                rejected++;
                continue;
            }
            Consumer<EffectBlockSample> applied = primary ? feedback::onWritten : null;
            if (!context.queueBlockChange(
                new BlockChange(position, air), applied, BlockJournalMode.SECTION
            )) {
                rejected++;
                return new ClusterResult(queued, rejected, true);
            }
            queued++;
        }
        return new ClusterResult(queued, rejected, false);
    }

    /** Ring radius at the leading edge, clamped to the final crater. */
    static double ringRadius(double frontRadius, double craterRadius, double band) {
        return Math.min(craterRadius, Math.max(0.0, frontRadius + band));
    }

    /**
     * Equal-area latitude strata with a golden-angle longitude. The per-tick phase rotates the
     * whole set, so successive ticks cover the sphere without changing a resumed tick's order.
     */
    static Vec3 direction(long seed, int ageTicks, int index, int rays) {
        if (rays < 1) {
            throw new IllegalArgumentException("Ray count must be positive");
        }
        if (index < 0 || index >= rays) {
            throw new IllegalArgumentException("Ray index is outside the batch");
        }
        if (ageTicks < 0) {
            throw new IllegalArgumentException("Effect age cannot be negative");
        }
        long mixed = mix(seed + GOLDEN_GAMMA * (ageTicks + 1L));
        double latitudePhase = unitDouble(mixed);
        double longitudePhase = unitDouble(mix(mixed ^ LONGITUDE_SALT));
        double band = (index + 0.5) / rays + latitudePhase;
        band -= Math.floor(band);
        double y = 1.0 - 2.0 * band;
        double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        double angle = GOLDEN_ANGLE * (index + longitudePhase * rays);
        return new Vec3(ring * Math.cos(angle), y, ring * Math.sin(angle));
    }

    /** Distance-paced infall lifetime, clamped to the authored window. */
    static int fallLifetimeTicks(
        double distance,
        double fallSpeed,
        int minimumTicks,
        int maximumTicks
    ) {
        if (!Double.isFinite(distance) || distance <= 0.0
            || !Double.isFinite(fallSpeed) || fallSpeed <= 0.0) {
            return minimumTicks;
        }
        double raw = Math.ceil(distance / fallSpeed);
        if (!Double.isFinite(raw) || raw >= maximumTicks) {
            return maximumTicks;
        }
        return Math.max(minimumTicks, (int)raw);
    }

    private static int rayCount(double ringRadius, int maximum, double spacing) {
        if (maximum <= 0) {
            return 0;
        }
        if (spacing <= 0.0) {
            return maximum;
        }
        double wanted = Math.ceil(2.0 * Math.PI * ringRadius / spacing);
        return (int)Math.max(1.0, Math.min(maximum, wanted));
    }

    private static double distanceSquared(BlockPos position, Vec3 center) {
        double dx = position.getX() + 0.5 - center.x;
        double dy = position.getY() + 0.5 - center.y;
        double dz = position.getZ() + 0.5 - center.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static BlockPos[] clusterOffsets(int radius) {
        int side = radius * 2 + 1;
        BlockPos[] offsets = new BlockPos[side * side * side];
        int index = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    offsets[index++] = new BlockPos(x, y, z);
                }
            }
        }
        return offsets;
    }

    private static long mix(long value) {
        long mixed = value;
        mixed = (mixed ^ mixed >>> 30) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ mixed >>> 27) * 0x94D049BB133111EBL;
        return mixed ^ mixed >>> 31;
    }

    private static double unitDouble(long value) {
        return (value >>> 11) / (double)(1L << 53);
    }

    /** Authored tear tuning. */
    public record Settings(
        double band,
        int raysPerTick,
        double raySpacing,
        int clusterRadius
    ) {
        public Settings {
            if (!Double.isFinite(band) || band < 0.0) {
                throw new IllegalArgumentException("Tear band must be finite and non-negative");
            }
            if (raysPerTick < 0 || raysPerTick > MAX_RAYS_PER_TICK) {
                throw new IllegalArgumentException(
                    "Tear rays per tick must be between 0 and " + MAX_RAYS_PER_TICK
                );
            }
            if (!Double.isFinite(raySpacing) || raySpacing < 0.0) {
                throw new IllegalArgumentException(
                    "Tear ray spacing must be finite and non-negative"
                );
            }
            if (clusterRadius < 0 || clusterRadius > MAX_CLUSTER_RADIUS) {
                throw new IllegalArgumentException(
                    "Tear cluster radius must be between 0 and " + MAX_CLUSTER_RADIUS
                );
            }
        }
    }

    /** One moving tear ring inside the authored volume. */
    public record Geometry(
        EffectBlockVolume volume,
        Vec3 center,
        double frontRadius,
        double craterRadius
    ) {
        public Geometry {
            Objects.requireNonNull(volume, "volume");
            Objects.requireNonNull(center, "center");
            requireFinite(center, "Tear center");
            if (!Double.isFinite(frontRadius) || frontRadius < 0.0) {
                throw new IllegalArgumentException(
                    "Tear front radius must be finite and non-negative"
                );
            }
            if (!Double.isFinite(craterRadius) || craterRadius < 0.0
                || craterRadius > EffectWorldQuery.MAX_RAYCAST_DISTANCE) {
                throw new IllegalArgumentException(
                    "Tear crater radius must be finite and between 0 and "
                        + EffectWorldQuery.MAX_RAYCAST_DISTANCE
                );
            }
            frontRadius = Math.min(frontRadius, craterRadius);
        }
    }

    /** What one tick cast and queued. A non-null {@code stoppedBy} means a soft budget stop. */
    public record TickResult(
        int raysCast,
        int hits,
        int queuedBlocks,
        int rejectedBlocks,
        @Nullable EffectRaycastStatus stoppedBy
    ) {
        public static final TickResult EMPTY = new TickResult(0, 0, 0, 0, null);

        public TickResult {
            if (raysCast < 0 || hits < 0 || hits > raysCast
                || queuedBlocks < 0 || rejectedBlocks < 0) {
                throw new IllegalArgumentException("Tear tick counters are inconsistent");
            }
        }
    }

    /**
     * Built-in debris tuning. {@code stopProgress} caps every lifetime so a burst never outlives
     * the phase that owns it; {@code maxBurstsPerTick} is applied before the payload is built.
     */
    public record Debris(
        EffectAudience audience,
        EffectDebrisAttractor attractor,
        @Nullable EffectDebrisSpawn spawn,
        int pieces,
        double fallSpeed,
        int minLifetimeTicks,
        int maxLifetimeTicks,
        float scale,
        int maxBurstsPerTick,
        float stopProgress
    ) {
        public Debris {
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(attractor, "attractor");
            spawn = spawn == null ? new EffectDebrisSpawn.Point() : spawn;
            requireDebrisPieces(pieces);
            requireFallSpeed(fallSpeed);
            requireDebrisLifetime(minLifetimeTicks, maxLifetimeTicks);
            requireDebrisScale(scale);
            requireDebrisBursts(maxBurstsPerTick);
            requireStopProgress(stopProgress);
        }
    }

    private static void requireDebrisPieces(int pieces) {
        if (pieces < 1 || pieces > EffectDebrisSpec.MAX_PIECES) {
            throw new IllegalArgumentException(
                "Debris pieces must be between 1 and " + EffectDebrisSpec.MAX_PIECES
            );
        }
    }

    private static void requireFallSpeed(double fallSpeed) {
        if (!Double.isFinite(fallSpeed) || fallSpeed <= 0.0) {
            throw new IllegalArgumentException("Debris fall speed must be finite and positive");
        }
    }

    private static void requireDebrisLifetime(int minimumTicks, int maximumTicks) {
        if (minimumTicks < 1 || minimumTicks > EffectDebrisSpec.MAX_LIFETIME_TICKS
            || maximumTicks < minimumTicks
            || maximumTicks > EffectDebrisSpec.MAX_LIFETIME_TICKS) {
            throw new IllegalArgumentException(
                "Debris lifetime must be ordered inside 1 and "
                    + EffectDebrisSpec.MAX_LIFETIME_TICKS
            );
        }
    }

    private static void requireDebrisScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0.0F || scale > EffectDebrisSpec.MAX_SCALE) {
            throw new IllegalArgumentException(
                "Debris scale must be greater than 0 and at most "
                    + EffectDebrisSpec.MAX_SCALE
            );
        }
    }

    private static void requireDebrisBursts(int burstsPerTick) {
        if (burstsPerTick < 0 || burstsPerTick > MAX_DEBRIS_BURSTS) {
            throw new IllegalArgumentException(
                "Debris bursts per tick must be between 0 and " + MAX_DEBRIS_BURSTS
            );
        }
    }

    private static void requireStopProgress(float stopProgress) {
        if (!Float.isFinite(stopProgress) || stopProgress < 0.0F || stopProgress > 1.0F) {
            throw new IllegalArgumentException("Debris stop progress must be inside [0, 1]");
        }
    }

    private record ClusterResult(int queued, int rejected, boolean queueFull) {
    }

    private record HitOutcome(int queued, int rejected, boolean stop) {
    }

    private static void requireFinite(Vec3 value, String name) {
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
