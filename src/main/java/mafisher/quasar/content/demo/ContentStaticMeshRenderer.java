package mafisher.quasar.content.demo;

import java.util.List;
import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderPass;
import mafisher.quasar.api.client.render.EffectStaticMesh;
import mafisher.quasar.api.client.render.EffectStaticMeshPass;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import mafisher.quasar.api.client.render.StaticMeshVisualEffectRenderer;
import mafisher.quasar.content.ContentBootstrap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * Content-owned static-mesh acceptance demo.
 *
 * <p>The renderer loads one external OBJ through the public {@link EffectStaticMesh}
 * contract and submits it as a cached static pass. The pass list is rebuilt only when
 * the mesh or the instance scale changes, so steady-state frames do not allocate.</p>
 */
public final class ContentStaticMeshRenderer implements StaticMeshVisualEffectRenderer {
    private static final ResourceLocation ASSET = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "quasar/meshes/mushroom.obj"
    );
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "textures/effect/ice_shard.png"
    );
    private static final String PASS_ID = "static-mesh-test";
    private static final RenderType RENDER_TYPE = QuasarRenderTypes.worldVisual(TEXTURE);
    private static final List<EffectRenderPass> RENDER_PASSES = List.of();

    private final EffectStaticMesh mesh = EffectStaticMesh.of(ASSET);
    private @Nullable EffectMesh cachedMesh;
    private float cachedScale = Float.NaN;
    private List<EffectStaticMeshPass> cachedPasses = List.of();

    @Override
    public List<EffectRenderPass> renderPasses(EffectRenderContext context) {
        return RENDER_PASSES;
    }

    @Override
    public List<EffectStaticMeshPass> staticMeshPasses(EffectRenderContext context) {
        EffectMesh current = this.mesh.mesh();
        if (current == null) {
            return List.of();
        }
        float scale = context.scale();
        if (current != this.cachedMesh || scale != this.cachedScale) {
            this.cachedMesh = current;
            this.cachedScale = scale;
            this.cachedPasses = List.of(new EffectStaticMeshPass(
                PASS_ID,
                RENDER_TYPE,
                current,
                new Matrix4f().scale(scale),
                1.0F,
                1.0F,
                1.0F,
                1.0F
            ));
        }
        return this.cachedPasses;
    }

    @Override
    public AABB cullingBounds(EffectRenderContext context) {
        double radius = spatialRadius(context);
        double x = context.transform().position().x();
        double y = context.transform().position().y();
        double z = context.transform().position().z();
        return new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
    }

    @Override
    public double spatialRadius(EffectRenderContext context) {
        AABB bounds = this.mesh.localBounds();
        if (bounds == null) {
            return context.scale();
        }
        double x = Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX));
        double y = Math.max(Math.abs(bounds.minY), Math.abs(bounds.maxY));
        double z = Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ));
        return Math.sqrt(x * x + y * y + z * z) * context.scale();
    }

    @Override
    public void onResourceReload(ResourceManager resources) {
        this.mesh.reload(resources);
        this.cachedMesh = null;
        this.cachedScale = Float.NaN;
        this.cachedPasses = List.of();
    }
}
