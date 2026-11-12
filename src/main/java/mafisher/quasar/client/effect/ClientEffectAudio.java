package mafisher.quasar.client.effect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioMotion;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.audio.EffectAudioTrigger;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.effect.network.EffectAudioPlanPayload;
import mafisher.quasar.effect.network.EffectAudioTriggerPayload;
import mafisher.quasar.effect.network.StopEffectAudioPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;

/** Client-side scheduler for effect sound plans; cue timing follows a server-relative timeline, not packet arrival. */
@ApiStatus.Internal
public final class ClientEffectAudio {
    private static final int MAX_ACTIVE_PLANS = 128;
    private static final int MAX_ACTIVE_LOOPS_PER_PLAN = 4;
    private static final int MAX_TRIGGERS_PER_TICK_PER_PLAN = 8;
    private static final int GRACE_TICKS = EffectAudioPlan.GRACE_TICKS;
    private static final Map<UUID, State> PLANS = new LinkedHashMap<>();

    private ClientEffectAudio() {
    }

    static void accept(EffectAudioPlanPayload payload) {
        EffectAudioPlan plan = payload.plan();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !level.dimension().location().equals(plan.dimensionId())) {
            return;
        }
        ClientEffectClock.observe(payload.serverGameTime(), level.getGameTime());
        int age = ageTicks(level, plan);
        if (age >= plan.durationTicks() + GRACE_TICKS || !plan.hasPlayableCues(age, GRACE_TICKS)) {
            return;
        }

        State replaced = PLANS.remove(plan.effectId());
        if (replaced != null) {
            replaced.stop();
        }
        State state = new State(plan, plan.firstCueIndex(age, GRACE_TICKS));
        PLANS.put(plan.effectId(), state);
        while (PLANS.size() > MAX_ACTIVE_PLANS) {
            Iterator<Map.Entry<UUID, State>> iterator = PLANS.entrySet().iterator();
            Map.Entry<UUID, State> oldest = iterator.next();
            iterator.remove();
            oldest.getValue().stop();
        }
        state.playDue(level, age);
    }

    static void stop(StopEffectAudioPayload payload) {
        State state = PLANS.get(payload.effectId());
        if (state == null || !state.plan.dimensionId().equals(payload.dimensionId())) {
            return;
        }
        PLANS.remove(payload.effectId());
        state.stop();
    }

    static void trigger(EffectAudioTriggerPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        State state = PLANS.get(payload.effectId());
        if (state == null) {
            return;
        }
        state.playTrigger(level, payload);
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }

        Iterator<Map.Entry<UUID, State>> iterator = PLANS.entrySet().iterator();
        while (iterator.hasNext()) {
            State state = iterator.next().getValue();
            if (!level.dimension().location().equals(state.plan.dimensionId())) {
                state.stop();
                iterator.remove();
                continue;
            }
            int age = ageTicks(level, state.plan);
            if (age > state.plan.durationTicks() + GRACE_TICKS) {
                state.stop();
                iterator.remove();
                continue;
            }
            state.tick(level, age);
        }
    }

    static void clear() {
        for (State state : PLANS.values()) {
            state.stop();
        }
        PLANS.clear();
    }

    private static int ageTicks(ClientLevel level, EffectAudioPlan plan) {
        long serverGameTime = ClientEffectClock.estimate(level.getGameTime());
        return EffectTimeline.ageTicks(serverGameTime, plan.startGameTime());
    }

    private static final class State {
        private final EffectAudioPlan plan;
        private final List<SoundInstance> activeInstances = new ArrayList<>();
        private final List<LoopTrack> loops = new ArrayList<>();
        private int nextCueIndex;
        private int triggersThisTick;

        private State(EffectAudioPlan plan, int nextCueIndex) {
            this.plan = plan;
            this.nextCueIndex = nextCueIndex;
        }

        private void tick(ClientLevel level, int age) {
            this.triggersThisTick = 0;
            this.prune();
            this.tickLoops(age);
            this.playDue(level, age);
        }

        private void playDue(ClientLevel level, int age) {
            while (this.nextCueIndex < this.plan.cueCount()) {
                EffectAudioCue cue = this.plan.cues().get(this.nextCueIndex);
                if (cue.tick() > age) {
                    return;
                }
                this.nextCueIndex++;
                if (cue.envelope().looping()) {
                    int stopTick = cue.envelope().stopTickOr(this.plan.durationTicks());
                    if (age < stopTick) {
                        this.startLoop(level, cue, stopTick, age);
                    }
                    continue;
                }
                if (age - cue.tick() > GRACE_TICKS) {
                    continue;
                }
                this.play(level, cue);
            }
        }

        private void startLoop(ClientLevel level, EffectAudioCue cue, int stopTick, int age) {
            SoundManager manager = Minecraft.getInstance().getSoundManager();
            while (this.loops.size() >= MAX_ACTIVE_LOOPS_PER_PLAN) {
                LoopTrack oldest = this.loops.remove(0);
                oldest.instance.stopNow();
                manager.stop(oldest.instance);
            }
            EffectLoopSoundInstance instance = new EffectLoopSoundInstance(cue);
            instance.setPosition(this.resolvePosition(cue));
            instance.setGain(cue.envelope().gainAt(age - cue.tick(), stopTick));
            manager.play(instance);
            this.loops.add(new LoopTrack(cue, stopTick, instance));
        }

        private void tickLoops(int age) {
            SoundManager manager = Minecraft.getInstance().getSoundManager();
            Iterator<LoopTrack> iterator = this.loops.iterator();
            while (iterator.hasNext()) {
                LoopTrack track = iterator.next();
                if (age >= track.stopTick) {
                    track.instance.stopNow();
                    manager.stop(track.instance);
                    iterator.remove();
                    continue;
                }
                track.instance.setPosition(this.resolvePosition(track.cue));
                track.instance.setGain(track.cue.envelope().gainAt(age - track.cue.tick(), track.stopTick));
            }
        }

        private Vec3 resolvePosition(EffectAudioCue cue) {
            Vec3 translation = Vec3.ZERO;
            if (cue.motion() instanceof EffectAudioMotion.FollowEffect) {
                Vec3 origin = ClientEffectFrameRuntime.effectOrigin(this.plan.effectId());
                if (origin != null) {
                    translation = origin.subtract(this.plan.center());
                }
            }
            return cue.motion().resolve(cue.position(), translation);
        }

        private void playTrigger(ClientLevel level, EffectAudioTriggerPayload payload) {
            EffectAudioTrigger trigger = this.plan.triggerOrNull(payload.triggerId());
            if (trigger == null || this.triggersThisTick >= MAX_TRIGGERS_PER_TICK_PER_PLAN) {
                return;
            }
            this.triggersThisTick++;
            EffectAudioCue cue = new EffectAudioCue(
                0,
                payload.position(),
                trigger.soundId(),
                trigger.source(),
                trigger.volume(),
                trigger.pitch(),
                trigger.attenuation()
            );
            this.play(level, cue);
        }

        private void play(ClientLevel level, EffectAudioCue cue) {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(cue.soundId());
            if (sound == null) {
                return;
            }
            if (cue.attenuation() instanceof EffectAudioAttenuation.Vanilla) {
                level.playLocalSound(
                    cue.position().x,
                    cue.position().y,
                    cue.position().z,
                    sound,
                    cue.source(),
                    cue.volume(),
                    cue.pitch(),
                    false
                );
                return;
            }
            EffectSoundInstance instance = new EffectSoundInstance(cue);
            Minecraft.getInstance().getSoundManager().play(instance);
            this.activeInstances.add(instance);
        }

        private void prune() {
            SoundManager manager = Minecraft.getInstance().getSoundManager();
            this.activeInstances.removeIf(instance -> !manager.isActive(instance));
        }

        private void stop() {
            SoundManager manager = Minecraft.getInstance().getSoundManager();
            for (SoundInstance instance : this.activeInstances) {
                manager.stop(instance);
            }
            for (LoopTrack track : this.loops) {
                track.instance.stopNow();
                manager.stop(track.instance);
            }
            this.activeInstances.clear();
            this.loops.clear();
        }
    }

    private static final class LoopTrack {
        private final EffectAudioCue cue;
        private final int stopTick;
        private final EffectLoopSoundInstance instance;

        private LoopTrack(EffectAudioCue cue, int stopTick, EffectLoopSoundInstance instance) {
            this.cue = cue;
            this.stopTick = stopTick;
            this.instance = instance;
        }
    }
}
