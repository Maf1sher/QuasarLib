package mafisher.quasar.api.client.screen;

import java.util.Objects;
import mafisher.quasar.api.compat.FogPolicy;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * Camera-relative water-medium optics for a screen effect.
 *
 * <p>The CPU accessors mirror {@code quasar_lib:medium_optics.glsl}. Build one
 * snapshot per effect and frame with {@link #from(ScreenEffectOperationContext)},
 * then use {@link #bind(ScreenEffectOperationContext, ShaderInstance)} before
 * drawing.</p>
 *
 * <p>Only an open top-surface water crossing drives refraction. A bounded water
 * body between two dry endpoints, or a side/bottom fluid crossing, contributes
 * fog and tint but no horizontal plane. {@code surfaceY} is relative to the
 * camera and finite when {@link #surfaceActive()} is true. A camera under water
 * looking at an above-water effect is suppressed instead of being drawn as a
 * floating reflection.</p>
 *
 * <p>Fog applies to every emission layer, including bloom, and never to the
 * re-added background. {@link FogPolicy#RESPECT} is required for fog; the other
 * policies keep the effect emissive. Medium crossings still block screen-space
 * lens and vortex work regardless of the fog policy.</p>
 */
@SuppressWarnings({"PMD.TooManyMethods", "PMD.GodClass"})
public final class ScreenEffectMediumOptics {
    /** Refractive index of water used by the shared CPU and GLSL helpers. */
    public static final float WATER_IOR = 1.333F;
    /** Denominator floor for Fresnel reflection; mirrors the shader include. */
    static final float FRESNEL_FLOOR = 0.0001F;

    private final ScreenEffectMediumState medium;
    private final ScreenEffectFogState fog;
    private final FogPolicy fogPolicy;
    private final boolean surfaceActive;
    private final float surfaceY;
    private final float waterIor;
    private final float fogLength;

    private ScreenEffectMediumOptics(ScreenEffectMediumState medium, ScreenEffectFogState fog,
        FogPolicy fogPolicy, boolean surfaceActive, float surfaceY, float waterIor,
        float fogLength) {
        this.medium = Objects.requireNonNull(medium, "medium");
        this.fog = Objects.requireNonNull(fog, "fog");
        this.fogPolicy = Objects.requireNonNull(fogPolicy, "fogPolicy");
        if (!Float.isFinite(waterIor) || waterIor <= 1.0F) {
            throw new IllegalArgumentException("Water IOR must be finite and greater than one");
        }
        if (!Float.isFinite(fogLength) || fogLength < 0.0F) {
            throw new IllegalArgumentException("Medium path length must be finite and non-negative");
        }
        if (surfaceActive && !Float.isFinite(surfaceY)) {
            throw new IllegalArgumentException("An active medium surface must be finite");
        }
        this.surfaceActive = surfaceActive;
        this.surfaceY = surfaceActive ? surfaceY : 0.0F;
        this.waterIor = waterIor;
        this.fogLength = fogLength;
    }

    /** Builds the medium snapshot for the current screen-operation frame. */
    public static ScreenEffectMediumOptics from(ScreenEffectOperationContext context) {
        Objects.requireNonNull(context, "context");
        return create(context.medium(), context.mediumPath(), context.fogState(),
            context.environment().fogPolicy(), context.camera().position().y);
    }

    /** Package-private seam for headless tests without a live render context. */
    static ScreenEffectMediumOptics create(ScreenEffectMediumState medium,
        ScreenEffectMediumPath path, ScreenEffectFogState fog, FogPolicy fogPolicy,
        double cameraY) {
        Objects.requireNonNull(medium, "medium");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(fog, "fog");
        Objects.requireNonNull(fogPolicy, "fogPolicy");

        boolean suppressAboveWater = suppressAboveWater(medium);
        boolean active = false;
        float surfaceY = 0.0F;
        if (!suppressAboveWater) {
            ScreenEffectMediumCrossing crossing = path.firstOpenWaterCrossing().orElse(null);
            if (crossing != null && Float.isFinite(crossing.surfaceY())) {
                double relative = (double)crossing.surfaceY() - cameraY;
                if (Double.isFinite(relative)) {
                    float value = (float)relative;
                    if (Float.isFinite(value)) {
                        active = true;
                        surfaceY = value;
                    }
                }
            }
        }
        return new ScreenEffectMediumOptics(medium, fog, fogPolicy, active, surfaceY,
            WATER_IOR, (float)path.submergedLength());
    }

    /** Returns whether an open top-surface water crossing supplies the refraction plane. */
    public boolean surfaceActive() {
        return this.surfaceActive;
    }

    /** Returns the camera-relative water plane, or {@code 0} when no plane is active. */
    public float surfaceY() {
        return this.surfaceY;
    }

    /** Returns whether an above-water effect must be hidden from an underwater camera. */
    public boolean suppressAboveWater() {
        return suppressAboveWater(this.medium);
    }

    /** Returns the water refractive index used by Fresnel and refraction. */
    public float waterIor() {
        return this.waterIor;
    }

    /** Returns the total submerged length along the camera-to-effect line. */
    public float fogLength() {
        return this.fogLength;
    }

    /**
     * Returns whether fog is active for this frame.
     *
     * <p>An air effect with no medium crossing is never fogged by world fog; a
     * bounded water body between two dry endpoints still fogs and tints.</p>
     */
    public boolean fogActive() {
        return this.fogPolicy == FogPolicy.RESPECT && this.fog.active()
            && hasMediumInteraction();
    }

    /**
     * Returns whether lens and vortex screen work may sample the scene.
     *
     * <p>Any medium crossing, a submerged camera, an active surface, or above-water
     * suppression disables the straight-ray warp. Fog policy is deliberately not
     * part of this test.</p>
     */
    public boolean screenWarpAllowed() {
        return !this.surfaceActive && !this.medium.cameraSubmerged()
            && this.fogLength <= 0.0F && !suppressAboveWater();
    }

    /**
     * Returns the fog distance: the whole submerged path, or {@code fallbackDistance}
     * when the path is empty but an endpoint is submerged.
     */
    public float fogDistance(float fallbackDistance) {
        if (!hasMediumInteraction()) {
            return 0.0F;
        }
        if (this.fogLength > 0.0F) {
            return this.fogLength;
        }
        return Float.isFinite(fallbackDistance) ? Math.max(fallbackDistance, 0.0F) : 0.0F;
    }

    /** Returns the smooth {@code 0..1} fog factor for {@code distance}. */
    public float fogFactor(float distance) {
        return fogActive() ? this.fog.factor(distance) : 0.0F;
    }

    /** Returns Fresnel reflectance for the camera side of the active surface. */
    public float fresnelReflectance(float cosIncidence) {
        if (!this.surfaceActive) {
            return 0.0F;
        }
        float incident = this.medium.cameraSubmerged() ? this.waterIor : 1.0F;
        float transmitted = this.medium.cameraSubmerged() ? 1.0F : this.waterIor;
        return fresnelReflectance(cosIncidence, incident, transmitted);
    }

    /** Returns whether the active surface totally internally reflects the camera ray. */
    public boolean totalInternalReflection(float cosIncidence) {
        if (!this.surfaceActive) {
            return false;
        }
        float incident = this.medium.cameraSubmerged() ? this.waterIor : 1.0F;
        float transmitted = this.medium.cameraSubmerged() ? 1.0F : this.waterIor;
        return sinTransmittedSquared(cosIncidence, incident, transmitted) > 1.0F;
    }

    /**
     * Returns surface transmission: {@code 1} without a surface, {@code 0} on total
     * internal reflection, otherwise {@code 1 - Fresnel}.
     */
    public float surfaceTransmittance(float cosIncidence) {
        if (!this.surfaceActive) {
            return 1.0F;
        }
        return 1.0F - fresnelReflectance(cosIncidence);
    }

    /** Binds the standard medium uniform group, failing on an undeclared uniform. */
    public void bind(ScreenEffectOperationContext context, ShaderInstance shader) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(shader, "shader");
        context.setUniform(shader, "MediumSurfaceActive", this.surfaceActive ? 1.0F : 0.0F);
        context.setUniform(shader, "MediumSurfaceY", this.surfaceY);
        context.setUniform(shader, "MediumWaterIor", this.waterIor);
        context.setUniform(shader, "MediumSuppressAboveWater",
            suppressAboveWater() ? 1.0F : 0.0F);
        context.setUniform(shader, "MediumPathLength", this.fogLength);
        context.setUniform(shader, "MediumCameraSubmerged",
            this.medium.cameraSubmerged() ? 1.0F : 0.0F);
        context.setUniform(shader, "MediumFogStart", this.fog.start());
        context.setUniform(shader, "MediumFogEnd", this.fog.end());
        context.setUniform(shader, "MediumFogColor", (float)this.fog.color().x,
            (float)this.fog.color().y, (float)this.fog.color().z);
        context.setUniform(shader, "MediumFogActive", fogActive() ? 1.0F : 0.0F);
    }

    /**
     * Unpolarised Fresnel reflectance between two refractive indices.
     *
     * @param cosIncidence absolute cosine of the incidence angle
     * @param incidentIor refractive index on the camera side
     * @param transmittedIor refractive index on the far side
     */
    public static float fresnelReflectance(float cosIncidence, float incidentIor,
        float transmittedIor) {
        if (!Float.isFinite(cosIncidence)) {
            throw new IllegalArgumentException("Incidence cosine must be finite");
        }
        if (!Float.isFinite(incidentIor) || incidentIor <= 0.0F
            || !Float.isFinite(transmittedIor) || transmittedIor <= 0.0F) {
            throw new IllegalArgumentException("Refractive indices must be finite and positive");
        }
        float cosI = Math.max(0.0F, Math.min(1.0F, Math.abs(cosIncidence)));
        float sinT2 = sinTransmittedSquared(cosI, incidentIor, transmittedIor);
        if (sinT2 >= 1.0F) {
            return 1.0F;
        }
        float cosT = (float)Math.sqrt(1.0F - sinT2);
        float rs = (incidentIor * cosI - transmittedIor * cosT)
            / Math.max(incidentIor * cosI + transmittedIor * cosT, FRESNEL_FLOOR);
        float rp = (incidentIor * cosT - transmittedIor * cosI)
            / Math.max(incidentIor * cosT + transmittedIor * cosI, FRESNEL_FLOOR);
        return Math.max(0.0F, Math.min(1.0F, 0.5F * (rs * rs + rp * rp)));
    }

    private boolean hasMediumInteraction() {
        return this.fogLength > 0.0F || this.medium.cameraSubmerged()
            || this.medium.effectSubmerged();
    }

    private static boolean suppressAboveWater(ScreenEffectMediumState medium) {
        return medium.cameraMedium() == ScreenEffectMedium.WATER
            && medium.effectMedium() != ScreenEffectMedium.WATER;
    }

    private static float sinTransmittedSquared(float cosIncidence, float incidentIor,
        float transmittedIor) {
        float cosI = Math.max(0.0F, Math.min(1.0F, Math.abs(cosIncidence)));
        return (incidentIor * incidentIor) / (transmittedIor * transmittedIor)
            * (1.0F - cosI * cosI);
    }
}
