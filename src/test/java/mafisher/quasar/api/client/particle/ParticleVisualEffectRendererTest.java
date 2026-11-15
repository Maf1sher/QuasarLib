package mafisher.quasar.api.client.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectMeshes;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.InstancedVisualEffectRenderer;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class ParticleVisualEffectRendererTest {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "test", "textures/effect/particle.png"
    );
    private static final EffectParticleField FIELD = EffectParticleField.builder()
        .appearance(new EffectParticleAppearance(TEXTURE, EffectParticleAppearance.Blend.ALPHA,
            new EffectParticleAppearance.SizeRange(1.0, 0.5), 0xFFFFFFFF, 0x00000000, 0.5))
        .build();

    @Test
    void supportsParticlesAndInstancedPassesTogether() {
        CombinedRenderer renderer = new CombinedRenderer();
        EffectRenderContext context = TestVisualEffects.context(
            TestVisualEffects.EFFECT_ID, UUID.randomUUID(), Vec3.ZERO, 1.0F, 100, 0.5F
        );

        assertFalse(renderer.particlePasses(context).isEmpty());
        assertFalse(renderer.instancedPasses(context).isEmpty());
        assertEquals(List.of(), renderer.renderPasses(context));
    }

    private static final class CombinedRenderer
        implements ParticleVisualEffectRenderer, InstancedVisualEffectRenderer {
        @Override
        public List<EffectParticlePass> particlePasses(EffectRenderContext context) {
            return List.of(new EffectParticlePass("particles", FIELD));
        }

        @Override
        public List<EffectInstancedPass> instancedPasses(EffectRenderContext context) {
            return List.of(new EffectInstancedPass("instances",
                QuasarRenderTypes.instancedWorldVisual(TEXTURE), EffectMeshes.horizontalQuad(),
                new Matrix4f(), 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F));
        }
    }
}
