package mafisher.quasar.api.gameplay.block;

import mafisher.quasar.api.effect.debris.EffectDebrisSpec;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.world.EffectBlockSample;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Built-in infall debris feedback for {@link EffectSurfaceTear}. Kept package-private so the
 * consumer-facing contract stays two types: the tear and the feedback callback.
 */
final class SurfaceTearDebrisFeedback implements EffectWriteFeedback {
    private final GameplayEffectContext context;
    private final EffectSurfaceTear.Debris settings;
    private int lastAge = Integer.MIN_VALUE;
    private int bursts;

    SurfaceTearDebrisFeedback(GameplayEffectContext context, EffectSurfaceTear.Debris settings) {
        this.context = context;
        this.settings = settings;
    }

    @Override
    public void onWritten(EffectBlockSample sample) {
        if (this.settings.maxBurstsPerTick() <= 0) {
            return;
        }
        int age = this.context.ageTicks();
        if (age != this.lastAge) {
            this.lastAge = age;
            this.bursts = 0;
        }
        if (this.bursts >= this.settings.maxBurstsPerTick()) {
            return;
        }
        BlockState state = sample.state();
        if (state.isAir()) {
            return;
        }
        int remaining = remainingTicks(age);
        if (remaining < this.settings.minLifetimeTicks()) {
            return;
        }
        Vec3 origin = Vec3.atCenterOf(sample.position());
        double distance = origin.distanceTo(this.settings.attractor().center());
        int lifetime = Math.min(
            EffectSurfaceTear.fallLifetimeTicks(
                distance,
                this.settings.fallSpeed(),
                this.settings.minLifetimeTicks(),
                this.settings.maxLifetimeTicks()
            ),
            remaining
        );
        if (lifetime < this.settings.minLifetimeTicks()) {
            return;
        }
        int sent = this.context.spawnDebris(EffectDebrisSpec.infallField(
            origin,
            state,
            this.settings.pieces(),
            0.0,
            0.0,
            lifetime,
            this.settings.audience(),
            this.settings.attractor(),
            this.settings.spawn(),
            this.settings.scale()
        ));
        if (sent > 0) {
            this.bursts++;
        }
    }

    private int remainingTicks(int age) {
        int duration = Math.max(1, this.context.spec().phases().totalDurationTicks());
        int stopTick = (int)Math.max(
            1L,
            Math.min(duration, Math.round(this.settings.stopProgress() * duration))
        );
        return stopTick - age;
    }
}
