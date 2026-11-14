package mafisher.quasar.api.client.screen;

/**
 * One depth layer of a screen-space effect.
 *
 * <p>A layered effect does not have one depth: an additive glow, a solid rim, a
 * disk body, and a background lens each sit at a different camera distance.
 * Effects encode and test the owning layer per pixel with
 * {@link ScreenEffectVisibility} instead of hiding the whole effect from one
 * projected centre or one shared depth.</p>
 *
 * <p>Translucent foreground (glass, water, ice) only hard-gates
 * {@link #LENS}. Every other layer stays visible through it and is attenuated
 * by the shader tint, because a translucent surface must not erase the effect
 * behind it.</p>
 */
public enum ScreenEffectLayer {
    /** Additive glow or light emitted by the effect; never occludes the scene. */
    EMISSION(2.0F),
    /** Solid silhouette or rim, such as a photon ring. */
    EDGE(2.0F),
    /** Solid surface body, such as an accretion disk or core. */
    FILL(2.0F),
    /** Background lens or vortex sample that must not read through a medium. */
    LENS(2.0F),
    /** Dark occlusion or shadow that replaces the background where it wins. */
    SHADOW(2.0F);

    private final float rangeFactor;

    ScreenEffectLayer(float rangeFactor) {
        this.rangeFactor = rangeFactor;
    }

    /**
     * Returns the encoded-depth range multiplier for this layer.
     *
     * <p>All layers currently share the same range as the former black-hole
     * depth codec. Keeping the factor per layer means a later layer can widen
     * its range without changing the public call sites.</p>
     */
    float rangeFactor() {
        return this.rangeFactor;
    }
}
