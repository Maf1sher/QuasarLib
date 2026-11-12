package mafisher.quasar.effect.network;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioCue;
import mafisher.quasar.effect.audio.EffectAudioEnvelope;
import mafisher.quasar.effect.audio.EffectAudioMotion;
import mafisher.quasar.effect.audio.EffectAudioPlan;
import mafisher.quasar.effect.audio.EffectAudioTrigger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Stream codecs for one effect audio plan, kept separate from the payload envelope. */
final class EffectAudioPlanCodec {
    static final StreamCodec<FriendlyByteBuf, EffectAudioAttenuation> ATTENUATION = StreamCodec.of(
        EffectAudioPlanCodec::writeAttenuation,
        EffectAudioPlanCodec::readAttenuation
    );

    static final StreamCodec<FriendlyByteBuf, EffectAudioCue> CUE = StreamCodec.of(
        EffectAudioPlanCodec::writeCue,
        EffectAudioPlanCodec::readCue
    );

    static final StreamCodec<FriendlyByteBuf, EffectAudioTrigger> TRIGGER = StreamCodec.of(
        EffectAudioPlanCodec::writeTrigger,
        EffectAudioPlanCodec::readTrigger
    );

    static final StreamCodec<FriendlyByteBuf, EffectAudioPlan> PLAN = StreamCodec.of(
        EffectAudioPlanCodec::writePlan,
        EffectAudioPlanCodec::readPlan
    );

    private EffectAudioPlanCodec() {
    }

    private static void writePlan(FriendlyByteBuf buffer, EffectAudioPlan plan) {
        buffer.writeUUID(plan.effectId());
        buffer.writeResourceLocation(plan.dimensionId());
        writePosition(buffer, plan.center());
        buffer.writeLong(plan.startGameTime());
        buffer.writeVarInt(plan.durationTicks());
        buffer.writeVarInt(plan.cueCount());
        for (EffectAudioCue cue : plan.cues()) {
            CUE.encode(buffer, cue);
        }
        buffer.writeVarInt(plan.triggerCount());
        for (EffectAudioTrigger trigger : plan.triggers()) {
            TRIGGER.encode(buffer, trigger);
        }
    }

    private static EffectAudioPlan readPlan(FriendlyByteBuf buffer) {
        UUID effectId = buffer.readUUID();
        ResourceLocation dimensionId = buffer.readResourceLocation();
        Vec3 center = readPosition(buffer);
        long startGameTime = buffer.readLong();
        int durationTicks = buffer.readVarInt();

        int cueCount = buffer.readVarInt();
        if (cueCount < 0 || cueCount > EffectAudioPlan.MAX_CUES) {
            throw new DecoderException("Invalid effect audio cue count: " + cueCount);
        }
        List<EffectAudioCue> cues = new ArrayList<>(cueCount);
        for (int index = 0; index < cueCount; index++) {
            cues.add(CUE.decode(buffer));
        }

        int triggerCount = buffer.readVarInt();
        if (triggerCount < 0 || triggerCount > EffectAudioPlan.MAX_TRIGGERS) {
            throw new DecoderException("Invalid effect audio trigger count: " + triggerCount);
        }
        List<EffectAudioTrigger> triggers = new ArrayList<>(triggerCount);
        for (int index = 0; index < triggerCount; index++) {
            triggers.add(TRIGGER.decode(buffer));
        }
        return new EffectAudioPlan(
            effectId, dimensionId, center, startGameTime, durationTicks, cues, triggers
        );
    }

    private static void writeCue(FriendlyByteBuf buffer, EffectAudioCue cue) {
        buffer.writeVarInt(cue.tick());
        writePosition(buffer, cue.position());
        buffer.writeResourceLocation(cue.soundId());
        buffer.writeEnum(cue.source());
        buffer.writeFloat(cue.volume());
        buffer.writeFloat(cue.pitch());
        ATTENUATION.encode(buffer, cue.attenuation());
        buffer.writeBoolean(cue.envelope().looping());
        buffer.writeVarInt(cue.envelope().fadeInTicks());
        buffer.writeVarInt(cue.envelope().fadeOutTicks());
        buffer.writeVarInt(cue.envelope().stopTick());
        buffer.writeByte(cue.motion() instanceof EffectAudioMotion.FollowEffect ? 1 : 0);
    }

    private static EffectAudioCue readCue(FriendlyByteBuf buffer) {
        return new EffectAudioCue(
            buffer.readVarInt(),
            readPosition(buffer),
            buffer.readResourceLocation(),
            buffer.readEnum(SoundSource.class),
            buffer.readFloat(),
            buffer.readFloat(),
            ATTENUATION.decode(buffer),
            new EffectAudioEnvelope(
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
            ),
            buffer.readUnsignedByte() == 1
                ? EffectAudioMotion.FOLLOW_EFFECT
                : EffectAudioMotion.ANCHORED
        );
    }

    private static void writeTrigger(FriendlyByteBuf buffer, EffectAudioTrigger trigger) {
        buffer.writeUtf(trigger.id());
        writePosition(buffer, trigger.position());
        buffer.writeResourceLocation(trigger.soundId());
        buffer.writeEnum(trigger.source());
        buffer.writeFloat(trigger.volume());
        buffer.writeFloat(trigger.pitch());
        ATTENUATION.encode(buffer, trigger.attenuation());
    }

    private static EffectAudioTrigger readTrigger(FriendlyByteBuf buffer) {
        return new EffectAudioTrigger(
            buffer.readUtf(EffectAudioTrigger.MAX_ID_LENGTH),
            readPosition(buffer),
            buffer.readResourceLocation(),
            buffer.readEnum(SoundSource.class),
            buffer.readFloat(),
            buffer.readFloat(),
            ATTENUATION.decode(buffer)
        );
    }

    private static void writeAttenuation(FriendlyByteBuf buffer, EffectAudioAttenuation attenuation) {
        if (attenuation instanceof EffectAudioAttenuation.Vanilla) {
            buffer.writeByte(0);
            return;
        }
        if (attenuation instanceof EffectAudioAttenuation.Distance distance) {
            buffer.writeByte(1);
            buffer.writeFloat(distance.maxDistance());
            return;
        }
        if (attenuation instanceof EffectAudioAttenuation.Directional directional) {
            buffer.writeByte(2);
            writePosition(buffer, directional.direction());
            buffer.writeFloat(directional.innerAngleDegrees());
            buffer.writeFloat(directional.outerAngleDegrees());
            buffer.writeFloat(directional.outsideGain());
            buffer.writeFloat(directional.maxDistance());
            return;
        }
        throw new IllegalArgumentException("Unknown effect audio attenuation type");
    }

    private static EffectAudioAttenuation readAttenuation(FriendlyByteBuf buffer) {
        int type = buffer.readUnsignedByte();
        return switch (type) {
            case 0 -> EffectAudioAttenuation.VANILLA;
            case 1 -> EffectAudioAttenuation.distance(buffer.readFloat());
            case 2 -> EffectAudioAttenuation.directional(
                readPosition(buffer),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
            );
            default -> throw new DecoderException("Unknown effect audio attenuation type: " + type);
        };
    }

    private static void writePosition(FriendlyByteBuf buffer, Vec3 position) {
        buffer.writeDouble(position.x);
        buffer.writeDouble(position.y);
        buffer.writeDouble(position.z);
    }

    private static Vec3 readPosition(FriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
