package mafisher.quasar.effect.network;

import io.netty.handler.codec.DecoderException;
import mafisher.quasar.QuasarLib;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.effect.cinematic.CinematicAudioCue;
import mafisher.quasar.effect.cinematic.CinematicAudioPlan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Server-to-client sound schedule for one cinematic session. */
public record CinematicAudioPlanPayload(CinematicAudioPlan plan) implements CustomPacketPayload {
    public static final Type<CinematicAudioPlanPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "cinematic_audio_plan")
    );
    public static final StreamCodec<FriendlyByteBuf, CinematicAudioPlanPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> writePlan(buffer, payload.plan()),
        buffer -> new CinematicAudioPlanPayload(readPlan(buffer))
    );

    public CinematicAudioPlanPayload {
        Objects.requireNonNull(plan, "plan");
    }

    @Override
    public Type<CinematicAudioPlanPayload> type() {
        return TYPE;
    }

    private static void writePlan(FriendlyByteBuf buffer, CinematicAudioPlan plan) {
        buffer.writeUUID(plan.sessionId());
        buffer.writeResourceLocation(plan.timelineId());
        buffer.writeResourceLocation(plan.dimensionId());
        buffer.writeLong(plan.startGameTime());
        buffer.writeVarInt(plan.durationTicks());
        buffer.writeVarInt(plan.cueCount());
        for (CinematicAudioCue cue : plan.cues()) {
            buffer.writeVarInt(cue.tick());
            buffer.writeDouble(cue.position().x);
            buffer.writeDouble(cue.position().y);
            buffer.writeDouble(cue.position().z);
            buffer.writeResourceLocation(cue.soundId());
            buffer.writeEnum(cue.source());
            buffer.writeFloat(cue.volume());
            buffer.writeFloat(cue.pitch());
        }
    }

    private static CinematicAudioPlan readPlan(FriendlyByteBuf buffer) {
        UUID sessionId = buffer.readUUID();
        ResourceLocation timelineId = buffer.readResourceLocation();
        ResourceLocation dimensionId = buffer.readResourceLocation();
        long startGameTime = buffer.readLong();
        int durationTicks = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > CinematicAudioPlan.MAX_CUES) {
            throw new DecoderException("Invalid cinematic audio cue count: " + count);
        }
        List<CinematicAudioCue> cues = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cues.add(new CinematicAudioCue(
                buffer.readVarInt(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readResourceLocation(),
                buffer.readEnum(SoundSource.class),
                buffer.readFloat(),
                buffer.readFloat()
            ));
        }
        return new CinematicAudioPlan(sessionId, timelineId, dimensionId, startGameTime, durationTicks, cues);
    }
}
