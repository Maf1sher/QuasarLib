package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Mutable request reused by {@link ClientDepthOcclusion}. The backend consumes it synchronously, so
 * one instance per tracker avoids a per-probe allocation.
 */
final class DepthProbeRequest {
    private AABB bounds;
    private Matrix4f viewRotation;
    private Matrix4f projection;
    private RenderTarget depthTarget;
    private double cameraX;
    private double cameraY;
    private double cameraZ;

    DepthProbeRequest set(AABB bounds, Matrix4f viewRotation, Matrix4f projection,
        RenderTarget depthTarget, double cameraX, double cameraY, double cameraZ) {
        this.bounds = bounds;
        this.viewRotation = viewRotation;
        this.projection = projection;
        this.depthTarget = depthTarget;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        return this;
    }

    AABB bounds() {
        return this.bounds;
    }

    Matrix4f viewRotation() {
        return this.viewRotation;
    }

    Matrix4f projection() {
        return this.projection;
    }

    RenderTarget depthTarget() {
        return this.depthTarget;
    }

    double cameraX() {
        return this.cameraX;
    }

    double cameraY() {
        return this.cameraY;
    }

    double cameraZ() {
        return this.cameraZ;
    }
}
