package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.client.effect.EffectRenderContext;
import mafisher.quasar.client.effect.EffectRenderPass;
import mafisher.quasar.client.effect.ScreenEffectMask;
import mafisher.quasar.client.effect.ScreenEffectSettings;
import mafisher.quasar.client.effect.VisualEffectInstance;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class ContentRuneCircleRendererTest {
    @Test
    void declaresOrderedDistinctPasses() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();

        List<EffectRenderPass> passes = renderer.renderPasses(context());

        assertEquals(3, passes.size());
        assertEquals(3, passes.stream().map(EffectRenderPass::id).distinct().count());
        assertTrue(passes.stream().allMatch(pass -> pass.renderType() != null));
    }

    @Test
    void selectsContentOwnedScreenGlow() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();
        ScreenEffectSettings settings = renderer.screenEffectSettings(context());

        assertSame(ContentScreenOperations.RUNE_GLOW, settings.operation());
        assertSame(ContentRuneGlowSettings.DEFAULT,
            settings.settings(ContentRuneGlowSettings.class));
        ScreenEffectMask mask = renderer.screenEffectMask(context());
        assertTrue(mask.strength() > 0.0F);
        assertNotNull(renderer.screenMaskRenderType(context()));
    }

    @Test
    void returnsFiniteConservativeBounds() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();

        AABB bounds = renderer.cullingBounds(context());

        assertTrue(Double.isFinite(bounds.minX) && Double.isFinite(bounds.minY) && Double.isFinite(bounds.minZ));
        assertTrue(Double.isFinite(bounds.maxX) && Double.isFinite(bounds.maxY) && Double.isFinite(bounds.maxZ));
        assertTrue(bounds.maxX - bounds.minX > 10.0);
        assertTrue(bounds.maxY - bounds.minY > 10.0);
        assertTrue(bounds.maxZ - bounds.minZ > 10.0);
    }

    private static EffectRenderContext context() {
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            ContentEffectIds.RUNE_CIRCLE,
            UUID.randomUUID(),
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
            0.0,
            0.0,
            0.0,
            0.0F,
            0.0F,
            0.0F,
            5.0F,
            100,
            0L,
            12_345L
        );
        return new EffectRenderContext(new VisualEffectInstance(payload), payload.transform(), 10.0F, 0.1F, 0.5F);
    }
}
