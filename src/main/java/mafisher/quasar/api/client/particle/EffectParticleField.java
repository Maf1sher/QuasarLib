package mafisher.quasar.api.client.particle;

import java.util.Objects;

/**
 * Declarative, stateless micro-particle field.
 *
 * <p>A field is drawn by {@link ParticleVisualEffectRenderer} as one GPU-instanced pass.
 * Every particle state is derived on the GPU from the effect seed, the effect age in
 * ticks, and the particle instance index; the engine never stores or updates a particle
 * on the CPU. The requested count is a ceiling: quality tiers and the global client
 * particle budget may draw fewer particles without rejecting the field.</p>
 *
 * <p>Coordinates and sizes are local to the effect transform. The field follows the
 * effect, including attachments. Use {@code salt} to decorrelate several fields that
 * belong to the same effect instance.</p>
 */
public final class EffectParticleField {
    /** Maximum requested particles for one field. */
    public static final int MAX_PARTICLES = 65_536;
    /** Maximum particle lifetime in ticks. */
    public static final int MAX_LIFETIME_TICKS = 72_000;

    private final int maxParticles;
    private final int lifetimeTicks;
    private final Mode mode;
    private final EffectParticleShape shape;
    private final EffectParticleMotion motion;
    private final EffectParticleAppearance appearance;
    private final long salt;

    private EffectParticleField(Builder builder) {
        this.maxParticles = builder.maxParticles;
        this.lifetimeTicks = builder.lifetimeTicks;
        this.mode = builder.mode;
        this.shape = builder.shape;
        this.motion = builder.motion;
        this.appearance = Objects.requireNonNull(builder.appearance, "appearance");
        this.salt = builder.salt;
    }

    /** Starts a field configuration. */
    public static Builder builder() {
        return new Builder();
    }

    /** Requested particle ceiling; the engine may draw fewer. */
    public int maxParticles() {
        return this.maxParticles;
    }

    /** Particle lifetime in ticks. */
    public int lifetimeTicks() {
        return this.lifetimeTicks;
    }

    /** Lifecycle mode. */
    public Mode mode() {
        return this.mode;
    }

    /** Local spawn shape. */
    public EffectParticleShape shape() {
        return this.shape;
    }

    /** Local motion model. */
    public EffectParticleMotion motion() {
        return this.motion;
    }

    /** Texture, size, color, and softness. */
    public EffectParticleAppearance appearance() {
        return this.appearance;
    }

    /** Value mixed into the effect seed to decorrelate fields. */
    public long salt() {
        return this.salt;
    }

    /**
     * Conservative local radius that contains every particle at every age, including
     * spawn shape, maximum displacement, and billboard size. Culling helpers should
     * multiply this by the effect scale.
     */
    public double spatialRadius() {
        return this.shape.extent()
            + this.motion.maxDisplacement(this.lifetimeTicks)
            + this.appearance.maxSize();
    }

    /** Particle lifecycle mode. */
    public enum Mode {
        /** Each particle starts at a random phase, so the field is full at age zero. */
        STEADY,
        /** Every particle starts at age zero and disappears after one lifetime. */
        BURST
    }

    /** Mutable builder for {@link EffectParticleField}. */
    public static final class Builder {
        private int maxParticles = 256;
        private int lifetimeTicks = 40;
        private Mode mode = Mode.STEADY;
        private EffectParticleShape shape = new EffectParticleShape.Point();
        private EffectParticleMotion motion = EffectParticleMotion.stationary();
        private EffectParticleAppearance appearance;
        private long salt;

        private Builder() {
        }

        /** Sets the requested particle ceiling. */
        public Builder maxParticles(int maxParticles) {
            if (maxParticles < 1 || maxParticles > MAX_PARTICLES) {
                throw new IllegalArgumentException("Particle count must be between 1 and "
                    + MAX_PARTICLES);
            }
            this.maxParticles = maxParticles;
            return this;
        }

        /** Sets the particle lifetime in ticks. */
        public Builder lifetimeTicks(int lifetimeTicks) {
            if (lifetimeTicks < 1 || lifetimeTicks > MAX_LIFETIME_TICKS) {
                throw new IllegalArgumentException("Particle lifetime must be between 1 and "
                    + MAX_LIFETIME_TICKS);
            }
            this.lifetimeTicks = lifetimeTicks;
            return this;
        }

        /** Sets the lifecycle mode. */
        public Builder mode(Mode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /** Sets the local spawn shape. */
        public Builder shape(EffectParticleShape shape) {
            this.shape = Objects.requireNonNull(shape, "shape");
            return this;
        }

        /** Sets the local motion model. */
        public Builder motion(EffectParticleMotion motion) {
            this.motion = Objects.requireNonNull(motion, "motion");
            return this;
        }

        /** Sets the appearance. Required before {@link #build()}. */
        public Builder appearance(EffectParticleAppearance appearance) {
            this.appearance = Objects.requireNonNull(appearance, "appearance");
            return this;
        }

        /** Sets the field salt mixed into the effect seed. */
        public Builder salt(long salt) {
            this.salt = salt;
            return this;
        }

        /** Builds the immutable field. */
        public EffectParticleField build() {
            if (this.appearance == null) {
                throw new IllegalStateException("A particle field needs an appearance");
            }
            return new EffectParticleField(this);
        }
    }
}
