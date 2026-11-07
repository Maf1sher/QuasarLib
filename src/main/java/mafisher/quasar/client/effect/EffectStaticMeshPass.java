package mafisher.quasar.client.effect;

import java.util.Objects;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/**
 * One static, GPU-cached world pass for a visible effect instance or segment cell. The engine draws the mesh from a
 * shared {@link com.mojang.blaze3d.vertex.VertexBuffer} after applying the cell's interpolated world transform
 * (position and yaw/pitch/roll) and then {@code localTransform}; include scale and any local rotation in
 * {@code localTransform}.
 *
 * <p>{@code renderType} must use {@link com.mojang.blaze3d.vertex.DefaultVertexFormat#BLOCK} and
 * {@link com.mojang.blaze3d.vertex.VertexFormat.Mode#QUADS}. The mesh, tint, overlay, and light are baked into the
 * cached buffer once, so this pass is for static materials and stable {@link EffectMesh} instances. Use a CPU
 * {@link EffectRenderPass} for geometry whose vertex colours or {@code UV1} change every frame.</p>
 *
 * @param id stable pass ID, unique within one context
 * @param renderType cached world render type
 * @param mesh local-space mesh uploaded once per render type, mesh, tint, overlay, and light
 * @param localTransform local scale and rotation applied after the world transform
 * @param red red tint baked into the cached vertices
 * @param green green tint baked into the cached vertices
 * @param blue blue tint baked into the cached vertices
 * @param alpha alpha multiplier baked into the cached vertices
 * @param overlay UV1 overlay value baked into the cached vertices
 * @param light packed light value baked into the cached vertices
 */
public record EffectStaticMeshPass(
    String id,
    RenderType renderType,
    EffectMesh mesh,
    Matrix4f localTransform,
    float red,
    float green,
    float blue,
    float alpha,
    int overlay,
    int light
) {
    /** Packed full-bright light used by an effect mesh emitted without world lighting. */
    public static final int FULLBRIGHT_LIGHT = 0x00F000F0;

    public EffectStaticMeshPass {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(renderType, "renderType");
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(localTransform, "localTransform");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Static mesh pass ID must not be blank");
        }
        if (!localTransform.isFinite()
            || !Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue)
            || !Float.isFinite(alpha)) {
            throw new IllegalArgumentException("Static mesh pass contains non-finite values");
        }
        localTransform = new Matrix4f(localTransform);
    }

    /** Builds a pass with no overlay texture and full-bright lighting. */
    public EffectStaticMeshPass(String id, RenderType renderType, EffectMesh mesh, Matrix4f localTransform,
        float red, float green, float blue, float alpha) {
        this(id, renderType, mesh, localTransform, red, green, blue, alpha,
            OverlayTexture.NO_OVERLAY, FULLBRIGHT_LIGHT);
    }
}
