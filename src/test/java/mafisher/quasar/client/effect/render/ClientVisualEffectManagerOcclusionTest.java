package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectCameraSnapshot;
import mafisher.quasar.api.compat.SceneDepthFrame;
import mafisher.quasar.api.compat.SceneDepthSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class ClientVisualEffectManagerOcclusionTest {
    private static final Matrix4f PROJECTION = new Matrix4f()
        .perspective((float)Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 1024.0F);
    private static final AABB BOUNDS = new AABB(-10.0, -10.0, -110.0, 10.0, 10.0, -90.0);

    @Test
    void filtersOwnersByTheOcclusionDecision() {
        FakeBackend backend = new FakeBackend();
        ClientDepthOcclusion occlusion = new ClientDepthOcclusion(backend);
        ClientVisualEffectManager manager = new ClientVisualEffectManager(
            new VisualEffectRendererRegistry(), mock(ResourceManager.class), occlusion);
        SceneDepthFrame frame = new SceneDepthFrame(new FakeTarget(),
            SceneDepthSource.HORIZON_LOD, PROJECTION);
        EffectCameraSnapshot camera = new EffectCameraSnapshot(
            new Vec3(0.0, 0.0, 0.0), new Matrix4f(), PROJECTION, 1920, 1080);
        UUID owner = UUID.randomUUID();
        occlusion.beginFrame(frame, camera);

        assertTrue(manager.shouldRenderDepth(owner, BOUNDS, true));
        backend.complete(backend.lastQuery(), false);
        occlusion.beginFrame(frame, camera);

        assertFalse(manager.shouldRenderDepth(owner, BOUNDS, true));
        assertTrue(manager.shouldRenderDepth(owner, BOUNDS, false));
        assertTrue(manager.shouldRenderDepth(owner, null, true));
    }

    private static final class FakeTarget extends RenderTarget {
        private FakeTarget() {
            super(true);
            this.width = 1920;
            this.height = 1080;
            this.depthBufferId = 7;
        }
    }

    private static final class FakeBackend implements DepthProbeBackend {
        private final Map<Integer, Boolean> results = new HashMap<>();
        private int nextQuery = 1;
        private int lastQuery;

        private void complete(int query, boolean anySamples) {
            this.results.put(query, anySamples);
        }

        private int lastQuery() {
            return this.lastQuery;
        }

        @Override
        public boolean supported() {
            return true;
        }

        @Override
        public int issue(DepthProbeRequest request) {
            this.lastQuery = this.nextQuery++;
            return this.lastQuery;
        }

        @Override
        public boolean isResultAvailable(int query) {
            return this.results.containsKey(query);
        }

        @Override
        public boolean anySamplesPassed(int query) {
            return this.results.getOrDefault(query, true);
        }

        @Override
        public void deleteQuery(int query) {
            this.results.remove(query);
        }

        @Override
        public void reset() {
        }
    }
}
