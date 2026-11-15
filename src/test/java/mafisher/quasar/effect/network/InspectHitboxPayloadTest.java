package mafisher.quasar.effect.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectBounds;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.damage.EffectDamageShape;
import mafisher.quasar.api.effect.damage.EffectHitboxOutcome;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class InspectHitboxPayloadTest {
    @Test
    void roundTripsEveryShapeAndOutcome() {
        InspectHitboxPayload payload = new InspectHitboxPayload(
            42L,
            ResourceLocation.withDefaultNamespace("overworld"),
            List.of(new InspectHitboxPayload.Session(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                ResourceLocation.fromNamespaceAndPath("quasar_content", "black_hole"),
                12,
                1,
                3,
                List.of(
                    new InspectHitboxPayload.Shape(
                        new EffectDamageShape.Box(new EffectBounds(-1, -2, -3, 4, 5, 6)),
                        EffectHitboxOutcome.DAMAGE
                    ),
                    new InspectHitboxPayload.Shape(
                        new EffectDamageShape.Sphere(new EffectPoint(1, 2, 3), 4.0),
                        EffectHitboxOutcome.REMOVE
                    ),
                    new InspectHitboxPayload.Shape(
                        new EffectDamageShape.Cylinder(new EffectPoint(1, 2, 3), 4.0, 5.0),
                        EffectHitboxOutcome.DAMAGE
                    ),
                    new InspectHitboxPayload.Shape(
                        new EffectDamageShape.Beam(new EffectPoint(0, 0, 0), new EffectPoint(1, 1, 1), 2.0),
                        EffectHitboxOutcome.REMOVE
                    )
                )
            ))
        );
        ByteBuf buffer = Unpooled.buffer();

        InspectHitboxPayload.STREAM_CODEC.encode(
            new FriendlyByteBuf(buffer), payload
        );
        assertTrue(buffer.readableBytes() <= InspectHitboxPayload.MAX_BYTES);
        InspectHitboxPayload decoded = InspectHitboxPayload.STREAM_CODEC.decode(
            new FriendlyByteBuf(buffer)
        );

        assertEquals(payload, decoded);
        assertEquals(1, decoded.sessions().size());
        assertEquals(4, decoded.sessions().getFirst().shapes().size());
    }

    @Test
    void rejectsOversizedSessionsAndShapes() {
        List<InspectHitboxPayload.Session> sessions = new ArrayList<>();
        for (int index = 0; index < InspectHitboxPayload.MAX_SESSIONS + 1; index++) {
            sessions.add(session(List.of(shape())));
        }
        assertThrows(IllegalArgumentException.class, () -> new InspectHitboxPayload(
            0L, ResourceLocation.withDefaultNamespace("overworld"), sessions
        ));

        List<InspectHitboxPayload.Shape> shapes = new ArrayList<>();
        for (int index = 0; index < InspectHitboxPayload.MAX_SHAPES_PER_SESSION + 1; index++) {
            shapes.add(shape());
        }
        assertThrows(IllegalArgumentException.class, () -> session(shapes));
    }

    private static InspectHitboxPayload.Session session(List<InspectHitboxPayload.Shape> shapes) {
        return new InspectHitboxPayload.Session(
            UUID.randomUUID(),
            ResourceLocation.fromNamespaceAndPath("quasar_content", "test"),
            0,
            0,
            1,
            shapes
        );
    }

    private static InspectHitboxPayload.Shape shape() {
        return new InspectHitboxPayload.Shape(
            new EffectDamageShape.Sphere(new EffectPoint(0, 0, 0), 1.0),
            EffectHitboxOutcome.DAMAGE
        );
    }
}
