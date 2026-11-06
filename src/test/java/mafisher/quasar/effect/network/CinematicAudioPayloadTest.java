package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.effect.cinematic.CinematicAudioCue;
import mafisher.quasar.effect.cinematic.CinematicAudioPlan;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CinematicAudioPayloadTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void planPayloadRoundTrips() {
        UUID sessionId = UUID.randomUUID();
        CinematicAudioPlan plan = new CinematicAudioPlan(
            sessionId,
            ResourceLocation.fromNamespaceAndPath("quasar_lib", "plan"),
            DIMENSION,
            1234L,
            200,
            List.of(
                new CinematicAudioCue(
                    0,
                    new Vec3(1.5, 64.0, -2.5),
                    ResourceLocation.fromNamespaceAndPath("minecraft", "block.amethyst_block.chime"),
                    SoundSource.MASTER,
                    1.0F,
                    1.0F
                ),
                new CinematicAudioCue(
                    40,
                    Vec3.ZERO,
                    ResourceLocation.fromNamespaceAndPath("minecraft", "ui.button.click"),
                    SoundSource.PLAYERS,
                    0.5F,
                    1.2F
                )
            )
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());

        CinematicAudioPlanPayload.STREAM_CODEC.encode(buffer, new CinematicAudioPlanPayload(plan));
        CinematicAudioPlan decoded = CinematicAudioPlanPayload.STREAM_CODEC.decode(buffer).plan();

        assertEquals(sessionId, decoded.sessionId());
        assertEquals(plan.timelineId(), decoded.timelineId());
        assertEquals(DIMENSION, decoded.dimensionId());
        assertEquals(1234L, decoded.startGameTime());
        assertEquals(200, decoded.durationTicks());
        assertEquals(2, decoded.cueCount());
        assertEquals(40, decoded.cues().get(1).tick());
        assertEquals(SoundSource.PLAYERS, decoded.cues().get(1).source());
        assertEquals(1.5, decoded.cues().getFirst().position().x);
    }

    @Test
    void stopPayloadRoundTrips() {
        UUID sessionId = UUID.randomUUID();
        FriendlyByteBuf buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());

        StopCinematicAudioPayload.STREAM_CODEC.encode(buffer, new StopCinematicAudioPayload(sessionId, DIMENSION));
        StopCinematicAudioPayload decoded = StopCinematicAudioPayload.STREAM_CODEC.decode(buffer);

        assertEquals(sessionId, decoded.sessionId());
        assertEquals(DIMENSION, decoded.dimensionId());
    }
}
