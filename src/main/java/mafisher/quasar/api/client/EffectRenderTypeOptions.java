package mafisher.quasar.api.client;

import java.util.Objects;

/**
 * Immutable render-state options for a shader-backed effect render type.
 *
 * <p>Use a preset for the common combinations or construct options directly for
 * a custom material. The world and mask factories in {@link QuasarRenderTypes}
 * pin the output target; these options control the remaining state.</p>
 */
public record EffectRenderTypeOptions(
    Transparency transparency,
    boolean depthTest,
    boolean writeColor,
    boolean writeDepth,
    boolean cull,
    boolean lightmap,
    boolean sort
) {
    /** Translucent, depth-tested, culled, lightmapped, colour-only world visuals. */
    public static final EffectRenderTypeOptions WORLD_DEFAULT = new EffectRenderTypeOptions(
        Transparency.TRANSLUCENT, true, true, false, true, true, true
    );

    /** Opaque, depth-testing and depth-writing, unculled world geometry. */
    public static final EffectRenderTypeOptions WORLD_DEPTH_WRITING = new EffectRenderTypeOptions(
        Transparency.NONE, true, true, true, false, true, false
    );

    /** Translucent, depth-tested, culled, unlit mask coverage. */
    public static final EffectRenderTypeOptions MASK_DEFAULT = new EffectRenderTypeOptions(
        Transparency.TRANSLUCENT, true, true, false, true, false, true
    );

    public EffectRenderTypeOptions {
        Objects.requireNonNull(transparency, "transparency");
    }

    /** Stable descriptor used in render-type names; never depends on object hashes. */
    public String cacheKey() {
        return this.transparency.name()
            + (this.depthTest ? "_depth" : "_nodepth")
            + (this.writeColor ? "_color" : "_nocolor")
            + (this.writeDepth ? "_write" : "_nowrite")
            + (this.cull ? "_cull" : "_nocull")
            + (this.lightmap ? "_lightmap" : "_nolightmap")
            + (this.sort ? "_sort" : "_nosort");
    }

    /** Blend mode applied by the render type. */
    public enum Transparency {
        /** No blending; fragments replace the destination. */
        NONE,
        /** Standard alpha blending. */
        TRANSLUCENT,
        /** Additive blending for glows and beams. */
        ADDITIVE
    }
}
