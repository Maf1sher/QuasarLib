package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.client.effect.EffectInstancedPass;
import mafisher.quasar.client.effect.EffectLod;
import mafisher.quasar.client.effect.EffectRenderContext;
import mafisher.quasar.client.effect.ScreenEffectMask;
import mafisher.quasar.client.effect.ScreenEffectSettings;
import mafisher.quasar.client.effect.VisualEffectInstance;
import mafisher.quasar.effect.EffectQualityTier;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class ContentRuneCircleRendererTest {
    @Test
    void declaresOrderedDistinctInstancedPasses() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();

        List<EffectInstancedPass> passes = renderer.instancedPasses(context());

        assertTrue(renderer.renderPasses(context()).isEmpty());
        assertEquals(3, passes.size());
        assertEquals(3, passes.stream().map(EffectInstancedPass::id).distinct().count());
        assertTrue(passes.stream().allMatch(pass -> pass.renderType() != null && pass.mesh() != null));
        assertTrue(passes.stream().allMatch(pass -> pass.localTransform().isFinite()));
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
    void thinsPassesAtReducedLevelOfDetail() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();
        EffectRenderContext full = context();
        EffectRenderContext reduced = full.withLod(new EffectLod(1, 128.0, 8.0));

        assertEquals(3, renderer.instancedPasses(full).size());
        assertEquals(List.of("outer"),
            renderer.instancedPasses(reduced).stream().map(EffectInstancedPass::id).toList());
    }

    @Test
    void trimsLayersByQualityTier() {
        ContentRuneCircleRenderer renderer = new ContentRuneCircleRenderer();
        EffectRenderContext context = context();

        assertEquals(1, renderer.instancedPasses(context.withQuality(EffectQualityTier.LOW)).size());
        assertEquals(2, renderer.instancedPasses(context.withQuality(EffectQualityTier.MEDIUM)).size());
        assertEquals(3, renderer.instancedPasses(context.withQuality(EffectQualityTier.HIGH)).size());
        assertEquals(3, renderer.instancedPasses(context.withQuality(EffectQualityTier.ULTRA)).size());
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
        assertTrue(renderer.spatialRadius(context()) >= (bounds.maxX - bounds.minX) / 2.0);
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
