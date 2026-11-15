package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EffectShadersTest {
    @Test
    void registersScreenPresent() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_lib", "screen_present"),
            EffectShaders.SCREEN_PRESENT.id());
        assertEquals(DefaultVertexFormat.POSITION, EffectShaders.SCREEN_PRESENT.format());
    }

    @Test
    void registersMicroParticles() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_lib", "micro_particles"),
            EffectShaders.MICRO_PARTICLES.id());
        assertEquals(DefaultVertexFormat.BLOCK, EffectShaders.MICRO_PARTICLES.format());
    }
}
