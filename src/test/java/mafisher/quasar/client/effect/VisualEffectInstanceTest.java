package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class VisualEffectInstanceTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");
    private static final ResourceLocation DIMENSION = ResourceLocation.withDefaultNamespace("overworld");

    @Test
    void startsItsTimelineAtTheServerStartTime() {
        StartVisualEffectPayload payload = payload(100L, 0.0);

        VisualEffectInstance instance = new VisualEffectInstance(payload);

        assertEquals(100L, instance.previousTransformGameTime());
        assertEquals(100L, instance.currentTransformGameTime());
        assertEquals(100L, payload.startGameTime());
    }

    @Test
    void acceptsServerUpdatesThatAreOlderThanTheClientClock() {
        StartVisualEffectPayload payload = payload(100L, 0.0);
        VisualEffectInstance instance = new VisualEffectInstance(payload);

        // The client clock may already be at 200 while the server update reports tick 106.
        VisualEffectInstance updated = instance.withTransform(transform(12.0), 106L);

        assertEquals(instance.currentTransform(), updated.previousTransform());
        assertEquals(100L, updated.previousTransformGameTime());
        assertEquals(106L, updated.currentTransformGameTime());
    }

    @Test
    void interpolatesAgainstServerTimeInsteadOfClientTime() {
        StartVisualEffectPayload payload = payload(100L, 0.0);
        VisualEffectInstance updated = new VisualEffectInstance(payload).withTransform(transform(12.0), 106L);

        EffectTransform interpolated = updated.interpolatedTransform(105L, 0.5F);

        assertEquals(9.0, interpolated.position().x(), 1.0E-5);
    }

    @Test
    void ignoresUpdatesOlderThanTheCurrentServerTime() {
        StartVisualEffectPayload payload = payload(100L, 0.0);
        VisualEffectInstance updated = new VisualEffectInstance(payload).withTransform(transform(12.0), 106L);

        VisualEffectInstance stale = updated.withTransform(transform(4.0), 104L);

        assertSame(updated, stale);
    }

    private static StartVisualEffectPayload payload(long startGameTime, double x) {
        return new StartVisualEffectPayload(
            EFFECT_ID,
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            DIMENSION,
            x,
            64.0,
            0.0,
            0.0F,
            0.0F,
            0.0F,
            1.0F,
            200,
            startGameTime,
            startGameTime,
            7L,
            null
        );
    }

    private static EffectTransform transform(double x) {
        return new EffectTransform(new EffectPoint(x, 64.0, 0.0), 0.0F, 0.0F, 0.0F);
    }
}
