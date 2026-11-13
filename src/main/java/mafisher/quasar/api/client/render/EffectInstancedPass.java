package mafisher.quasar.api.client.render;

import java.util.Objects;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * One GPU-instanced world pass for a visible effect instance or segment cell. The engine applies the cell's
 * interpolated world transform (position and yaw/pitch/roll) and then {@code localTransform}; include scale and any
 * local rotation in {@code localTransform}.
 *
 * <p>{@code renderType} must use {@link com.mojang.blaze3d.vertex.DefaultVertexFormat#BLOCK} and
 * {@link com.mojang.blaze3d.vertex.VertexFormat.Mode#QUADS}. The shader must declare
 * {@code uniform mat4 Instances[128]}, {@code uniform vec4 InstanceColors[128]}, and
 * {@code uniform vec4 InstanceData[128]}, and use {@code gl_InstanceID} to index them. The built-in
 * {@link QuasarRenderTypes#instancedWorldVisual(net.minecraft.resources.ResourceLocation)} factories satisfy the
 * contract.</p>
 *
 * @param id stable pass ID, unique within one context
 * @param renderType instanced world render type
 * @param mesh local-space mesh uploaded to the GPU once per format and mesh
 * @param localTransform local scale and rotation applied after the world transform
 * @param red red tint in the shader's working range
 * @param green green tint in the shader's working range
 * @param blue blue tint in the shader's working range
 * @param alpha alpha multiplier
 * @param effectDataX first shader data value, available as {@code InstanceData[gl_InstanceID].x}
 * @param effectDataY second shader data value, available as {@code InstanceData[gl_InstanceID].y}
 */
public record EffectInstancedPass(
    String id,
    RenderType renderType,
    EffectMesh mesh,
    Matrix4f localTransform,
    float red,
    float green,
    float blue,
    float alpha,
    float effectDataX,
    float effectDataY
) {
    public EffectInstancedPass {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(renderType, "renderType");
        Objects.requireNonNull(mesh, "mesh");
        Objects.requireNonNull(localTransform, "localTransform");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Instanced pass ID must not be blank");
        }
        if (!localTransform.isFinite()
            || !Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue) || !Float.isFinite(alpha)
            || !Float.isFinite(effectDataX) || !Float.isFinite(effectDataY)) {
            throw new IllegalArgumentException("Instanced pass contains non-finite values");
        }
        localTransform = new Matrix4f(localTransform);
    }
}
