package mafisher.quasar.content.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.particle.EffectParticlePass;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ContentParticleRendererTest {
    private static final ContentParticleRenderer RENDERER = new ContentParticleRenderer();

    @Test
    void returnsThreeCachedFieldPasses() {
        EffectRenderContext context = context();
        List<EffectParticlePass> passes = RENDERER.particlePasses(context);

        assertEquals(3, passes.size());
        assertSame(passes, RENDERER.particlePasses(context));
        assertEquals(3, passes.stream().map(EffectParticlePass::id).distinct().count());
        assertEquals(3, passes.stream().map(pass -> pass.field().salt()).distinct().count());
        assertTrue(passes.stream().allMatch(pass -> pass.field().maxParticles() > 0));
    }

    @Test
    void cullingBoundsContainTheEffectOrigin() {
        EffectRenderContext context = context();
        AABB bounds = RENDERER.cullingBounds(context);
        var position = context.transform().position();

        assertTrue(bounds.contains(new Vec3(position.x(), position.y(), position.z())));
        assertTrue(RENDERER.spatialRadius(context) > 0.0);
    }

    private static EffectRenderContext context() {
        return TestVisualEffects.context(
            TestVisualEffects.EFFECT_ID, UUID.randomUUID(), new Vec3(10.0, 64.0, -4.0),
            5.0F, 600, 0.25F
        );
    }
}
