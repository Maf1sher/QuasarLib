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
            "SceneSampler", "RawSceneSampler", "OpaqueColorSampler", "ForegroundDepthSampler",
            "MaskSampler", "DepthSampler", "EffectSampler",
            "BloomSampler",
            "ViewMat", "WorldProjMat", "InvProjMat", "InvViewMat",
            "ViewportWidth", "ViewportHeight", "MaskStrength",
            "BlackHoleOffset", "BlackHoleRadius", "LensRadius", "LensWarpRadius",
            "DiskInnerRadius", "DiskOuterRadius",
            "DiskThickness", "DiskRight", "DiskForward", "DiskNormal",
            "AgeTicks", "Progress", "Seed", "Charge", "Fade", "RingFormation", "LensStrength",
            "ShockRadius", "ShockStrength", "ShockAbsorb", "CoreFlash", "FlashRadius",
            "OutroJet", "VortexStrength", "VortexRadius", "Darkness",
            "RingStrength", "DiskBrightness", "Turbulence", "StepCount",
            "MediumFogStart", "MediumFogEnd", "MediumFogColor", "MediumFogActive", "CameraSubmerged", "EffectSubmerged",
            "MediumPathLength", "ForegroundDepthValid", "OpaqueColorValid", "SurfaceY", "SurfaceActive", "SuppressAboveWater",
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
    void blackHoleShaderDeclaresCinematicInputs() throws IOException {
        String shader = readAsset("assets/quasar_content/shaders/core/black_hole.fsh");

        assertTrue(shader.contains("CoreFlash"),
            "ignition and implosion need an emissive core flash");
        assertTrue(shader.contains("FlashRadius"),
            "the flash size must not collapse with the horizon");
        assertTrue(shader.contains("ShockRadius") && shader.contains("ShockStrength"),
            "the intro and outro need an impact-space shockwave ring");
        assertTrue(shader.contains("ShockAbsorb"),
            "the shockwave must be able to absorb light instead of only adding it");
        assertTrue(shader.contains("VortexStrength") && shader.contains("VortexRadius")
            && shader.contains("projectPoint"),
            "the pre-ignition space vortex must warp the lensed background");
        assertTrue(shader.contains("Darkness"),
            "the ignition and implosion must be able to dim the screen");
        assertTrue(shader.contains("RingFormation") && shader.contains("density *= wrap"),
            "the accretion ring must form after the horizon instead of appearing with it");
        assertTrue(shader.contains("vec2 sceneUv")
            && shader.contains("texture(SceneSampler, sceneUv)"),
            "the screen-wide vortex must warp the scene outside the lens footprint");
        assertTrue(shader.contains("OutroJet"),
            "the collapse needs polar jets");
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
        assertTrue(shader.contains("closestEntry") && shader.contains("nearestEntry"),
            "the effect surface must be the nearest traced point, not a bounding volume");
        assertTrue(shader.contains("nearestDiskEntry"),
            "the disk must contribute its own traced crossing depth");
        assertTrue(shader.contains("encodeEffectDepth") && shader.contains("decodeEffectDepth"),
            "the raymarch must carry the effect entry into the composite pass");
        assertTrue(shader.contains("sceneDistance"),
            "the scene depth must participate in visibility");
        assertTrue(!shader.contains("shadowRadius"),
            "a shadow bounding sphere sits in front of the horizon and covers entities");
        assertTrue(!shader.contains("effectEntry = tEntry"),
            "the lens-shell entry cannot stand in for the hole surface");
        assertTrue(!shader.contains("centerSceneDistance"),
            "a single centre sample cannot represent partial foreground occlusion");
        assertTrue(!shader.contains("sampleDistance < sceneDistance"),
            "per-sample unlensed depth clips the bent light path at the horizon");
        assertTrue(!shader.contains("maxLensDelta"),
            "the lens warp must not be clamped to a tiny screen-space delta");
    }

    @Test
    void blackHoleWaterOpticsUseOneFrameAndHideTotalInternalReflection() throws IOException {
        String shader = readAsset("assets/quasar_content/shaders/core/black_hole.fsh");

        assertTrue(shader.contains("vec3 hit = origin + direction * surfaceDistance;"),
            "the surface hit must combine the hole-relative camera origin with the ray");
        assertTrue(shader.contains("waterSurfaceNormal(hit)"),
            "the ripple phase must use the hole-relative hit, not a camera-relative one");
        assertTrue(shader.contains("totalInternalReflection"),
            "TIR must hide the above-water effect instead of tracing the straight ray");
        assertTrue(shader.contains("fresnelWater"),
            "the surface must transmit physically instead of applying a fixed blue tint");
        assertTrue(!shader.contains("vec3 surfaceColor"),
            "the old fixed surface tint produced the floating reflection-like patch");
        assertTrue(shader.contains("bloomFade") && shader.contains("surfaceTransmittance"),
            "the emissive bloom must fade with the surface and the medium fog");
        assertTrue(shader.contains("ray.surfaceDistance <= 0.0"),
            "the screen-space lens and vortex must be skipped across a water surface");
        assertTrue(shader.contains("SuppressAboveWater"),
            "an above-water effect must be suppressed when the camera is submerged");
        assertTrue(shader.contains("CameraSubmerged > 0.5")
                && shader.contains("CameraSubmerged < 0.5"),
            "the screen-space lens and vortex must also be skipped from inside water");
        assertTrue(shader.contains("MediumPathLength"),
            "fog must follow the whole medium path, not only the endpoint blocks");
    }

    @Test
    void blackHoleGatesWarpAndTintsThroughForegroundTranslucency() throws IOException {
        String shader = readAsset("assets/quasar_content/shaders/core/black_hole.fsh");

        assertTrue(shader.contains("ForegroundDepthSampler"),
            "the lens must see glass and water that the opaque depth copy misses");
        assertTrue(shader.contains("sourceForeground") && shader.contains("swirlForeground"),
            "both the lens source and the vortex source must be gated");
        assertTrue(shader.contains("OpaqueColorSampler") && shader.contains("foregroundTint"),
            "colored glass must tint the effect instead of hard-occluding it");
        assertTrue(shader.contains("OpaqueColorValid") && shader.contains("ForegroundDepthValid"),
            "the tint must fall back when the foreground copies are unavailable");
        assertTrue(shader.contains("foregroundTint = ratio * 0.80"),
            "a detected translucent layer must always attenuate bright glass");
        assertTrue(shader.contains("foregroundTint = vec3(0.85)"),
            "water on the camera-to-effect line must attenuate even without a foreground copy");
        assertTrue(shader.contains("foregroundInFront"),
            "the tint must only apply when the translucent layer is in front");
        assertTrue(shader.contains("currentForeground") && shader.contains("foregroundLayer"),
            "the destination pixel and the color delta must also reject lens warps");
        assertTrue(shader.contains("MediumPathLength > 0.0")
                && shader.contains("MediumPathLength <= 0.0"),
            "water on the camera-to-effect line must disable the lens and vortex");
    }

    @Test
    void blackHoleLensSamplesTheAccumulatedComposition() throws IOException {
        String shader = readAsset("assets/quasar_content/shaders/core/black_hole.fsh");
        assertTrue(shader.contains("texture(SceneSampler, sourceUv)"),
            "the lens must bend the accumulated image so stacked effects stay visible");
        assertTrue(!shader.contains("texture(RawSceneSampler, sourceUv)"),
            "sampling the pristine frame erases other screen effects inside the lens footprint");
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
