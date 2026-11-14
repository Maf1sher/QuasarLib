package mafisher.quasar.client.effect.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectStaticMeshPass;
import mafisher.quasar.api.effect.EffectTransform;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Groups consecutive compatible static-mesh passes into instanced runs. The builder is CPU-side and reusable: it
 * keeps command and instance objects between frames so the steady-state render path does not allocate.
 *
 * <p>Unlike dynamic instanced batching, static runs keep insertion order and are not sorted. This preserves the
 * draw order of the cached-mesh path. A pass whose material has no instanced twin becomes a single fallback command
 * in the same position.</p>
 */
@SuppressWarnings("PMD.TooManyMethods")
final class StaticMeshBatchBuilder {
    private final int maxInstancesPerDraw;
    private final List<Command> commands = new ArrayList<>();
    private final Matrix4f rotationScratch = new Matrix4f();
    private int commandCount;
    private Vec3 camera = Vec3.ZERO;

    StaticMeshBatchBuilder(int maxInstancesPerDraw) {
        if (maxInstancesPerDraw < 1) {
            throw new IllegalArgumentException("A static batch needs at least one slot");
        }
        this.maxInstancesPerDraw = maxInstancesPerDraw;
    }

    int maxInstancesPerDraw() {
        return this.maxInstancesPerDraw;
    }

    Vec3 camera() {
        return this.camera;
    }

    void reset(Vec3 camera) {
        Objects.requireNonNull(camera, "camera");
        if (!Double.isFinite(camera.x) || !Double.isFinite(camera.y) || !Double.isFinite(camera.z)) {
            throw new IllegalArgumentException("Static batch camera position must be finite");
        }
        this.camera = camera;
        for (int index = 0; index < this.commandCount; index++) {
            this.commands.get(index).reset();
        }
        this.commandCount = 0;
    }

    /**
     * Adds one static pass. {@code batchRenderType} is the material's instanced twin, or {@code null} to keep the
     * pass on the unbatched fallback path.
     */
    void add(EffectStaticMeshPass pass, @Nullable RenderType batchRenderType,
        EffectTransform transform) {
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(transform, "transform");
        if (batchRenderType == null) {
            this.nextCommand().beginSingle(pass, transform);
            return;
        }

        Command last = this.commandCount == 0 ? null : this.commands.get(this.commandCount - 1);
        if (last != null && last.isBatched() && last.instanceCount() < this.maxInstancesPerDraw
            && last.matches(pass, batchRenderType)) {
            last.addInstance(pass, transform, this.camera, this.rotationScratch);
            return;
        }

        Command command = this.nextCommand();
        command.beginBatch(pass, batchRenderType);
        command.addInstance(pass, transform, this.camera, this.rotationScratch);
    }

    boolean isEmpty() {
        return this.commandCount == 0;
    }

    int commandCount() {
        return this.commandCount;
    }

    Command command(int index) {
        if (index < 0 || index >= this.commandCount) {
            throw new IndexOutOfBoundsException(index);
        }
        return this.commands.get(index);
    }

    private Command nextCommand() {
        if (this.commandCount == this.commands.size()) {
            this.commands.add(new Command());
        }
        Command command = this.commands.get(this.commandCount++);
        command.reset();
        return command;
    }

    static final class Command implements InstanceBatchDescriptor {
        private final List<InstancedBatchBuilder.Instance> instances = new ArrayList<>();
        private final List<InstancedBatchBuilder.Instance> instancePool = new ArrayList<>();
        private final Matrix4f matrixScratch = new Matrix4f();
        private boolean batched;
        private String id;
        private RenderType renderType;
        private EffectMesh mesh;
        private int overlay;
        private int light;
        private EffectStaticMeshPass singlePass;
        private EffectTransform singleTransform;

        private Command() {
        }

        boolean isBatched() {
            return this.batched;
        }

        @Override
        public String id() {
            return this.id;
        }

        @Override
        public RenderType renderType() {
            return this.renderType;
        }

        @Override
        public EffectMesh mesh() {
            return this.mesh;
        }

        @Override
        public int overlay() {
            return this.overlay;
        }

        @Override
        public int light() {
            return this.light;
        }

        int instanceCount() {
            return this.instances.size();
        }

        List<InstancedBatchBuilder.Instance> instances() {
            return this.instances;
        }

        EffectStaticMeshPass singlePass() {
            return this.singlePass;
        }

        EffectTransform singleTransform() {
            return this.singleTransform;
        }

        private void beginBatch(EffectStaticMeshPass pass, RenderType batchRenderType) {
            this.reset();
            this.batched = true;
            this.id = pass.id();
            this.renderType = batchRenderType;
            this.mesh = pass.mesh();
            this.overlay = pass.overlay();
            this.light = pass.light();
        }

        private void beginSingle(EffectStaticMeshPass pass, EffectTransform transform) {
            this.reset();
            this.batched = false;
            this.singlePass = pass;
            this.singleTransform = transform;
        }

        private boolean matches(EffectStaticMeshPass pass, RenderType batchRenderType) {
            return this.id.equals(pass.id())
                && this.renderType == batchRenderType
                && this.mesh == pass.mesh()
                && this.overlay == pass.overlay()
                && this.light == pass.light();
        }

        private void addInstance(EffectStaticMeshPass pass, EffectTransform transform, Vec3 camera,
            Matrix4f rotationScratch) {
            double dx = transform.position().x() - camera.x;
            double dy = transform.position().y() - camera.y;
            double dz = transform.position().z() - camera.z;
            InstancedBatchBuilder.modelMatrixInto(transform, pass.localTransform(), camera.x, camera.y,
                camera.z, this.matrixScratch, rotationScratch);
            InstancedBatchBuilder.Instance instance = this.instancePool.isEmpty()
                ? new InstancedBatchBuilder.Instance()
                : this.instancePool.remove(this.instancePool.size() - 1);
            instance.setModel(this.matrixScratch);
            instance.setColor(pass.red(), pass.green(), pass.blue(), pass.alpha());
            instance.setEffectData(
                clampOverlayComponent(pass.overlay() & 0xFFFF),
                clampOverlayComponent((pass.overlay() >>> 16) & 0xFFFF),
                0.0F,
                0.0F
            );
            instance.setCameraDistanceSquared(dx * dx + dy * dy + dz * dz);
            this.instances.add(instance);
        }

        private void reset() {
            this.instancePool.addAll(this.instances);
            this.instances.clear();
            this.batched = false;
            this.id = null;
            this.renderType = null;
            this.mesh = null;
            this.overlay = 0;
            this.light = 0;
            this.singlePass = null;
            this.singleTransform = null;
        }

        private static float clampOverlayComponent(int value) {
            return Math.min(1.0F, value / 32767.0F);
        }
    }
}
