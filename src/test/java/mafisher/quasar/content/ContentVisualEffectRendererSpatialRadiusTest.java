package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.EffectLodProfile;
import mafisher.quasar.api.client.EffectRenderContext;
import mafisher.quasar.api.client.VisualEffectRenderer;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentVisualEffectRendererSpatialRadiusTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_content", "test");

    @Test
    void defaultsToInfinite() {
        VisualEffectRenderer renderer = new VisualEffectRenderer() {
        };

        assertEquals(Double.POSITIVE_INFINITY, renderer.spatialRadius(context(5.0F, 0.0F)));
        assertEquals(EffectLodProfile.FULL, renderer.lodProfile());
    }

    @Test
    void contentRenderersCoverTheirCullingBounds() {
        List<VisualEffectRenderer> renderers = List.of(
            new ContentRuneCircleRenderer(),
            new ContentWaveDistortionRenderer(),
            new ContentSceneBlurRenderer(),
            new ContentBloomRenderer(),
            new ContentBlackHoleRenderer(),
            new ContentBlackHoleMeshRenderer()
        );

        for (VisualEffectRenderer renderer : renderers) {
            for (float scale : List.of(1.0F, 5.0F, 32.0F, 128.0F)) {
                for (float progress : List.of(0.0F, 0.25F, 0.9F)) {
                    EffectRenderContext context = context(scale, progress);
                    AABB bounds = renderer.cullingBounds(context);
                    double radius = renderer.spatialRadius(context);
                    double extent = maximumExtent(bounds, context.transform().position().x(),
                        context.transform().position().y(), context.transform().position().z());

                    assertTrue(Double.isFinite(radius));
                    assertTrue(radius >= extent - 1.0E-6,
                        () -> renderer.getClass().getSimpleName() + " spatial radius is smaller than its bounds");
                }
            }
        }
    }

    private static EffectRenderContext context(float scale, float progress) {
        StartVisualEffectPayload payload = TestVisualEffects.payload(
            EFFECT_ID,
            UUID.randomUUID(),
            new Vec3(10.0, 64.0, -5.0),
            20.0F,
            5.0F,
            -3.0F,
            scale,
            100,
            1234L
        );
        return TestVisualEffects.context(payload, progress * 100.0F, progress);
    }

    private static double maximumExtent(AABB bounds, double x, double y, double z) {
        double max = 0.0;
        max = Math.max(max, distance(bounds.minX, y, z, x, y, z));
        max = Math.max(max, distance(bounds.maxX, y, z, x, y, z));
        max = Math.max(max, distance(x, bounds.minY, z, x, y, z));
        max = Math.max(max, distance(x, bounds.maxY, z, x, y, z));
        max = Math.max(max, distance(x, y, bounds.minZ, x, y, z));
        max = Math.max(max, distance(x, y, bounds.maxZ, x, y, z));
        return max;
    }

    private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
