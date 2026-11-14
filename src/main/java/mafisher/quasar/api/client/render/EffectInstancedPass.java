package mafisher.quasar.api.client.render;

import java.util.Objects;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * One GPU-instanced world pass for a visible effect instance or segment cell. The engine applies the cell's
 * interpolated transform (position and yaw/pitch/roll) in the camera-relative frame and then {@code localTransform};
 * include scale and any local rotation in {@code localTransform}.
 *
 * <p>{@code renderType} must use {@link com.mojang.blaze3d.vertex.DefaultVertexFormat#BLOCK} and
 * {@link com.mojang.blaze3d.vertex.VertexFormat.Mode#QUADS}. The engine's buffer-texture contract declares
 * {@code uniform samplerBuffer InstanceBuffer} and imports
 * {@code quasar_lib:instance_payload.glsl}; it stores four camera-relative model-matrix columns, a color, and an
 * effect-data vector per instance. Shaders that declare only the legacy
 * {@code Instances[128]}/{@code InstanceColors[128]}/{@code InstanceData[128]} arrays keep working through the
 * 128-instance uniform fallback. Both paths use {@code gl_InstanceID}. The built-in
 * {@link QuasarRenderTypes#instancedWorldVisual(net.minecraft.resources.ResourceLocation)} factory satisfies the
 * buffer-texture contract.</p>
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
