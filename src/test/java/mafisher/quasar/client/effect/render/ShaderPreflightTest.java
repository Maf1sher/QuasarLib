package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.List;
import mafisher.quasar.api.client.render.EffectShader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShaderPreflightTest {
    @BeforeEach
    void clearBefore() {
        EffectShaderCatalog.clearForTests();
    }

    @AfterEach
    void clearAfter() {
        EffectShaderCatalog.clearForTests();
    }

    @Test
    void reportsAValidCatalog() {
        EffectShader good = EffectShader.create(id("good"), DefaultVertexFormat.BLOCK);
        EffectShaderCatalog.record(good);

        ShaderPreflight.Report report = ShaderPreflight.check(
            ResourceProvider.EMPTY, (shader, resources, failures) -> { }
        );

        assertEquals(1, report.checked());
        assertTrue(report.valid());
        assertTrue(report.chatLines().get(0).contains("Validated 1"));
    }

    @Test
    void reportsFailuresWithProgramStageAndImports() {
        EffectShader good = EffectShader.create(id("good"), DefaultVertexFormat.BLOCK);
        EffectShader bad = EffectShader.create(id("bad"), DefaultVertexFormat.BLOCK);
        EffectShaderCatalog.record(good);
        EffectShaderCatalog.record(bad);

        ShaderPreflight.Report report = ShaderPreflight.check(
            ResourceProvider.EMPTY,
            (shader, resources, failures) -> {
                if ("bad".equals(shader.id().getPath())) {
                    failures.add(new ShaderFailure(shader.id(), "fragment",
                        "quasar_test:shaders/core/bad.fsh", "test_pack",
                        List.of("quasar_test:shaders/include/module.glsl"),
                        "0(4): error: bad token", "full detail"));
                }
            }
        );

        assertFalse(report.valid());
        assertEquals(1, report.failures().size());
        String summary = report.chatLines().get(0);
        assertTrue(summary.contains("nothing was reloaded"));
        String line = report.chatLines().get(1);
        assertTrue(line.contains("quasar_test:bad"));
        assertTrue(line.contains("[fragment]"));
        assertTrue(line.contains("test_pack"));
        assertTrue(line.contains("module.glsl"));
    }

    @Test
    void capsChatFailuresAndPointsAtTheLog() {
        for (int index = 0; index < 3; index++) {
            EffectShaderCatalog.record(EffectShader.create(id("bad_" + index),
                DefaultVertexFormat.BLOCK));
        }

        ShaderPreflight.Report report = ShaderPreflight.check(
            ResourceProvider.EMPTY,
            (shader, resources, failures) -> failures.add(new ShaderFailure(shader.id(),
                "json", shader.id().toString(), "", List.of(), "error", "error"))
        );

        List<String> lines = report.chatLines(1);

        assertEquals(3, lines.size());
        assertTrue(lines.get(2).contains("2 more"));
    }

    @Test
    void containsValidatorCrashesAsPreflightFailures() {
        EffectShaderCatalog.record(EffectShader.create(id("crash"), DefaultVertexFormat.BLOCK));

        ShaderPreflight.Report report = ShaderPreflight.check(
            ResourceProvider.EMPTY,
            (shader, resources, failures) -> {
                throw new IllegalStateException("boom");
            }
        );

        assertFalse(report.valid());
        assertEquals("preflight", report.failures().get(0).stage());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("quasar_test", path);
    }
}
