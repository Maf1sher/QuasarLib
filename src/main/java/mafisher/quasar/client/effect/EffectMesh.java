package mafisher.quasar.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;

/** Immutable local-space quad mesh whose per-draw material values remain dynamic. */
final class EffectMesh {
    private final List<Vertex> vertices;

    private EffectMesh(List<Vertex> vertices) {
        this.vertices = List.copyOf(vertices);
    }

    static Builder builder(int expectedVertexCount) {
        return new Builder(expectedVertexCount);
    }

    int vertexCount() {
        return this.vertices.size();
    }

    Vertex vertex(int index) {
        return this.vertices.get(index);
    }

    void emit(VertexConsumer consumer, PoseStack.Pose pose, float red, float green, float blue, float alpha,
        int overlay, int light) {
        for (int index = 0; index < this.vertices.size(); index++) {
            Vertex vertex = this.vertices.get(index);
            consumer.addVertex(pose, vertex.x(), vertex.y(), vertex.z())
                .setColor(red, green, blue, alpha * vertex.alphaMultiplier())
                .setUv(vertex.u(), vertex.v())
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, vertex.normalX(), vertex.normalY(), vertex.normalZ());
        }
    }

    record Vertex(float x, float y, float z, float u, float v,
        float normalX, float normalY, float normalZ, float alphaMultiplier) {
    }

    static final class Builder {
        private final List<Vertex> vertices;

        private Builder(int expectedVertexCount) {
            if (expectedVertexCount < 4 || expectedVertexCount % 4 != 0) {
                throw new IllegalArgumentException("Mesh vertex capacity must be a positive multiple of four");
            }
            this.vertices = new ArrayList<>(expectedVertexCount);
        }

        Builder vertex(float x, float y, float z, float u, float v,
            float normalX, float normalY, float normalZ) {
            return this.vertex(x, y, z, u, v, normalX, normalY, normalZ, 1.0F);
        }

        Builder vertex(float x, float y, float z, float u, float v,
            float normalX, float normalY, float normalZ, float alphaMultiplier) {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || !Float.isFinite(u) || !Float.isFinite(v)
                || !Float.isFinite(normalX) || !Float.isFinite(normalY) || !Float.isFinite(normalZ)
                || !Float.isFinite(alphaMultiplier) || alphaMultiplier < 0.0F || alphaMultiplier > 1.0F) {
                throw new IllegalArgumentException("Mesh vertex contains invalid attributes");
            }
            this.vertices.add(new Vertex(x, y, z, u, v, normalX, normalY, normalZ, alphaMultiplier));
            return this;
        }

        EffectMesh build() {
            if (this.vertices.isEmpty() || this.vertices.size() % 4 != 0) {
                throw new IllegalStateException("Mesh must contain one or more complete quads");
            }
            return new EffectMesh(this.vertices);
        }
    }
}
