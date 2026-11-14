package mafisher.quasar.api.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import mafisher.quasar.api.compat.FogPolicy;
import mafisher.quasar.shader.GlslConstants;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ScreenEffectMediumOpticsTest {
    private static final float CAMERA_Y = 64.0F;
    private static final ScreenEffectFogState FOG =
        new ScreenEffectFogState(2.0F, 32.0F, new Vec3(0.2, 0.4, 0.7));

    @Test
    void airPathHasNoPlaneOrFogEvenWithActiveWorldFog() {
        ScreenEffectMediumOptics optics = optics(ScreenEffectMedium.NONE,
            ScreenEffectMedium.NONE, ScreenEffectMediumPath.EMPTY, FOG);

        assertFalse(optics.surfaceActive());
        assertEquals(0.0F, optics.surfaceY());
        assertFalse(optics.suppressAboveWater());
        assertFalse(optics.fogActive());
        assertTrue(optics.screenWarpAllowed());
        assertEquals(0.0F, optics.fogDistance(100.0F));
        assertEquals(0.0F, optics.fogFactor(100.0F));
    }

    @Test
    void openTopCrossingBecomesACameraRelativePlane() {
        ScreenEffectMediumOptics optics = optics(ScreenEffectMedium.NONE,
            ScreenEffectMedium.NONE, openWaterPath(), FOG);

        assertTrue(optics.surfaceActive());
        assertEquals(-2.0F, optics.surfaceY(), 1.0E-6F);
        assertEquals(ScreenEffectMediumOptics.WATER_IOR, optics.waterIor());
        assertEquals(30.0F, optics.fogLength(), 1.0E-6F);
        assertTrue(optics.fogActive());
        assertFalse(optics.screenWarpAllowed());
    }

    @Test
    void boundedWaterBodyOnlyFogsAndTints() {
        ScreenEffectMediumPath path = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 10.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 10.0, 20.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 20.0, 40.0)
        ), List.of(
            new ScreenEffectMediumCrossing(10.0, ScreenEffectMedium.NONE,
                ScreenEffectMedium.WATER, 62.0F, true),
            new ScreenEffectMediumCrossing(20.0, ScreenEffectMedium.WATER,
                ScreenEffectMedium.NONE, 62.0F, true)
        ));

        ScreenEffectMediumOptics optics = optics(ScreenEffectMedium.NONE,
            ScreenEffectMedium.NONE, path, FOG);

        assertFalse(optics.surfaceActive());
        assertEquals(10.0F, optics.fogLength(), 1.0E-6F);
        assertEquals(10.0F, optics.fogDistance(100.0F), 1.0E-6F);
        assertTrue(optics.fogActive());
        assertFalse(optics.screenWarpAllowed());
    }

    @Test
    void sideEntryWaterIsNotAHorizontalPlane() {
        ScreenEffectMediumPath path = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 10.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 10.0, 40.0)
        ), List.of(
            new ScreenEffectMediumCrossing(10.0, ScreenEffectMedium.NONE,
                ScreenEffectMedium.WATER, 62.0F, false)
        ));

        ScreenEffectMediumOptics optics = optics(ScreenEffectMedium.NONE,
            ScreenEffectMedium.NONE, path, FOG);

        assertFalse(optics.surfaceActive());
        assertEquals(30.0F, optics.fogLength(), 1.0E-6F);
        assertFalse(optics.screenWarpAllowed());
    }

    @Test
    void aCrossingWithoutAFiniteSurfaceDoesNotCreateAPlane() {
        ScreenEffectMediumPath path = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 10.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 10.0, 40.0)
        ), List.of(
            new ScreenEffectMediumCrossing(10.0, ScreenEffectMedium.NONE,
                ScreenEffectMedium.WATER, Float.NaN, true)
        ));

        ScreenEffectMediumOptics optics = optics(ScreenEffectMedium.NONE,
            ScreenEffectMedium.NONE, path, FOG);

        assertFalse(optics.surfaceActive());
        assertEquals(30.0F, optics.fogLength(), 1.0E-6F);
        assertFalse(optics.screenWarpAllowed());
    }

    @Test
    void underwaterCameraSuppressesAnAboveWaterEffect() {
        ScreenEffectMediumPath path = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 0.0, 5.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 5.0, 40.0)
        ), List.of(
            new ScreenEffectMediumCrossing(5.0, ScreenEffectMedium.WATER,
                ScreenEffectMedium.NONE, 64.0F, true)
        ));

        ScreenEffectMediumOptics optics = optics(ScreenEffectMedium.WATER,
            ScreenEffectMedium.NONE, path, FOG);

        assertTrue(optics.suppressAboveWater());
        assertFalse(optics.surfaceActive());
        assertFalse(optics.screenWarpAllowed());
    }

    @Test
    void suppressionKeepsTheExistingWaterVersusNonWaterRule() {
        assertTrue(optics(ScreenEffectMedium.WATER, ScreenEffectMedium.LAVA,
            ScreenEffectMediumPath.EMPTY, FOG).suppressAboveWater());
        assertFalse(optics(ScreenEffectMedium.WATER, ScreenEffectMedium.WATER,
            ScreenEffectMediumPath.EMPTY, FOG).suppressAboveWater());
        assertFalse(optics(ScreenEffectMedium.NONE, ScreenEffectMedium.WATER,
            ScreenEffectMediumPath.EMPTY, FOG).suppressAboveWater());
    }

    @Test
    void submergedEffectStillFogsItsWholePathWithoutAPlane() {
        ScreenEffectMediumPath path = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 0.0, 40.0)
        ), List.of());

        ScreenEffectMediumOptics optics = optics(ScreenEffectMedium.WATER,
            ScreenEffectMedium.WATER, path, FOG);

        assertFalse(optics.surfaceActive());
        assertFalse(optics.suppressAboveWater());
        assertEquals(40.0F, optics.fogLength(), 1.0E-6F);
        assertTrue(optics.fogActive());
        assertFalse(optics.screenWarpAllowed());
    }

    @Test
    void fogPolicyControlsFogButNotMediumBlocking() {
        ScreenEffectMediumOptics respect = optics(ScreenEffectMedium.NONE,
            ScreenEffectMedium.NONE, openWaterPath(), FOG, FogPolicy.RESPECT);
        ScreenEffectMediumOptics emissive = optics(ScreenEffectMedium.NONE,
            ScreenEffectMedium.NONE, openWaterPath(), FOG, FogPolicy.EMISSIVE);
        ScreenEffectMediumOptics none = optics(ScreenEffectMedium.NONE,
            ScreenEffectMedium.NONE, openWaterPath(), FOG, FogPolicy.NONE);

        assertTrue(respect.fogActive());
        assertTrue(respect.fogFactor(10.0F) > 0.0F);
        assertFalse(emissive.fogActive());
        assertEquals(0.0F, emissive.fogFactor(10.0F));
        assertFalse(none.fogActive());
        assertEquals(0.0F, none.fogFactor(10.0F));
        assertFalse(emissive.screenWarpAllowed());
        assertEquals(respect.fogLength(), emissive.fogLength(), 0.0F);
    }

    @Test
    void fogDistanceUsesEverySubmergedLeg() {
        ScreenEffectMediumPath lake = new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 20.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 20.0, 30.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 30.0, 100.0)
        ), List.of());

        ScreenEffectMediumOptics optics = optics(ScreenEffectMedium.NONE,
            ScreenEffectMedium.NONE, lake, FOG);

        assertEquals(10.0F, optics.fogLength(), 1.0E-6F);
        assertEquals(10.0F, optics.fogDistance(100.0F), 1.0E-6F);
    }

    @Test
    void fogFallsBackToTheTracedEntryWhenThePathIsEmpty() {
        ScreenEffectMediumOptics optics = optics(ScreenEffectMedium.POWDER_SNOW,
            ScreenEffectMedium.NONE, ScreenEffectMediumPath.EMPTY, FOG);

        assertEquals(0.0F, optics.fogLength());
        assertEquals(12.0F, optics.fogDistance(12.0F), 1.0E-6F);
        assertTrue(optics.fogActive());
        assertFalse(optics.screenWarpAllowed());
    }

    @Test
    void fresnelIsWeakAtNormalIncidenceAndOpaqueAtTheCriticalAngle() {
        ScreenEffectMediumOptics aboveWater = optics(ScreenEffectMedium.NONE,
            ScreenEffectMedium.NONE, openWaterPath(), FOG);
        ScreenEffectMediumOptics underwater = optics(ScreenEffectMedium.WATER,
            ScreenEffectMedium.WATER, new ScreenEffectMediumPath(List.of(
                new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 0.0, 10.0),
                new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 10.0, 20.0)
            ), List.of(
                new ScreenEffectMediumCrossing(10.0, ScreenEffectMedium.WATER,
                    ScreenEffectMedium.NONE, 64.0F, true)
            )), FOG);

        float normal = aboveWater.fresnelReflectance(1.0F);
        assertTrue(normal > 0.01F && normal < 0.03F,
            "normal water reflection must stay near 2%, got " + normal);

        float criticalCosine = (float)Math.sqrt(1.0
            - 1.0 / (ScreenEffectMediumOptics.WATER_IOR
                * ScreenEffectMediumOptics.WATER_IOR));
        float critical = underwater.fresnelReflectance(criticalCosine);
        assertTrue(critical > 0.99F,
            "Fresnel must approach total reflection at the critical angle, got " + critical);
    }

    @Test
    void totalInternalReflectionBlocksTransmission() {
        ScreenEffectMediumOptics optics = optics(ScreenEffectMedium.WATER,
            ScreenEffectMedium.WATER, new ScreenEffectMediumPath(List.of(
                new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 0.0, 10.0),
                new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 10.0, 20.0)
            ), List.of(
                new ScreenEffectMediumCrossing(10.0, ScreenEffectMedium.WATER,
                    ScreenEffectMedium.NONE, 64.0F, true)
            )), FOG);

        assertTrue(optics.surfaceActive());
        assertTrue(optics.totalInternalReflection(0.2F));
        assertEquals(0.0F, optics.surfaceTransmittance(0.2F), 1.0E-6F);
        assertEquals(1.0F, optics.fresnelReflectance(0.2F), 1.0E-6F);
    }

    @Test
    void anInactiveSurfacePassesLightAndDoesNotReflect() {
        ScreenEffectMediumOptics optics = optics(ScreenEffectMedium.NONE,
            ScreenEffectMedium.NONE, ScreenEffectMediumPath.EMPTY, FOG);

        assertFalse(optics.totalInternalReflection(0.1F));
        assertEquals(1.0F, optics.surfaceTransmittance(0.1F));
        assertEquals(0.0F, optics.fresnelReflectance(0.1F));
    }

    @Test
    void bindSetsTheStandardMediumUniforms() {
        ScreenEffectOperationContext context = mock(ScreenEffectOperationContext.class);
        ShaderInstance shader = mock(ShaderInstance.class);
        ScreenEffectMediumOptics optics = optics(ScreenEffectMedium.NONE,
            ScreenEffectMedium.NONE, openWaterPath(), FOG);

        optics.bind(context, shader);

        verify(context).setUniform(shader, "MediumSurfaceActive", 1.0F);
        verify(context).setUniform(shader, "MediumSurfaceY", -2.0F);
        verify(context).setUniform(shader, "MediumWaterIor",
            ScreenEffectMediumOptics.WATER_IOR);
        verify(context).setUniform(shader, "MediumSuppressAboveWater", 0.0F);
        verify(context).setUniform(shader, "MediumPathLength", 30.0F);
        verify(context).setUniform(shader, "MediumCameraSubmerged", 0.0F);
        verify(context).setUniform(shader, "MediumFogStart", 2.0F);
        verify(context).setUniform(shader, "MediumFogEnd", 32.0F);
        verify(context).setUniform(shader, "MediumFogColor", 0.2F, 0.4F, 0.7F);
        verify(context).setUniform(shader, "MediumFogActive", 1.0F);
    }

    @Test
    void cpuConstantsMatchTheShippedShaderInclude() {
        Map<String, Double> constants = GlslConstants.load(
            "quasar_lib:shaders/include/medium_optics.glsl");

        assertEquals(GlslConstants.require(constants, "QUASAR_MEDIUM_WATER_IOR"),
            ScreenEffectMediumOptics.WATER_IOR, 1.0E-6);
        assertEquals(GlslConstants.require(constants, "QUASAR_MEDIUM_FRESNEL_FLOOR"),
            ScreenEffectMediumOptics.FRESNEL_FLOOR, 1.0E-9);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(NullPointerException.class, () -> ScreenEffectMediumOptics.from(null));
        assertThrows(IllegalArgumentException.class,
            () -> ScreenEffectMediumOptics.fresnelReflectance(Float.NaN, 1.0F, 1.333F));
        assertThrows(IllegalArgumentException.class,
            () -> ScreenEffectMediumOptics.fresnelReflectance(1.0F, 0.0F, 1.333F));
        assertThrows(IllegalArgumentException.class,
            () -> ScreenEffectMediumOptics.fresnelReflectance(1.0F, 1.0F, -1.333F));
    }

    private static ScreenEffectMediumOptics optics(ScreenEffectMedium cameraMedium,
        ScreenEffectMedium effectMedium, ScreenEffectMediumPath path,
        ScreenEffectFogState fog) {
        return optics(cameraMedium, effectMedium, path, fog, FogPolicy.RESPECT);
    }

    private static ScreenEffectMediumOptics optics(ScreenEffectMedium cameraMedium,
        ScreenEffectMedium effectMedium, ScreenEffectMediumPath path,
        ScreenEffectFogState fog, FogPolicy policy) {
        return ScreenEffectMediumOptics.create(new ScreenEffectMediumState(cameraMedium,
            effectMedium, Float.NaN, Float.NaN), path, fog, policy, CAMERA_Y);
    }

    private static ScreenEffectMediumPath openWaterPath() {
        return new ScreenEffectMediumPath(List.of(
            new ScreenEffectMediumSpan(ScreenEffectMedium.NONE, 0.0, 10.0),
            new ScreenEffectMediumSpan(ScreenEffectMedium.WATER, 10.0, 40.0)
        ), List.of(
            new ScreenEffectMediumCrossing(10.0, ScreenEffectMedium.NONE,
                ScreenEffectMedium.WATER, 62.0F, true)
        ));
    }
}
