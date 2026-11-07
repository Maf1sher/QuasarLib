package mafisher.quasar.effect.network;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioCue;
import mafisher.quasar.effect.audio.EffectAudioEnvelope;
import mafisher.quasar.effect.audio.EffectAudioMotion;
import mafisher.quasar.effect.audio.EffectAudioPlan;
import mafisher.quasar.effect.audio.EffectAudioTrigger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Server-to-client sound schedule for one effect instance.
 *
 * @param plan immutable cue schedule
 * @param serverGameTime server game time when this payload was created; clients use it to age the
 *     plan without trusting their own clock
 */
public record EffectAudioPlanPayload(EffectAudioPlan plan, long serverGameTime) implements CustomPacketPayload {
    public static final Type<EffectAudioPlanPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "effect_audio_plan")
    );
    public static final StreamCodec<FriendlyByteBuf, EffectAudioPlanPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            writePlan(buffer, payload.plan());
            buffer.writeLong(payload.serverGameTime());
        },
        buffer -> new EffectAudioPlanPayload(readPlan(buffer), buffer.readLong())
    );

    public EffectAudioPlanPayload {
        Objects.requireNonNull(plan, "plan");
    }

    public EffectAudioPlanPayload(EffectAudioPlan plan) {
        this(plan, plan.startGameTime());
    }

    @Override
    public Type<EffectAudioPlanPayload> type() {
        return TYPE;
    }

    private static void writePlan(FriendlyByteBuf buffer, EffectAudioPlan plan) {
        buffer.writeUUID(plan.effectId());
        buffer.writeResourceLocation(plan.dimensionId());
        buffer.writeDouble(plan.center().x);
        buffer.writeDouble(plan.center().y);
        buffer.writeDouble(plan.center().z);
        buffer.writeLong(plan.startGameTime());
        buffer.writeVarInt(plan.durationTicks());
        buffer.writeVarInt(plan.cueCount());
        for (EffectAudioCue cue : plan.cues()) {
            buffer.writeVarInt(cue.tick());
            buffer.writeDouble(cue.position().x);
            buffer.writeDouble(cue.position().y);
            buffer.writeDouble(cue.position().z);
            buffer.writeResourceLocation(cue.soundId());
            buffer.writeEnum(cue.source());
            buffer.writeFloat(cue.volume());
            buffer.writeFloat(cue.pitch());
            writeAttenuation(buffer, cue.attenuation());
            buffer.writeBoolean(cue.envelope().looping());
            buffer.writeVarInt(cue.envelope().fadeInTicks());
            buffer.writeVarInt(cue.envelope().fadeOutTicks());
            buffer.writeVarInt(cue.envelope().stopTick());
            buffer.writeByte(cue.motion() instanceof EffectAudioMotion.FollowEffect ? 1 : 0);
        }
        buffer.writeVarInt(plan.triggerCount());
        for (EffectAudioTrigger trigger : plan.triggers()) {
            buffer.writeUtf(trigger.id());
            buffer.writeDouble(trigger.position().x);
            buffer.writeDouble(trigger.position().y);
            buffer.writeDouble(trigger.position().z);
            buffer.writeResourceLocation(trigger.soundId());
            buffer.writeEnum(trigger.source());
            buffer.writeFloat(trigger.volume());
            buffer.writeFloat(trigger.pitch());
            writeAttenuation(buffer, trigger.attenuation());
        }
    }

    private static EffectAudioPlan readPlan(FriendlyByteBuf buffer) {
        UUID effectId = buffer.readUUID();
        ResourceLocation dimensionId = buffer.readResourceLocation();
        Vec3 center = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        long startGameTime = buffer.readLong();
        int durationTicks = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > EffectAudioPlan.MAX_CUES) {
            throw new DecoderException("Invalid effect audio cue count: " + count);
        }
        List<EffectAudioCue> cues = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cues.add(new EffectAudioCue(
                buffer.readVarInt(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readResourceLocation(),
                buffer.readEnum(SoundSource.class),
                buffer.readFloat(),
                buffer.readFloat(),
                readAttenuation(buffer),
                new EffectAudioEnvelope(
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt()
                ),
                buffer.readUnsignedByte() == 1
                    ? EffectAudioMotion.FOLLOW_EFFECT
                    : EffectAudioMotion.ANCHORED
            ));
        }

        int triggerCount = buffer.readVarInt();
        if (triggerCount < 0 || triggerCount > EffectAudioPlan.MAX_TRIGGERS) {
            throw new DecoderException("Invalid effect audio trigger count: " + triggerCount);
        }
        List<EffectAudioTrigger> triggers = new ArrayList<>(triggerCount);
        for (int index = 0; index < triggerCount; index++) {
            triggers.add(new EffectAudioTrigger(
                buffer.readUtf(EffectAudioTrigger.MAX_ID_LENGTH),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readResourceLocation(),
                buffer.readEnum(SoundSource.class),
                buffer.readFloat(),
                buffer.readFloat(),
                readAttenuation(buffer)
            ));
        }
        return new EffectAudioPlan(
            effectId, dimensionId, center, startGameTime, durationTicks, cues, triggers
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
            buffer.writeDouble(directional.direction().x);
            buffer.writeDouble(directional.direction().y);
            buffer.writeDouble(directional.direction().z);
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
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
            );
            default -> throw new DecoderException("Unknown effect audio attenuation type: " + type);
        };
    }
}
