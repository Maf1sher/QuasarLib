package mafisher.quasar.client.effect;

import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioCue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;

/**
 * Client sound instance with explicit distance and directional gain. The vanilla linear
 * attenuation is disabled so the cue's own attenuation model is the only gain applied.
 */
final class EffectSoundInstance extends AbstractSoundInstance {
    private final EffectAudioCue cue;

    EffectSoundInstance(EffectAudioCue cue) {
        super(cue.soundId(), cue.source(), SoundInstance.createUnseededRandom());
        this.cue = cue;
        this.volume = cue.volume();
        this.pitch = cue.pitch();
        this.x = cue.position().x;
        this.y = cue.position().y;
        this.z = cue.position().z;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public float getVolume() {
        float base = this.sound == null ? this.volume : super.getVolume();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gameRenderer == null) {
            return base;
        }

        var camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null) {
            return base;
        }
        Vec3 listener = camera.getPosition();
        double dx = this.x - listener.x;
        double dy = this.y - listener.y;
        double dz = this.z - listener.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float gain = 1.0F;
        if (this.cue.attenuation() instanceof EffectAudioAttenuation.Distance distanceAttenuation) {
            gain = EffectAudioGain.distanceGain(distance, distanceAttenuation.maxDistance());
        } else if (this.cue.attenuation() instanceof EffectAudioAttenuation.Directional directional) {
            float distanceGain = EffectAudioGain.distanceGain(distance, directional.maxDistance());
            double angle = directionAngleDegrees(dx, dy, dz, directional.direction());
            float directionGain = EffectAudioGain.directionGain(
                angle,
                directional.innerAngleDegrees(),
                directional.outerAngleDegrees(),
                directional.outsideGain()
            );
            gain = EffectAudioGain.combined(distanceGain, directionGain);
        }
        return Math.min(1.0F, base * gain);
    }

    @Override
    public SoundInstance.Attenuation getAttenuation() {
        return SoundInstance.Attenuation.NONE;
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
