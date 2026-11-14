package mafisher.quasar.client.effect.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectCameraSnapshot;
import mafisher.quasar.api.compat.SceneDepthFrame;
import mafisher.quasar.api.compat.SceneDepthSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class ClientDepthOcclusionTest {
    private static final Matrix4f PROJECTION = new Matrix4f()
        .perspective((float)Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 1024.0F);
    private static final AABB BOUNDS = new AABB(-10.0, -10.0, -110.0, 10.0, 10.0, -90.0);
    private static final SceneDepthFrame FRAME = new SceneDepthFrame(
        new FakeTarget(), SceneDepthSource.HORIZON_LOD, PROJECTION);

    @Test
    void firstFrameDrawsAndStartsAProbe() {
        FakeBackend backend = new FakeBackend();
        ClientDepthOcclusion occlusion = new ClientDepthOcclusion(backend);
        UUID owner = UUID.randomUUID();

        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));

        assertTrue(occlusion.shouldDraw(owner, BOUNDS, true));
        assertEquals(1, backend.issued.size());
    }

    @Test
    void zeroSamplesSkipsOwnersUntilTheResultExpires() {
        FakeBackend backend = new FakeBackend();
        ClientDepthOcclusion occlusion = new ClientDepthOcclusion(backend);
        UUID owner = UUID.randomUUID();
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        assertTrue(occlusion.shouldDraw(owner, BOUNDS, true));
        backend.complete(backend.issued.getLast(), false);

        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        assertFalse(occlusion.shouldDraw(owner, BOUNDS, true));

        // Let the cached result expire without completing its refresh probe.
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        assertTrue(occlusion.shouldDraw(owner, BOUNDS, true));
    }

    @Test
    void passingSamplesKeepTheOwnerVisible() {
        FakeBackend backend = new FakeBackend();
        ClientDepthOcclusion occlusion = new ClientDepthOcclusion(backend);
        UUID owner = UUID.randomUUID();
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        occlusion.shouldDraw(owner, BOUNDS, true);
        backend.complete(backend.issued.getLast(), true);

        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));

        assertTrue(occlusion.shouldDraw(owner, BOUNDS, true));
    }

    @Test
    void cameraOrBoundsChangesInvalidateAnOccludedResult() {
        FakeBackend backend = new FakeBackend();
        ClientDepthOcclusion occlusion = new ClientDepthOcclusion(backend);
        UUID owner = UUID.randomUUID();
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        occlusion.shouldDraw(owner, BOUNDS, true);
        backend.complete(backend.issued.getLast(), false);
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        assertFalse(occlusion.shouldDraw(owner, BOUNDS, true));

        occlusion.beginFrame(frame(), camera(100.0, 0.0, 0.0));
        assertTrue(occlusion.shouldDraw(owner, BOUNDS, true));
        backend.complete(backend.issued.getLast(), false);

        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        assertFalse(occlusion.shouldDraw(owner, BOUNDS, true));
        assertTrue(occlusion.shouldDraw(owner, BOUNDS.move(100.0, 0.0, 0.0), true));
    }

    @Test
    void disabledOwnersNeverProbe() {
        FakeBackend backend = new FakeBackend();
        ClientDepthOcclusion occlusion = new ClientDepthOcclusion(backend);
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));

        assertTrue(occlusion.shouldDraw(UUID.randomUUID(), BOUNDS, false));
        assertEquals(0, backend.issued.size());
    }

    @Test
    void probeBudgetLimitsOneFrame() {
        FakeBackend backend = new FakeBackend();
        ClientDepthOcclusion occlusion = new ClientDepthOcclusion(backend);
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));

        for (int index = 0; index < ClientDepthOcclusion.MAX_PROBES_PER_FRAME + 12; index++) {
            occlusion.shouldDraw(UUID.randomUUID(), BOUNDS, true);
        }

        assertEquals(ClientDepthOcclusion.MAX_PROBES_PER_FRAME, backend.issued.size());
    }

    @Test
    void anOwnerHasAtMostOneQueryInFlight() {
        FakeBackend backend = new FakeBackend();
        ClientDepthOcclusion occlusion = new ClientDepthOcclusion(backend);
        UUID owner = UUID.randomUUID();
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        occlusion.shouldDraw(owner, BOUNDS, true);

        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        assertTrue(occlusion.shouldDraw(owner, BOUNDS, true));

        assertEquals(1, backend.issued.size());
    }

    @Test
    void unresolvedQueriesAreDiscardedAfterTheAgeLimit() {
        FakeBackend backend = new FakeBackend();
        ClientDepthOcclusion occlusion = new ClientDepthOcclusion(backend);
        UUID owner = UUID.randomUUID();
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        occlusion.shouldDraw(owner, BOUNDS, true);
        int query = backend.issued.getLast();

        for (int index = 0; index <= ClientDepthOcclusion.MAX_QUERY_AGE_FRAMES; index++) {
            occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        }

        assertTrue(backend.deleted.contains(query));
        assertTrue(occlusion.shouldDraw(owner, BOUNDS, true));
    }

    @Test
    void forgetCancelsTheOwnerQuery() {
        FakeBackend backend = new FakeBackend();
        ClientDepthOcclusion occlusion = new ClientDepthOcclusion(backend);
        UUID owner = UUID.randomUUID();
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        occlusion.shouldDraw(owner, BOUNDS, true);
        int query = backend.issued.getLast();

        occlusion.forget(owner);

        assertTrue(backend.deleted.contains(query));
    }

    @Test
    void resetReleasesBackendStateAndStopsCulling() {
        FakeBackend backend = new FakeBackend();
        ClientDepthOcclusion occlusion = new ClientDepthOcclusion(backend);
        UUID owner = UUID.randomUUID();
        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));
        occlusion.shouldDraw(owner, BOUNDS, true);

        occlusion.reset();

        assertEquals(1, backend.resetCalls);
        assertTrue(occlusion.shouldDraw(owner, BOUNDS, true));
        assertEquals(1, backend.issued.size());
    }

    @Test
    void unsupportedBackendAlwaysDraws() {
        FakeBackend backend = new FakeBackend();
        backend.supported = false;
        ClientDepthOcclusion occlusion = new ClientDepthOcclusion(backend);

        occlusion.beginFrame(frame(), camera(0.0, 0.0, 0.0));

        assertTrue(occlusion.shouldDraw(UUID.randomUUID(), BOUNDS, true));
        assertEquals(0, backend.issued.size());
        assertTrue(occlusion.status().contains("inactive"));
    }

    private static SceneDepthFrame frame() {
        return FRAME;
    }

    private static EffectCameraSnapshot camera(double x, double y, double z) {
        return new EffectCameraSnapshot(new Vec3(x, y, z), new Matrix4f(), PROJECTION, 1920, 1080);
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
        private final List<Integer> issued = new ArrayList<>();
        private final List<Integer> deleted = new ArrayList<>();
        private boolean supported = true;
        private int nextQuery = 1;
        private int resetCalls;

        private void complete(int query, boolean anySamples) {
            this.results.put(query, anySamples);
        }

        @Override
        public boolean supported() {
            return this.supported;
        }

        @Override
        public int issue(DepthProbeRequest request) {
            int query = this.nextQuery++;
            this.issued.add(query);
            return query;
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
            this.deleted.add(query);
            this.results.remove(query);
        }

        @Override
        public void reset() {
            this.resetCalls++;
        }
    }
}
