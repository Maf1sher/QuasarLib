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
}
