package mafisher.quasar.client.effect;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import mafisher.quasar.effect.cinematic.CinematicAudioCue;
import mafisher.quasar.effect.cinematic.CinematicAudioPlan;
import mafisher.quasar.effect.network.CinematicAudioPlanPayload;
import mafisher.quasar.effect.network.StopCinematicAudioPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Client-side scheduler for cinematic sound plans; cue timing follows client game time, not packet arrival. */
public final class ClientCinematicAudio {
    private static final int MAX_ACTIVE_PLANS = 16;
    private static final int GRACE_TICKS = 2;
    private static final Map<UUID, State> PLANS = new LinkedHashMap<>();

    private ClientCinematicAudio() {
    }

    static void accept(CinematicAudioPlanPayload payload) {
        CinematicAudioPlan plan = payload.plan();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !level.dimension().location().equals(plan.dimensionId())) {
            return;
        }
        int age = ageTicks(level, plan);
        if (age >= plan.durationTicks() + GRACE_TICKS) {
            return;
        }

        State state = new State(plan, plan.firstCueIndex(age, GRACE_TICKS));
        PLANS.put(plan.sessionId(), state);
        while (PLANS.size() > MAX_ACTIVE_PLANS) {
            Iterator<UUID> iterator = PLANS.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        state.playDue(level, age);
    }

    static void stop(StopCinematicAudioPayload payload) {
        PLANS.remove(payload.sessionId());
    }

    static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            PLANS.clear();
            return;
        }

        Iterator<Map.Entry<UUID, State>> iterator = PLANS.entrySet().iterator();
        while (iterator.hasNext()) {
            State state = iterator.next().getValue();
            if (!level.dimension().location().equals(state.plan.dimensionId())) {
                iterator.remove();
                continue;
            }
            int age = ageTicks(level, state.plan);
            if (age > state.plan.durationTicks() + GRACE_TICKS) {
                iterator.remove();
                continue;
            }
            state.playDue(level, age);
        }
    }

    static void clear() {
        PLANS.clear();
    }

    private static int ageTicks(ClientLevel level, CinematicAudioPlan plan) {
        return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, level.getGameTime() - plan.startGameTime()));
    }

    private static final class State {
        private final CinematicAudioPlan plan;
        private int nextCueIndex;

        private State(CinematicAudioPlan plan, int nextCueIndex) {
            this.plan = plan;
            this.nextCueIndex = nextCueIndex;
        }

        private void playDue(ClientLevel level, int age) {
            while (this.nextCueIndex < this.plan.cueCount()) {
                CinematicAudioCue cue = this.plan.cues().get(this.nextCueIndex);
                if (cue.tick() > age) {
                    return;
                }
                this.nextCueIndex++;
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(cue.soundId());
                if (sound == null) {
                    continue;
                }
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
            }
        }
    }
}
