package mafisher.quasar.client.effect;

import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioCue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;

/**
 * Client sound instance with explicit distance and directional gain. The vanilla linear
 * attenuation is disabled for custom attenuation models so the cue's own model is the only gain
 * applied. Looping variants opt into vanilla attenuation when the cue asks for it.
 */
class EffectSoundInstance extends AbstractSoundInstance {
    protected final EffectAudioCue cue;
    private float gain = 1.0F;

    EffectSoundInstance(EffectAudioCue cue) {
        this(cue, false);
    }

    EffectSoundInstance(EffectAudioCue cue, boolean vanillaAttenuation) {
        super(cue.soundId(), cue.source(), SoundInstance.createUnseededRandom());
        this.cue = cue;
        this.volume = cue.volume();
        this.pitch = cue.pitch();
        this.x = cue.position().x;
        this.y = cue.position().y;
        this.z = cue.position().z;
        this.attenuation = vanillaAttenuation
            ? SoundInstance.Attenuation.LINEAR
            : SoundInstance.Attenuation.NONE;
    }

    /** Runtime gain multiplier supplied by the loop envelope; one-shot sounds leave it at one. */
    void setGain(float gain) {
        this.gain = Math.max(0.0F, Math.min(1.0F, gain));
    }

    /** Moves a looping cue without recreating its sound instance. */
    void setPosition(Vec3 position) {
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
    }

    @Override
    public float getVolume() {
        float base = this.sound == null ? this.volume : super.getVolume();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gameRenderer == null) {
            return base * this.gain;
        }

        var camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null) {
            return base * this.gain;
        }
        Vec3 listener = camera.getPosition();
        double dx = this.x - listener.x;
        double dy = this.y - listener.y;
        double dz = this.z - listener.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float attenuationGain = 1.0F;
        if (this.cue.attenuation() instanceof EffectAudioAttenuation.Distance distanceAttenuation) {
            attenuationGain = EffectAudioGain.distanceGain(distance, distanceAttenuation.maxDistance());
        } else if (this.cue.attenuation() instanceof EffectAudioAttenuation.Directional directional) {
            float distanceGain = EffectAudioGain.distanceGain(distance, directional.maxDistance());
            double angle = directionAngleDegrees(dx, dy, dz, directional.direction());
            float directionGain = EffectAudioGain.directionGain(
                angle,
                directional.innerAngleDegrees(),
                directional.outerAngleDegrees(),
                directional.outsideGain()
            );
            attenuationGain = EffectAudioGain.combined(distanceGain, directionGain);
        }
        return Math.min(1.0F, base * this.gain * attenuationGain);
    }

    @Override
    public SoundInstance.Attenuation getAttenuation() {
        return this.attenuation;
    }

    @Override
    public boolean isRelative() {
        return false;
    }

    private static double directionAngleDegrees(double dx, double dy, double dz, Vec3 direction) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 1.0E-9) {
            return 0.0;
        }
        double dot = (dx * direction.x + dy * direction.y + dz * direction.z) / length;
        double clamped = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(Math.acos(clamped));
    }
}
