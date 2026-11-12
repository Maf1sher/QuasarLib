package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioEnvelope;
import mafisher.quasar.api.audio.EffectAudioMotion;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.audio.EffectAudioTrigger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EffectAudioPlanPayloadTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation SOUND = ResourceLocation.withDefaultNamespace("test");

    @Test
    void planPayloadRoundTrips() {
        UUID effectId = UUID.randomUUID();
        EffectAudioPlan plan = new EffectAudioPlan(
            effectId,
            DIMENSION,
            new Vec3(1.0, 64.0, -2.0),
            1234L,
            200,
            List.of(
                new EffectAudioCue(0, new Vec3(1.5, 64.0, -2.5), SOUND, SoundSource.MASTER, 1.0F, 1.0F,
                    EffectAudioAttenuation.distance(64.0F)),
                new EffectAudioCue(40, Vec3.ZERO, SOUND, SoundSource.PLAYERS, 0.5F, 1.2F,
                    EffectAudioAttenuation.directional(new Vec3(0.0, -1.0, 0.0), 30.0F, 90.0F, 0.25F, 48.0F)),
                new EffectAudioCue(60, new Vec3(2.0, 3.0, 4.0), SOUND, SoundSource.AMBIENT, 1.0F, 1.0F,
                    EffectAudioAttenuation.distance(64.0F),
                    EffectAudioEnvelope.loopUntil(150, 10, 20),
                    EffectAudioMotion.FOLLOW_EFFECT)
            ),
            List.of(new EffectAudioTrigger(
                "impact", new Vec3(8.0, 9.0, 10.0), SOUND, SoundSource.PLAYERS, 0.8F, 1.1F,
                EffectAudioAttenuation.distance(32.0F)
            ))
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());

        EffectAudioPlanPayload.STREAM_CODEC.encode(buffer, new EffectAudioPlanPayload(plan, 1300L));
        EffectAudioPlanPayload decodedPayload = EffectAudioPlanPayload.STREAM_CODEC.decode(buffer);
        EffectAudioPlan decoded = decodedPayload.plan();

        assertEquals(1300L, decodedPayload.serverGameTime());
        assertEquals(effectId, decoded.effectId());
        assertEquals(DIMENSION, decoded.dimensionId());
        assertEquals(1234L, decoded.startGameTime());
        assertEquals(200, decoded.durationTicks());
        assertEquals(3, decoded.cueCount());
        assertEquals(40, decoded.cues().get(1).tick());
        assertEquals(SoundSource.PLAYERS, decoded.cues().get(1).source());
        assertEquals(1.5, decoded.cues().getFirst().position().x);
        assertEquals(0.25F,
            ((EffectAudioAttenuation.Directional)decoded.cues().get(1).attenuation()).outsideGain());
        assertEquals(EffectAudioEnvelope.loopUntil(150, 10, 20), decoded.cues().get(2).envelope());
        assertEquals(EffectAudioMotion.FOLLOW_EFFECT, decoded.cues().get(2).motion());
        assertEquals(1, decoded.triggerCount());
        assertEquals("impact", decoded.triggers().getFirst().id());
        assertEquals(8.0, decoded.triggers().getFirst().position().x);
        assertEquals(32.0F,
            ((EffectAudioAttenuation.Distance)decoded.triggers().getFirst().attenuation()).maxDistance());
    }

    @Test
    void rejectsAnOversizedCueCount() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeResourceLocation(DIMENSION);
        buffer.writeDouble(0.0);
        buffer.writeDouble(0.0);
        buffer.writeDouble(0.0);
        buffer.writeLong(0L);
        buffer.writeVarInt(200);
        buffer.writeVarInt(EffectAudioPlan.MAX_CUES + 1);

        assertThrows(DecoderException.class, () -> EffectAudioPlanPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void rejectsAnUnknownAttenuationType() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buffer.writeUUID(UUID.randomUUID());
        buffer.writeResourceLocation(DIMENSION);
        buffer.writeDouble(0.0);
        buffer.writeDouble(0.0);
        buffer.writeDouble(0.0);
        buffer.writeLong(0L);
        buffer.writeVarInt(200);
        buffer.writeVarInt(1);
        buffer.writeVarInt(0);
        buffer.writeDouble(0.0);
        buffer.writeDouble(0.0);
        buffer.writeDouble(0.0);
        buffer.writeResourceLocation(SOUND);
        buffer.writeEnum(SoundSource.MASTER);
        buffer.writeFloat(1.0F);
        buffer.writeFloat(1.0F);
        buffer.writeByte(9);

        assertThrows(DecoderException.class, () -> EffectAudioPlanPayload.STREAM_CODEC.decode(buffer));
    }

    @Test
    void triggerPayloadRoundTrips() {
        UUID effectId = UUID.randomUUID();
        EffectAudioTriggerPayload payload = new EffectAudioTriggerPayload(
            effectId, DIMENSION, "impact", new Vec3(1.0, 2.0, 3.0), 900L
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());

        EffectAudioTriggerPayload.STREAM_CODEC.encode(buffer, payload);
        EffectAudioTriggerPayload decoded = EffectAudioTriggerPayload.STREAM_CODEC.decode(buffer);

        assertEquals(effectId, decoded.effectId());
        assertEquals(DIMENSION, decoded.dimensionId());
        assertEquals("impact", decoded.triggerId());
        assertEquals(new Vec3(1.0, 2.0, 3.0), decoded.position());
        assertEquals(900L, decoded.serverGameTime());
    }

    @Test
    void stopPayloadRoundTrips() {
        UUID effectId = UUID.randomUUID();
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());

        StopEffectAudioPayload.STREAM_CODEC.encode(buffer, new StopEffectAudioPayload(effectId, DIMENSION));
        StopEffectAudioPayload decoded = StopEffectAudioPayload.STREAM_CODEC.decode(buffer);

        assertEquals(effectId, decoded.effectId());
        assertEquals(DIMENSION, decoded.dimensionId());
    }
}
