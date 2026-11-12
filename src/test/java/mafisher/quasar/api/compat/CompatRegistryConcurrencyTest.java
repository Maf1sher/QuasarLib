package mafisher.quasar.api.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.compat.RenderEnvironment;
import mafisher.quasar.api.compat.SceneDepthProvider;
import mafisher.quasar.api.compat.SceneDepthSource;
import mafisher.quasar.testing.GlobalStateTest;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class CompatRegistryConcurrencyTest {
    @BeforeEach
    void resetBefore() {
        CompatRegistry.clearForTesting();
    }

    @AfterEach
    void reset() {
        CompatRegistry.clearForTesting();
    }

    @Test
    void resolvesProviderCallbacksOutsideTheRegistryLock() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RenderTarget target = mock(RenderTarget.class);
        CompatRegistry.registerSceneDepthProvider(new SceneDepthProvider() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id("blocking");
            }

            @Override
            public int priority() {
                return 1;
            }

            @Override
            public SceneDepthSource source() {
                return SceneDepthSource.HORIZON_LOD;
            }

            @Override
            public RenderTarget depthTarget() {
                entered.countDown();
                await(release);
                return target;
            }
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RenderEnvironment> resolution = executor.submit(
                () -> CompatRegistry.environment(256.0));
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            Future<Boolean> registryRead = executor.submit(CompatRegistry::hasProtectionBridge);
            assertFalse(registryRead.get(2, TimeUnit.SECONDS));

            release.countDown();
            assertEquals(
                SceneDepthSource.HORIZON_LOD,
                resolution.get(5, TimeUnit.SECONDS).depthSource()
            );
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
