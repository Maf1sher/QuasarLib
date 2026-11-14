package mafisher.quasar.client.effect.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectStaticMeshPass;
import mafisher.quasar.api.effect.EffectSpace;
import mafisher.quasar.api.effect.EffectTransform;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Groups instanced passes across visible effects and segment cells and splits them into bounded GPU draws. This class
 * is pure: matrix composition and batching are covered by headless tests, while the renderer owns GL resources.
 *
 * <p>Model matrices are composed in the camera-relative frame so large world coordinates stay stable. Batches whose
 * render type sorts on upload are ordered back to front from the camera, so translucent instances blend in the
 * expected order even though an instanced draw cannot use {@code MultiBufferSource} sorting. Opaque and unsorted
 * types keep insertion order.</p>
 */
@SuppressWarnings("PMD.TooManyMethods")
final class InstancedBatchBuilder {
    private static final Comparator<Instance> BACK_TO_FRONT =
        Comparator.comparingDouble(Instance::cameraDistanceSquared).reversed();

    private final int maxInstancesPerDraw;
    private final Map<BatchKey, Batch> batches = new LinkedHashMap<>();
    private final Matrix4f rotationScratch = new Matrix4f();
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
            BatchKey key = new BatchKey(pass.id(), pass.renderType(), pass.mesh(), 0,
                EffectStaticMeshPass.FULLBRIGHT_LIGHT);
            this.batches.computeIfAbsent(key, Batch::new).add(context.transform(), pass,
                this.cameraX, this.cameraY, this.cameraZ, this.rotationScratch);
        }
    }

    /** Returns ordered draws, each bounded by the configured instance limit. */
    List<InstancedDraw> draws() {
        List<InstancedDraw> draws = new ArrayList<>();
        this.drawInto(draws);
        return draws;
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
                output.add(new InstancedDraw(batch.key, instances, start, end));
            }
        }
    }

    /**
     * Writes the camera-relative model matrix into a reusable destination.
     *
     * @param rotationScratch a reusable matrix used for the engine yaw/pitch/roll basis
     */
    static void modelMatrixInto(EffectTransform transform, Matrix4f localTransform, double cameraX,
        double cameraY, double cameraZ, Matrix4f destination, Matrix4f rotationScratch) {
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(localTransform, "localTransform");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(rotationScratch, "rotationScratch");
        if (!Double.isFinite(cameraX) || !Double.isFinite(cameraY) || !Double.isFinite(cameraZ)) {
            throw new IllegalArgumentException("Instanced batch camera position must be finite");
        }
        EffectSpace.Basis.rotationInto(
            transform.yaw(), transform.pitch(), transform.roll(), rotationScratch);
        destination.identity()
            .translate(
                (float)(transform.position().x() - cameraX),
                (float)(transform.position().y() - cameraY),
                (float)(transform.position().z() - cameraZ)
            )
            .mul(rotationScratch)
            .mul(localTransform);
    }

    record BatchKey(String id, RenderType renderType, EffectMesh mesh, int overlay, int light)
        implements InstanceBatchDescriptor {
        BatchKey {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(renderType, "renderType");
            Objects.requireNonNull(mesh, "mesh");
        }
    }

    static final class Instance {
        private final Matrix4f model = new Matrix4f();
        private float red;
        private float green;
        private float blue;
        private float alpha;
        private float effectDataX;
        private float effectDataY;
        private float effectDataZ;
        private float effectDataW;
        private double cameraDistanceSquared;

        Instance() {
        }

        void setModel(Matrix4f model) {
            Objects.requireNonNull(model, "model");
            if (!model.isFinite()) {
                throw new IllegalArgumentException("Instance model matrix must be finite");
            }
            this.model.set(model);
        }

        void setColor(float red, float green, float blue, float alpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }

        void setEffectData(float effectDataX, float effectDataY, float effectDataZ, float effectDataW) {
            this.effectDataX = effectDataX;
            this.effectDataY = effectDataY;
            this.effectDataZ = effectDataZ;
            this.effectDataW = effectDataW;
        }

        void setCameraDistanceSquared(double cameraDistanceSquared) {
            if (!Double.isFinite(cameraDistanceSquared) || cameraDistanceSquared < 0.0) {
                throw new IllegalArgumentException("Invalid instance camera distance");
            }
            this.cameraDistanceSquared = cameraDistanceSquared;
        }

        Matrix4f model() {
            return this.model;
        }

        float red() {
            return this.red;
        }

        float green() {
            return this.green;
        }

        float blue() {
            return this.blue;
        }

        float alpha() {
            return this.alpha;
        }

        float effectDataX() {
            return this.effectDataX;
        }

        float effectDataY() {
            return this.effectDataY;
        }

        float effectDataZ() {
            return this.effectDataZ;
        }

        float effectDataW() {
            return this.effectDataW;
        }

        double cameraDistanceSquared() {
            return this.cameraDistanceSquared;
        }
    }

    static final class InstancedDraw {
        private final BatchKey key;
        private final List<Instance> instances;
        private final int start;
        private final int end;

        InstancedDraw(BatchKey key, List<Instance> instances, int start, int end) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(instances, "instances");
            if (start < 0 || end <= start || end > instances.size()) {
                throw new IllegalArgumentException("Invalid instanced draw range");
            }
            this.key = key;
            this.instances = instances;
            this.start = start;
            this.end = end;
        }

        BatchKey key() {
            return this.key;
        }

        List<Instance> instances() {
            return this.instances;
        }

        int start() {
            return this.start;
        }

        int end() {
            return this.end;
        }

        int instanceCount() {
            return this.end - this.start;
        }

        Instance instance(int index) {
            if (index < 0 || index >= instanceCount()) {
                throw new IndexOutOfBoundsException(index);
            }
            return this.instances.get(this.start + index);
        }
    }

    private static final class Batch {
        private final BatchKey key;
        private final List<Instance> instances = new ArrayList<>();

        private Batch(BatchKey key) {
            this.key = key;
        }

        private void add(EffectTransform transform, EffectInstancedPass pass, double cameraX, double cameraY,
            double cameraZ, Matrix4f rotationScratch) {
            double dx = transform.position().x() - cameraX;
            double dy = transform.position().y() - cameraY;
            double dz = transform.position().z() - cameraZ;
            Matrix4f model = new Matrix4f();
            modelMatrixInto(transform, pass.localTransform(), cameraX, cameraY, cameraZ, model,
                rotationScratch);
            Instance instance = new Instance();
            instance.setModel(model);
            instance.setColor(pass.red(), pass.green(), pass.blue(), pass.alpha());
            instance.setEffectData(pass.effectDataX(), pass.effectDataY(), 0.0F, 0.0F);
            instance.setCameraDistanceSquared(dx * dx + dy * dy + dz * dz);
            this.instances.add(instance);
        }
    }
}
