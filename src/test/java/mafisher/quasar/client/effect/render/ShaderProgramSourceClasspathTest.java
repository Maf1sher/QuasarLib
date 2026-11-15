package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import mafisher.quasar.api.client.render.EffectShader;
import mafisher.quasar.api.client.screen.EmissiveComposite;
import mafisher.quasar.content.ContentShaders;
import mafisher.quasar.testing.TestResourceManager;
import org.junit.jupiter.api.Test;

class ShaderProgramSourceClasspathTest {
    @Test
    void loadsEveryShippedShaderProgram() {
        // instanced_translucent reuses minecraft:rendertype_translucent, which is not
        // part of the test classpath; the client resource manager supplies it in game.
        List<EffectShader> shaders = List.of(
            EffectShaders.INSTANCED_VISUAL,
            EffectShaders.MICRO_PARTICLES,
            EffectShaders.SCREEN_MASK,
            EffectShaders.SCREEN_FULLSCREEN_MASK,
            EffectShaders.SCREEN_COMPOSITE,
            EffectShaders.SCREEN_BLOOM,
            EffectShaders.SCREEN_FLASH,
            EffectShaders.SCREEN_PRESENT,
            EffectShaders.DEPTH_PROBE,
            EmissiveComposite.STANDARD_SHADER,
            ContentShaders.RUNE_CIRCLE,
            ContentShaders.RUNE_MASK,
            ContentShaders.RUNE_GLOW,
            ContentShaders.BLACK_HOLE,
            ContentShaders.BLACK_HOLE_MESH,
            ContentShaders.BLACK_HOLE_MESH_MASK
        );

        for (EffectShader shader : shaders) {
            assertDoesNotThrow(() -> ShaderProgramLoader.load(
                TestResourceManager.fromClasspath(), shader.id()
            ), "Could not preflight " + shader.id());
        }
    }
}
