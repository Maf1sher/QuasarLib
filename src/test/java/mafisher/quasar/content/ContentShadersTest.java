package mafisher.quasar.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ContentShadersTest {
    private static final List<String> SHADER_ASSETS = List.of(
        "shaders/core/rune_circle.json",
        "shaders/core/rune_circle.vsh",
        "shaders/core/rune_circle.fsh",
        "shaders/core/rune_mask.json",
        "shaders/core/rune_mask.vsh",
        "shaders/core/rune_mask.fsh",
        "shaders/core/rune_glow.json",
        "shaders/core/rune_glow.vsh",
        "shaders/core/rune_glow.fsh",
        "shaders/core/black_hole.json",
        "shaders/core/black_hole.vsh",
        "shaders/core/black_hole.fsh",
        "shaders/core/black_hole_mask.json",
        "shaders/core/black_hole_mask.vsh",
        "shaders/core/black_hole_mask.fsh"
    );

    @Test
    void exposesContentOwnedShaderIds() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "rune_circle"),
            ContentShaders.RUNE_CIRCLE.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "rune_mask"),
            ContentShaders.RUNE_MASK.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "rune_glow"),
            ContentShaders.RUNE_GLOW.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "black_hole"),
            ContentShaders.BLACK_HOLE.id());
        assertEquals(ResourceLocation.fromNamespaceAndPath("quasar_content", "black_hole_mask"),
            ContentShaders.BLACK_HOLE_MASK.id());
        assertEquals(DefaultVertexFormat.BLOCK, ContentShaders.RUNE_CIRCLE.format());
        assertEquals(DefaultVertexFormat.BLOCK, ContentShaders.RUNE_MASK.format());
        assertEquals(DefaultVertexFormat.POSITION, ContentShaders.RUNE_GLOW.format());
        assertEquals(DefaultVertexFormat.POSITION, ContentShaders.BLACK_HOLE.format());
        assertEquals(DefaultVertexFormat.BLOCK, ContentShaders.BLACK_HOLE_MASK.format());
    }

    @Test
    void shipsAllShaderAssets() throws IOException {
        for (String asset : SHADER_ASSETS) {
            String path = "assets/quasar_content/shaders/core/" + asset.substring("shaders/core/".length());
            assertNotNull(getClass().getClassLoader().getResource(path), path);
        }
    }

    @Test
    void blackHoleShaderDeclaresEveryOperationInput() throws IOException {
        String json = readAsset("assets/quasar_content/shaders/core/black_hole.json");
        for (String name : List.of(
            "SceneSampler", "RawSceneSampler", "MaskSampler", "DepthSampler", "EffectSampler",
            "BloomSampler",
            "ViewMat", "WorldProjMat", "InvProjMat", "InvViewMat",
            "ViewportWidth", "ViewportHeight", "MaskStrength",
            "BlackHoleOffset", "BlackHoleRadius", "LensRadius", "LensWarpRadius",
            "DiskInnerRadius", "DiskOuterRadius",
            "DiskThickness", "DiskRight", "DiskForward", "DiskNormal",
            "AgeTicks", "Progress", "Seed", "Charge", "Fade", "LensStrength",
            "RingStrength", "DiskBrightness", "Turbulence", "StepCount",
            "MediumFogStart", "MediumFogEnd", "MediumFogColor", "MediumFogActive", "CameraSubmerged", "EffectSubmerged",
            "SurfaceY", "SurfaceActive",
            "Pass", "EmissiveOnly", "BloomStrength", "BloomThreshold", "BloomRadiusPixels"
        )) {
            assertTrue(json.contains("\"" + name + "\""), name);
        }
    }

    @Test
    void blackHoleShaderDoesNotUseVanillaFog() throws IOException {
        String shader = readAsset("assets/quasar_content/shaders/core/black_hole.fsh");
        assertTrue(!shader.contains("linear_fog"), "black-hole emission must punch through vanilla fog");
    }

    @Test
    void blackHoleRayReconstructionSamplesTheFarPlane() throws IOException {
        String shader = readAsset("assets/quasar_content/shaders/core/black_hole.fsh");
        assertTrue(shader.contains("vec4 clip = vec4(ndc, 1.0, 1.0);"),
            "near-plane rays amplify the view-bob translation baked into the projection");
    }

    @Test
    void blackHoleMaskIsScreenSpaceCoverage() throws IOException {
        String shader = readAsset("assets/quasar_content/shaders/core/black_hole_mask.vsh");
        assertTrue(shader.contains("gl_Position = vec4(UV0.xy * 2.0 - 1.0, 0.0, 1.0);"),
            "a world-space mask can clip the screen-space effect");
    }

    @Test
    void blackHoleVisibilityUsesPerPixelEffectDepth() throws IOException {
        String shader = readAsset("assets/quasar_content/shaders/core/black_hole.fsh");
        assertTrue(shader.contains("lensEntry") || shader.contains("tEntry"),
            "the effect surface must be depth-tested against the scene");
        assertTrue(shader.contains("shadowEntry") && shader.contains("diskEntry"),
            "the ring and disk need their own effect depths");
        assertTrue(shader.contains("sceneDistance"),
            "the scene depth must participate in visibility");
        assertTrue(!shader.contains("centerSceneDistance"),
            "a single centre sample cannot represent partial foreground occlusion");
        assertTrue(!shader.contains("sampleDistance < sceneDistance"),
            "per-sample unlensed depth clips the bent light path at the horizon");
        assertTrue(!shader.contains("maxLensDelta"),
            "the lens warp must not be clamped to a tiny screen-space delta");
    }

    @Test
    void blackHoleUsesCriticalImpactAndActiveMediumInputs() throws IOException {
        String shader = readAsset("assets/quasar_content/shaders/core/black_hole.fsh");
        assertTrue(shader.contains("B_CRITICAL"),
            "the shadow radius must follow the Schwarzschild critical impact parameter");
        assertTrue(shader.contains("LensWarpRadius") && shader.contains("warpFalloff"),
            "the background lens must fade inside a tunable footprint");
        assertTrue(shader.contains("MediumFogStart") && shader.contains("MediumFogEnd")
            && shader.contains("MediumFogColor") && shader.contains("MediumFogActive"));
        assertTrue(shader.contains("CameraSubmerged") && shader.contains("EffectSubmerged"));
        assertTrue(shader.contains("SurfaceY") && shader.contains("SurfaceActive"));
        assertTrue(shader.contains("BloomSampler") && shader.contains("EmissiveOnly"),
            "bloom must sample the emissive layer instead of the lensed background");
        assertTrue(shader.contains("innerOcclusion"),
            "the shadow-to-ring gap must not show the lensed background");
    }

    @Test
    void worldShaderDeclaresInstanceArrays() throws IOException {
        String source = readAsset("assets/quasar_content/shaders/core/rune_circle.vsh");
        assertTrue(source.contains("Instances[128]"), source);
        assertTrue(source.contains("InstanceColors[128]"), source);
        assertTrue(source.contains("InstanceData[128]"), source);
    }

    private static String readAsset(String path) throws IOException {
        try (InputStream stream = ContentShadersTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
