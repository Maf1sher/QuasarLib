package mafisher.quasar.client.effect.render;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import org.joml.Matrix4f;

/**
 * Draws the ordered command list produced by {@link StaticMeshBatchBuilder}. Batched commands go through the shared
 * instanced renderer; incompatible passes fall back to one cached-mesh draw in the same position.
 *
 * <p>Resolved GPU meshes are cached per command identity, so the steady-state path does not rebuild a mesh key every
 * frame. A cache generation invalidates the reference when {@link GpuMeshCache} evicts a mesh.</p>
 */
final class StaticMeshBatchRenderer {
    private final InstancedBatchRenderer instancedRenderer;
    private final CachedMeshRenderer cachedRenderer;
    private final GpuMeshCache meshCache;
    private final Map<StaticMeshBatchBuilder.Command, CachedMesh> cachedMeshes = new IdentityHashMap<>();

    StaticMeshBatchRenderer(InstancedBatchRenderer instancedRenderer, CachedMeshRenderer cachedRenderer,
        GpuMeshCache meshCache) {
        this.instancedRenderer = Objects.requireNonNull(instancedRenderer, "instancedRenderer");
        this.cachedRenderer = Objects.requireNonNull(cachedRenderer, "cachedRenderer");
        this.meshCache = Objects.requireNonNull(meshCache, "meshCache");
    }

    void draw(StaticMeshBatchBuilder builder, Matrix4f modelView, Matrix4f projection) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(modelView, "modelView");
        Objects.requireNonNull(projection, "projection");
        for (int index = 0; index < builder.commandCount(); index++) {
            StaticMeshBatchBuilder.Command command = builder.command(index);
            if (command.isBatched()) {
                CachedMesh cached = this.cachedMeshes.get(command);
                if (cached == null) {
                    cached = new CachedMesh(command);
                    this.cachedMeshes.put(command, cached);
                } else if (!cached.matches(command)) {
                    cached.update(command);
                }
                GpuMeshCache.GpuMesh mesh = cached.resolve(this.meshCache);
                if (mesh == null) {
                    continue;
                }
                this.instancedRenderer.drawResolvedBatch(
                    command,
                    mesh,
                    command.instances(),
                    0,
                    command.instanceCount(),
                    modelView,
                    projection
                );
            } else {
                this.cachedRenderer.drawSingle(
                    command.singlePass(),
                    command.singleTransform(),
                    builder.camera(),
                    modelView,
                    projection
                );
            }
        }
    }

    private static final class CachedMesh {
        private GpuMeshCache.GpuMeshKey key;
        private GpuMeshCache.GpuMesh mesh;
        private long generation = Long.MIN_VALUE;

        private CachedMesh(StaticMeshBatchBuilder.Command command) {
            this.update(command);
        }

        private boolean matches(StaticMeshBatchBuilder.Command command) {
            return this.key.mesh() == command.mesh()
                && this.key.format() == command.renderType().format()
                && this.key.mode() == command.renderType().mode()
                && this.key.overlay() == command.overlay()
                && this.key.light() == command.light();
        }

        private void update(StaticMeshBatchBuilder.Command command) {
            this.key = new GpuMeshCache.GpuMeshKey(
                command.renderType().format(),
                command.renderType().mode(),
                command.mesh(),
                1.0F,
                1.0F,
                1.0F,
                1.0F,
                command.overlay(),
                command.light()
            );
            this.mesh = null;
            this.generation = Long.MIN_VALUE;
        }

        private GpuMeshCache.GpuMesh resolve(GpuMeshCache cache) {
            if (this.mesh == null || this.generation != cache.generation()) {
                this.mesh = cache.get(this.key);
                this.generation = cache.generation();
            }
            return this.mesh;
        }
    }
}
