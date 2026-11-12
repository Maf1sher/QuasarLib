package mafisher.quasar.api.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import mafisher.quasar.api.block.BlockProtectionContext;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.compat.HorizonProvider;
import mafisher.quasar.api.compat.PhysicsBridge;
import mafisher.quasar.api.compat.ProtectionBridge;
import mafisher.quasar.api.compat.RenderEnvironment;
import mafisher.quasar.api.compat.SceneDepthProvider;
import mafisher.quasar.api.compat.SceneDepthSource;
import mafisher.quasar.api.compat.ShaderPackProvider;
import mafisher.quasar.api.compat.ShaderPackState;
import mafisher.quasar.testing.GlobalStateTest;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class CompatProviderIsolationTest {
    @BeforeEach
    void resetBefore() {
        CompatRegistry.clearForTesting();
    }

    @AfterEach
    void reset() {
        CompatRegistry.clearForTesting();
    }

    @Test
    void skipsAProviderWhosePriorityThrows() {
        RenderTarget target = mock(RenderTarget.class);
        CompatRegistry.registerSceneDepthProvider(new SceneDepthProvider() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id("broken-priority");
            }

            @Override
            public int priority() {
                throw new IllegalStateException("priority lookup failed");
            }

            @Override
            public SceneDepthSource source() {
                return SceneDepthSource.HORIZON_LOD;
            }

            @Override
            public RenderTarget depthTarget() {
                return target;
            }
        });
        CompatRegistry.registerSceneDepthProvider(CompatTestProviders.depth(
            "working", 1, target, SceneDepthSource.HORIZON_LOD));

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertEquals(SceneDepthSource.HORIZON_LOD, environment.depthSource());
        assertEquals(
            List.of(CompatTestProviders.id("working")),
            environment.activeProviders()
        );
        assertSame(target, CompatRegistry.sceneDepthTarget(mock(RenderTarget.class)));
    }

    @Test
    void fallsBackToTheNextDepthProviderWhenTheBestTargetThrows() {
        RenderTarget fallback = mock(RenderTarget.class);
        RenderTarget low = mock(RenderTarget.class);
        CompatRegistry.registerSceneDepthProvider(new SceneDepthProvider() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id("broken-target");
            }

            @Override
            public int priority() {
                return 10;
            }

            @Override
            public SceneDepthSource source() {
                return SceneDepthSource.EXTERNAL_SHADERPACK;
            }

            @Override
            public RenderTarget depthTarget() {
                throw new IllegalStateException("depth target failed");
            }
        });
        CompatRegistry.registerSceneDepthProvider(CompatTestProviders.depth(
            "low", 1, low, SceneDepthSource.HORIZON_LOD));

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertEquals(SceneDepthSource.HORIZON_LOD, environment.depthSource());
        assertEquals(List.of(CompatTestProviders.id("low")), environment.activeProviders());
        assertSame(low, CompatRegistry.sceneDepthTarget(fallback));
    }

    @Test
    void fallsBackToTheNextShaderPackProviderWhenTheBestStateThrows() {
        CompatRegistry.registerShaderPackProvider(new ShaderPackProvider() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id("broken-shader");
            }

            @Override
            public int priority() {
                return 10;
            }

            @Override
            public ShaderPackState state() {
                throw new IllegalStateException("state lookup failed");
            }
        });
        CompatRegistry.registerShaderPackProvider(CompatTestProviders.shaderPack(
            "working-shader", 1, ShaderPackState.ACTIVE));

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertEquals(ShaderPackState.ACTIVE, environment.shaderPackState());
        assertEquals(
            List.of(CompatTestProviders.id("working-shader")),
            environment.activeProviders()
        );
    }

    @Test
    void skipsAShaderPackProviderWhosePriorityThrows() {
        CompatRegistry.registerShaderPackProvider(new ShaderPackProvider() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id("broken-shader-priority");
            }

            @Override
            public int priority() {
                throw new IllegalStateException("shader priority lookup failed");
            }

            @Override
            public ShaderPackState state() {
                return ShaderPackState.ACTIVE;
            }
        });
        CompatRegistry.registerShaderPackProvider(CompatTestProviders.shaderPack(
            "working-shader", 1, ShaderPackState.SHADOW_PASS));

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertEquals(ShaderPackState.SHADOW_PASS, environment.shaderPackState());
        assertEquals(
            List.of(CompatTestProviders.id("working-shader")),
            environment.activeProviders()
        );
    }

    @Test
    void treatsThrowingPhysicsBridgeAsUnavailable() {
        CompatRegistry.registerPhysicsBridge(new PhysicsBridge() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id("physics");
            }

            @Override
            public boolean available() {
                throw new IllegalStateException("physics availability failed");
            }
        });

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertFalse(environment.physicsAvailable());
        assertFalse(CompatRegistry.physics().available());
        assertTrue(environment.activeProviders().isEmpty());
    }

    @Test
    void treatsThrowingProtectionBridgeAsUnavailable() {
        CompatRegistry.registerProtectionBridge(new ProtectionBridge() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id("protection");
            }

            @Override
            public boolean available() {
                throw new IllegalStateException("protection availability failed");
            }

            @Override
            public boolean allows(BlockProtectionContext context) {
                return true;
            }
        });

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertFalse(environment.protectionAvailable());
        assertFalse(CompatRegistry.protection().available());
        assertTrue(CompatRegistry.hasProtectionBridge());
        assertTrue(environment.activeProviders().isEmpty());
    }

    @Test
    void excludesInactiveShaderPackFromActiveProviders() {
        CompatRegistry.registerShaderPackProvider(CompatTestProviders.shaderPack(
            "inactive-shader", 1, ShaderPackState.INACTIVE));

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertEquals(ShaderPackState.INACTIVE, environment.shaderPackState());
        assertTrue(environment.activeProviders().isEmpty());
    }

    @Test
    void excludesFailingHorizonProviderFromActiveProviders() {
        CompatRegistry.registerHorizonProvider(new HorizonProvider() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id("broken-horizon");
            }

            @Override
            public int priority() {
                return 1;
            }

            @Override
            public double horizonDistance() {
                throw new IllegalStateException("horizon lookup failed");
            }

            @Override
            public double farPlane() {
                return 1024.0;
            }
        });

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertFalse(environment.hasHorizonProvider());
        assertEquals(256.0, environment.horizonDistance());
        assertEquals(256.0, environment.farPlane());
        assertTrue(environment.activeProviders().isEmpty());
    }

    @Test
    void listsOnlyContributingProvidersInOrder() {
        CompatRegistry.registerSceneDepthProvider(CompatTestProviders.depth(
            "depth", 5, mock(RenderTarget.class), SceneDepthSource.EXTERNAL_SHADERPACK));
        CompatRegistry.registerHorizonProvider(CompatTestProviders.horizon("horizon", 4096.0, 8192.0));
        CompatRegistry.registerShaderPackProvider(CompatTestProviders.shaderPack(
            "shader", 1, ShaderPackState.ACTIVE));
        CompatRegistry.registerPhysicsBridge(CompatTestProviders.physics(
            "physics", true, new AtomicInteger()));
        CompatRegistry.registerProtectionBridge(CompatTestProviders.protection(
            "protection", true, new AtomicInteger()));

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertEquals(
            List.of(
                CompatTestProviders.id("depth"),
                CompatTestProviders.id("horizon"),
                CompatTestProviders.id("shader"),
                CompatTestProviders.id("physics"),
                CompatTestProviders.id("protection")
            ),
            environment.activeProviders()
        );
    }

    @Test
    void callsBridgeAvailabilityOncePerEnvironment() {
        AtomicInteger physicsCalls = new AtomicInteger();
        AtomicInteger protectionCalls = new AtomicInteger();
        CompatRegistry.registerPhysicsBridge(CompatTestProviders.physics("physics", true, physicsCalls));
        CompatRegistry.registerProtectionBridge(CompatTestProviders.protection(
            "protection", true, protectionCalls));

        CompatRegistry.environment(256.0);

        assertEquals(1, physicsCalls.get());
        assertEquals(1, protectionCalls.get());
    }

    @Test
    void doesNotCallProviderIdsDuringResolution() {
        AtomicInteger idCalls = new AtomicInteger();
        CompatRegistry.registerHorizonProvider(new HorizonProvider() {
            @Override
            public ResourceLocation id() {
                idCalls.incrementAndGet();
                return CompatTestProviders.id("horizon");
            }

            @Override
            public int priority() {
                return 1;
            }

            @Override
            public double horizonDistance() {
                return 1024.0;
            }

            @Override
            public double farPlane() {
                return 1024.0;
            }
        });
        assertEquals(1, idCalls.get());

        CompatRegistry.environment(256.0);

        assertEquals(1, idCalls.get());
    }
}
