package mafisher.quasar.client.effect.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mafisher.quasar.api.client.particle.EffectParticleField;
import mafisher.quasar.api.client.particle.EffectParticlePass;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.effect.EffectSpace;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import org.joml.Matrix4f;

/**
 * Collects stateless particle fields across visible effects, applies the quality tier
 * and the global count budget, and produces one GPU draw per non-empty field.
 *
 * <p>The builder never iterates particles. Work is proportional to the number of
 * fields, not to the requested particle count.</p>
 */
@SuppressWarnings("PMD.TooManyMethods")
final class ParticleBatchBuilder {
    private static final Comparator<PendingDraw> BY_PRIORITY = Comparator
        .comparingDouble(PendingDraw::priority).reversed()
        .thenComparingInt(PendingDraw::order);
    private static final Comparator<PendingDraw> BACK_TO_FRONT = Comparator
        .comparingDouble(PendingDraw::cameraDistanceSquared).reversed()
        .thenComparingInt(PendingDraw::order);

    private final List<PendingDraw> pending = new ArrayList<>();
    private final List<Draw> allocated = new ArrayList<>();
    private final Set<String> passIds = new HashSet<>();
    private final Matrix4f rotation = new Matrix4f();
    private final Matrix4f localScale = new Matrix4f();
    private final Matrix4f model = new Matrix4f();
    private int budget;
    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private int nextOrder;

    /** Clears the previous frame and starts a new budget and camera. */
    void reset(int budget, double cameraX, double cameraY, double cameraZ) {
        if (budget < 0) {
            throw new IllegalArgumentException("Particle budget must not be negative");
        }
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ)) {
            throw new IllegalArgumentException("Particle camera position must be finite");
        }
        this.budget = budget;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.pending.clear();
        this.allocated.clear();
        this.nextOrder = 0;
    }

    /** Adds one context's particle passes after validating pass IDs within that context. */
    void add(EffectRenderContext context, List<EffectParticlePass> passes) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(passes, "passes");
        this.passIds.clear();
        float priority = (float)context.screenMetrics().score();
        for (EffectParticlePass pass : passes) {
            Objects.requireNonNull(pass, "pass");
            if (!this.passIds.add(pass.id())) {
                throw new IllegalStateException("Duplicate particle pass ID '" + pass.id()
                    + "' for effect " + context.effect().effectId());
            }
            EffectParticleField field = pass.field();
            this.pending.add(new PendingDraw(
                pass.id(),
                field,
                modelMatrix(context),
                context.seed() ^ field.salt(),
                scaledCount(field.maxParticles(), context.effectiveQuality()),
                context.ageTicks(),
                (float)context.scale(),
                priority,
                distanceSquared(context),
                this.nextOrder++
            ));
        }
    }

    /** Returns one draw per non-empty field, allocated against the global budget. */
    List<Draw> draws() {
        this.allocated.clear();
        if (this.pending.isEmpty() || this.budget <= 0) {
            return this.allocated;
        }
        this.pending.sort(BY_PRIORITY);
        this.allocateCounts();
        this.pending.sort(BACK_TO_FRONT);
        for (PendingDraw draw : this.pending) {
            if (draw.count() > 0) {
                this.allocated.add(new Draw(
                    draw.id(), draw.field(), draw.model(), draw.seed(), draw.count(),
                    draw.ageTicks(), draw.scale()
                ));
            }
        }
        return this.allocated;
    }

    boolean isEmpty() {
        return this.pending.isEmpty();
    }

    /** Scale applied to a requested count by the per-effect quality tier. */
    static int scaledCount(int requested, EffectQualityTier tier) {
        Objects.requireNonNull(tier, "tier");
        double factor = switch (tier) {
            case LOW -> 0.25;
            case MEDIUM -> 0.5;
            case HIGH, ULTRA -> 1.0;
        };
        int scaled = (int)Math.floor(requested * factor);
        return requested == 0 ? 0 : Math.max(1, Math.min(requested, scaled));
    }

    private void allocateCounts() {
        long total = 0L;
        for (PendingDraw draw : this.pending) {
            total += draw.requested();
        }
        if (total <= this.budget) {
            for (PendingDraw draw : this.pending) {
                draw.setCount(draw.requested());
            }
            return;
        }
        int remaining = this.applyFloors(this.budget);
        if (remaining > 0) {
            remaining = this.applyProportional(remaining);
        }
        if (remaining > 0) {
            this.distributeLeftover();
        }
    }

    private int applyFloors(int budget) {
        int remaining = budget;
        for (PendingDraw draw : this.pending) {
            int floor = Math.max(1, draw.requested() / 8);
            int count = Math.min(floor, remaining);
            draw.setCount(count);
            remaining -= count;
        }
        return remaining;
    }

    private int applyProportional(int remaining) {
        long weight = 0L;
        for (PendingDraw draw : this.pending) {
            weight += Math.max(0, draw.requested() - draw.count());
        }
        if (weight == 0L) {
            return remaining;
        }
        int used = 0;
        for (PendingDraw draw : this.pending) {
            int capacity = draw.requested() - draw.count();
            if (capacity > 0) {
                int share = (int)Math.min(capacity, (long)remaining * capacity / weight);
                draw.setCount(draw.count() + share);
                used += share;
            }
        }
        return remaining - used;
    }

    private void distributeLeftover() {
        int leftover = this.budget;
        for (PendingDraw draw : this.pending) {
            leftover -= draw.count();
        }
        for (PendingDraw draw : this.pending) {
            if (leftover <= 0) {
                return;
            }
            if (draw.count() < draw.requested()) {
                draw.setCount(draw.count() + 1);
                leftover--;
            }
        }
    }

    private Matrix4f modelMatrix(EffectRenderContext context) {
        EffectSpace.Basis.rotationInto(
            context.transform().yaw(), context.transform().pitch(), context.transform().roll(),
            this.rotation
        );
        this.localScale.identity().scale((float)context.scale());
        this.model.identity()
            .translate(
                (float)(context.transform().position().x() - this.cameraX),
                (float)(context.transform().position().y() - this.cameraY),
                (float)(context.transform().position().z() - this.cameraZ)
            )
            .mul(this.rotation)
            .mul(this.localScale);
        return new Matrix4f(this.model);
    }

    private double distanceSquared(EffectRenderContext context) {
        return context.transform().position().distanceToSqr(this.cameraX, this.cameraY, this.cameraZ);
    }

    /** One field after tier scaling, before budget allocation. */
    static final class PendingDraw {
        private final String id;
        private final EffectParticleField field;
        private final Matrix4f model;
        private final long seed;
        private final int requested;
        private final float ageTicks;
        private final float scale;
        private final float priority;
        private final double cameraDistanceSquared;
        private final int order;
        private int count;

        @SuppressWarnings("checkstyle:ParameterNumber")
        private PendingDraw(String id, EffectParticleField field, Matrix4f model, long seed,
            int requested, float ageTicks, float scale, float priority,
            double cameraDistanceSquared, int order) {
            this.id = id;
            this.field = field;
            this.model = model;
            this.seed = seed;
            this.requested = requested;
            this.ageTicks = ageTicks;
            this.scale = scale;
            this.priority = priority;
            this.cameraDistanceSquared = cameraDistanceSquared;
            this.order = order;
        }

        String id() {
            return this.id;
        }

        EffectParticleField field() {
            return this.field;
        }

        Matrix4f model() {
            return this.model;
        }

        long seed() {
            return this.seed;
        }

        int requested() {
            return this.requested;
        }

        float ageTicks() {
            return this.ageTicks;
        }

        float scale() {
            return this.scale;
        }

        float priority() {
            return this.priority;
        }

        double cameraDistanceSquared() {
            return this.cameraDistanceSquared;
        }

        int order() {
            return this.order;
        }

        int count() {
            return this.count;
        }

        void setCount(int count) {
            if (count < 0 || count > this.requested) {
                throw new IllegalArgumentException("Particle count must stay within the request");
            }
            this.count = count;
        }
    }

    /** One allocated draw for the renderer. */
    record Draw(String id, EffectParticleField field, Matrix4f model, long seed, int count,
        float ageTicks, float scale) {
        Draw {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(model, "model");
            if (count < 1) {
                throw new IllegalArgumentException("A particle draw needs at least one particle");
            }
            if (!Float.isFinite(ageTicks) || ageTicks < 0.0F
                || !Float.isFinite(scale) || scale <= 0.0F) {
                throw new IllegalArgumentException("Particle draw age and scale must be valid");
            }
            model = new Matrix4f(model);
        }
    }

}
