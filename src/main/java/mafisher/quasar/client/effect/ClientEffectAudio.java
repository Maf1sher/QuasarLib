package mafisher.quasar.client.effect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioCue;
import mafisher.quasar.effect.audio.EffectAudioPlan;
import mafisher.quasar.effect.network.EffectAudioPlanPayload;
import mafisher.quasar.effect.network.StopEffectAudioPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Client-side scheduler for effect sound plans; cue timing follows a server-relative timeline, not packet arrival. */
public final class ClientEffectAudio {
    private static final int MAX_ACTIVE_PLANS = 128;
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
            state.prune();
            state.playDue(level, age);
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
        return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, serverGameTime - plan.startGameTime()));
    }

    private static final class State {
        private final EffectAudioPlan plan;
        private final List<SoundInstance> activeInstances = new ArrayList<>();
        private int nextCueIndex;

        private State(EffectAudioPlan plan, int nextCueIndex) {
            this.plan = plan;
            this.nextCueIndex = nextCueIndex;
        }

        private void playDue(ClientLevel level, int age) {
            while (this.nextCueIndex < this.plan.cueCount()) {
                EffectAudioCue cue = this.plan.cues().get(this.nextCueIndex);
                if (cue.tick() > age) {
                    return;
                }
                this.nextCueIndex++;
                if (age - cue.tick() > GRACE_TICKS) {
                    continue;
                }
                this.play(level, cue);
            }
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
            this.activeInstances.clear();
        }
    }
}
