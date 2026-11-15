package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.List;
import mafisher.quasar.api.client.render.EffectShader;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EffectShaderCatalogTest {
    @BeforeEach
    void clearBefore() {
        EffectShaderCatalog.clearForTests();
    }

    @AfterEach
    void clearAfter() {
        EffectShaderCatalog.clearForTests();
    }

    @Test
    void recordsHandlesAndBatchedVariantsInRegistrationOrder() {
        EffectShader normal = EffectShader.create(id("normal"), DefaultVertexFormat.BLOCK);
        EffectShader batched = EffectShader.createBatched(
            id("batched"), id("batched_instanced"), DefaultVertexFormat.BLOCK
        );

        EffectShaderCatalog.record(normal);
        EffectShaderCatalog.record(batched);

        assertEquals(List.of(normal, batched, batched.instancedVariant().orElseThrow()),
            EffectShaderCatalog.handles());
    }

    @Test
    void recordingTheSameHandleTwiceKeepsOneEntry() {
        EffectShader shader = EffectShader.create(id("once"), DefaultVertexFormat.BLOCK);

        EffectShaderCatalog.record(shader);
        EffectShaderCatalog.record(shader);

        assertEquals(List.of(shader), EffectShaderCatalog.handles());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("quasar_test", path);
    }
}
