package mafisher.quasar.content.orbital;

import java.util.List;
import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectMesh;
import mafisher.quasar.api.client.render.EffectMeshes;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderPass;
import mafisher.quasar.api.client.render.EffectRenderTypeOptions;
import mafisher.quasar.api.client.render.InstancedVisualEffectRenderer;
import mafisher.quasar.api.client.render.QuasarRenderTypes;
import mafisher.quasar.content.ContentBootstrap;
import mafisher.quasar.content.ContentShaders;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Content-owned depth-tested target marker.
 *
 * <p>The marker is one instanced disk whose fragment shader draws the red reticle. The first
 * {@link ContentOrbitalLaserSettings#TARGET_TICKS} ticks pulse and spin the reticle; after lock the
 * marker stays steady until the session ends or stage 6.2 hands it off to the beam.</p>
 */
public final class ContentOrbitalLaserRenderer implements InstancedVisualEffectRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
        ContentBootstrap.NAMESPACE, "textures/effect/orbital_laser_marker.png"
    );
    private static final EffectMesh MARKER_MESH = EffectMeshes.disk(2, 96);
    private static final RenderType MARKER_RENDER_TYPE = QuasarRenderTypes.instancedWorldVisual(
        ContentShaders.ORBITAL_LASER_MARKER,
        TEXTURE,
        new EffectRenderTypeOptions(
            EffectRenderTypeOptions.Transparency.TRANSLUCENT,
            true,
            true,
            false,
            false,
            false,
            true
        )
    );
    private static final String MARKER_PASS = "marker";
    private static final float MAX_SCALE_PULSE = 1.05F;
    private static final double BOUNDS_HEIGHT = 0.5;
    private static final List<EffectRenderPass> RENDER_PASSES = List.of();

    private static final float MARKER_RED = 1.0F;
    private static final float MARKER_GREEN = 0.16F;
    private static final float MARKER_BLUE = 0.10F;

    @Override
    public List<EffectRenderPass> renderPasses(EffectRenderContext context) {
        return RENDER_PASSES;
    }

    @Override
    public List<EffectInstancedPass> instancedPasses(EffectRenderContext context) {
        float age = context.ageTicks();
        boolean locking = age < ContentOrbitalLaserSettings.TARGET_TICKS;
        float pulse = locking
            ? 1.0F + 0.04F * (float)Math.sin(age * 0.55F)
            : 1.0F;
        float spin = locking
            ? (float)Math.toRadians(age * (360.0F / ContentOrbitalLaserSettings.TARGET_TICKS))
            : 0.0F;
        float alpha = locking
            ? 0.62F + 0.28F * (0.5F + 0.5F * (float)Math.sin(age * 0.5F))
            : 0.86F;
        float lockProgress = Math.min(
            1.0F, age / ContentOrbitalLaserSettings.TARGET_TICKS
        );
        float seed = (Long.hashCode(context.seed()) & 0xFFFF) / 65535.0F;
        Matrix4f localTransform = new Matrix4f()
            .rotateY(spin)
            .scale(context.scale() * pulse);
        EffectInstancedPass marker = new EffectInstancedPass(
            MARKER_PASS,
            MARKER_RENDER_TYPE,
            MARKER_MESH,
            localTransform,
            MARKER_RED,
            MARKER_GREEN,
            MARKER_BLUE,
            alpha,
            lockProgress,
            seed
        );
        return List.of(marker);
    }

    @Override
    public AABB cullingBounds(EffectRenderContext context) {
        double radius = maximumRadius(context);
        double x = context.transform().position().x();
        double y = context.transform().position().y();
        double z = context.transform().position().z();
        return new AABB(
            x - radius, y - BOUNDS_HEIGHT, z - radius,
            x + radius, y + BOUNDS_HEIGHT, z + radius
        );
    }

    @Override
    public double spatialRadius(EffectRenderContext context) {
        double radius = maximumRadius(context);
        return Math.sqrt(2.0 * radius * radius + BOUNDS_HEIGHT * BOUNDS_HEIGHT) + 0.01;
    }

    private static double maximumRadius(EffectRenderContext context) {
        return context.scale() * MAX_SCALE_PULSE;
    }
}
