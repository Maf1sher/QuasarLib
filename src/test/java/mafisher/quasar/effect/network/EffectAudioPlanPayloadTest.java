package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.audio.EffectAudioAttenuation;
import mafisher.quasar.effect.audio.EffectAudioCue;
import mafisher.quasar.effect.audio.EffectAudioPlan;
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
                    EffectAudioAttenuation.directional(new Vec3(0.0, -1.0, 0.0), 30.0F, 90.0F, 0.25F, 48.0F))
            )
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
        assertEquals(2, decoded.cueCount());
        assertEquals(40, decoded.cues().get(1).tick());
        assertEquals(SoundSource.PLAYERS, decoded.cues().get(1).source());
        assertEquals(1.5, decoded.cues().getFirst().position().x);
        assertEquals(0.25F,
            ((EffectAudioAttenuation.Directional)decoded.cues().get(1).attenuation()).outsideGain());
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
