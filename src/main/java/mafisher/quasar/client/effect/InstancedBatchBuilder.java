package mafisher.quasar.client.effect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import mafisher.quasar.api.client.EffectInstancedPass;
import mafisher.quasar.api.client.EffectMesh;
import mafisher.quasar.api.client.EffectRenderContext;
import mafisher.quasar.api.effect.EffectTransform;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Groups instanced passes across visible effects and segment cells and splits them into bounded GPU draws. This class
 * is pure: matrix composition and batching are covered by headless tests, while the renderer owns GL resources.
 *
 * <p>Batches whose render type sorts on upload are ordered back to front from the camera, so translucent instances
 * blend in the expected order even though an instanced draw cannot use {@code MultiBufferSource} sorting. Opaque and
 * unsorted types keep insertion order.</p>
 */
final class InstancedBatchBuilder {
    private static final Comparator<Instance> BACK_TO_FRONT =
        Comparator.comparingDouble(Instance::cameraDistanceSquared).reversed();

    private final int maxInstancesPerDraw;
    private final Map<BatchKey, Batch> batches = new LinkedHashMap<>();
    private double cameraX;
    private double cameraY;
    private double cameraZ;

    InstancedBatchBuilder(int maxInstancesPerDraw) {
        this(maxInstancesPerDraw, 0.0, 0.0, 0.0);
    }

    InstancedBatchBuilder(int maxInstancesPerDraw, double cameraX, double cameraY, double cameraZ) {
        if (maxInstancesPerDraw < 1) {
            throw new IllegalArgumentException("An instanced draw needs at least one slot");
        }
        this.maxInstancesPerDraw = maxInstancesPerDraw;
        this.reset(cameraX, cameraY, cameraZ);
    }

    int maxInstancesPerDraw() {
        return this.maxInstancesPerDraw;
    }

    /** Clears the previous frame and moves the sort origin to the new camera position. */
    void reset(double cameraX, double cameraY, double cameraZ) {
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ)) {
            throw new IllegalArgumentException("Instanced batch camera position must be finite");
        }
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.batches.clear();
    }

    boolean isEmpty() {
        return this.batches.isEmpty();
    }

    /** Adds one context's instanced passes after validating that its pass IDs are unique. */
    void add(EffectRenderContext context, List<EffectInstancedPass> passes) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(passes, "passes");
        Set<String> ids = new HashSet<>();
        for (EffectInstancedPass pass : passes) {
            Objects.requireNonNull(pass, "pass");
            if (!ids.add(pass.id())) {
                throw new IllegalStateException("Duplicate instanced pass ID '" + pass.id()
                    + "' for effect " + context.effect().effectId());
            }
            BatchKey key = new BatchKey(pass.id(), pass.renderType(), pass.mesh());
            this.batches.computeIfAbsent(key, Batch::new).add(context.transform(), pass,
                this.cameraX, this.cameraY, this.cameraZ);
        }
    }

    /** Returns ordered draws, each bounded by the configured instance limit. */
    List<InstancedDraw> draws() {
        List<InstancedDraw> draws = new ArrayList<>();
        this.drawInto(draws);
        return List.copyOf(draws);
    }

    /** Fills {@code output} with the ordered draws for one immediate render pass. */
    void drawInto(List<InstancedDraw> output) {
        Objects.requireNonNull(output, "output");
        output.clear();
        for (Batch batch : this.batches.values()) {
            List<Instance> instances = batch.instances;
            if (batch.key.renderType().sortOnUpload() && instances.size() > 1) {
                instances.sort(BACK_TO_FRONT);
            }
            for (int start = 0; start < instances.size(); start += this.maxInstancesPerDraw) {
                int end = Math.min(start + this.maxInstancesPerDraw, instances.size());
                output.add(new InstancedDraw(batch.key, instances.subList(start, end)));
            }
        }
    }

    /** Composes the cell world transform with the renderer's local transform. */
    static Matrix4f modelMatrix(EffectTransform transform, Matrix4f localTransform) {
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(localTransform, "localTransform");
        return new Matrix4f()
            .translate(
                (float)transform.position().x(),
                (float)transform.position().y(),
                (float)transform.position().z()
            )
            .rotateY((float)Math.toRadians(transform.yaw()))
            .rotateX((float)Math.toRadians(transform.pitch()))
            .rotateZ((float)Math.toRadians(transform.roll()))
            .mul(localTransform);
    }

    record BatchKey(String id, RenderType renderType, EffectMesh mesh) {
        BatchKey {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(renderType, "renderType");
            Objects.requireNonNull(mesh, "mesh");
        }
    }

    record Instance(
        Matrix4f model,
        float red,
        float green,
        float blue,
        float alpha,
        float effectDataX,
        float effectDataY,
        float effectDataZ,
        float effectDataW,
        double cameraDistanceSquared
    ) {
        Instance {
            Objects.requireNonNull(model, "model");
            if (!Double.isFinite(cameraDistanceSquared) || cameraDistanceSquared < 0.0) {
                throw new IllegalArgumentException("Invalid instance camera distance");
            }
        }

        /** Source-compatible constructor for passes that only carry two data values. */
        Instance(
            Matrix4f model,
            float red,
            float green,
            float blue,
            float alpha,
            float effectDataX,
            float effectDataY,
            double cameraDistanceSquared
        ) {
            this(model, red, green, blue, alpha, effectDataX, effectDataY, 0.0F, 0.0F,
                cameraDistanceSquared);
        }
    }

    record InstancedDraw(BatchKey key, List<Instance> instances) {
        InstancedDraw {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(instances, "instances");
            if (instances.isEmpty()) {
                throw new IllegalArgumentException("An instanced draw needs at least one instance");
            }
        }
    }

    private static final class Batch {
        private final BatchKey key;
        private final List<Instance> instances = new ArrayList<>();

        private Batch(BatchKey key) {
            this.key = key;
        }

        private void add(EffectTransform transform, EffectInstancedPass pass, double cameraX, double cameraY,
            double cameraZ) {
            double dx = transform.position().x() - cameraX;
            double dy = transform.position().y() - cameraY;
            double dz = transform.position().z() - cameraZ;
            this.instances.add(new Instance(
                modelMatrix(transform, pass.localTransform()),
                pass.red(),
                pass.green(),
                pass.blue(),
                pass.alpha(),
                pass.effectDataX(),
                pass.effectDataY(),
                dx * dx + dy * dy + dz * dz
            ));
        }
    }
}
