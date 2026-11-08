package mafisher.quasar.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mafisher.quasar.effect.block.BlockProtectionContext;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CompatRegistryTest {
    @AfterEach
    void reset() {
        CompatRegistry.clearForTesting();
    }

    @Test
    void fallsBackToVanillaWhenNoProviderIsRegistered() {
        RenderEnvironment environment = CompatRegistry.environment(512.0);

        assertEquals(SceneDepthSource.MAIN_TARGET, environment.depthSource());
        assertFalse(environment.hasDepthProvider());
        assertFalse(environment.hasHorizonProvider());
        assertEquals(512.0, environment.horizonDistance());
        assertEquals(512.0, environment.farPlane());
        assertEquals(ShaderPackState.INACTIVE, environment.shaderPackState());
        assertEquals(FogPolicy.RESPECT, environment.fogPolicy());
        assertFalse(environment.physicsAvailable());
        assertFalse(environment.protectionAvailable());
        assertTrue(environment.activeProviders().isEmpty());
        assertTrue(environment.screenCompositionAllowed());
    }

    @Test
    void usesTheRegisteredHorizonProvider() {
        CompatRegistry.registerHorizonProvider(new HorizonProvider() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("test", "horizon");
            }

            @Override
            public int priority() {
                return 10;
            }

            @Override
            public double horizonDistance() {
                return 4096.0;
            }

            @Override
            public double farPlane() {
                return 8192.0;
            }
        });

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertTrue(environment.hasHorizonProvider());
        assertEquals(4096.0, environment.horizonDistance());
        assertEquals(8192.0, environment.farPlane());
        assertTrue(environment.activeProviders().contains(
            ResourceLocation.fromNamespaceAndPath("test", "horizon")));
    }

    @Test
    void neverShrinksTheVanillaFarPlane() {
        CompatRegistry.registerHorizonProvider(new HorizonProvider() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("test", "small");
            }

            @Override
            public int priority() {
                return 1;
            }

            @Override
            public double horizonDistance() {
                return 32.0;
            }

            @Override
            public double farPlane() {
                return 64.0;
            }
        });

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertEquals(256.0, environment.horizonDistance());
        assertEquals(256.0, environment.farPlane());
    }

    @Test
    void allowsOnlyOneHorizonProvider() {
        HorizonProvider provider = new HorizonProvider() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("test", "horizon");
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
        };
        CompatRegistry.registerHorizonProvider(provider);

        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerHorizonProvider(provider));
    }

    @Test
    void rejectsDuplicateIdsAcrossProviderKinds() {
        CompatRegistry.registerSceneDepthProvider(new TestDepthProvider("test", "shared", 1, null));

        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerShaderPackProvider(
            new ShaderPackProvider() {
                @Override
                public ResourceLocation id() {
                    return ResourceLocation.fromNamespaceAndPath("test", "shared");
                }

                @Override
                public int priority() {
                    return 1;
                }

                @Override
                public ShaderPackState state() {
                    return ShaderPackState.INACTIVE;
                }
            }));
    }

    @Test
    void prefersTheHighestPriorityDepthProvider() {
        RenderTarget fallback = mock(RenderTarget.class);
        RenderTarget low = mock(RenderTarget.class);
        RenderTarget high = mock(RenderTarget.class);
        CompatRegistry.registerSceneDepthProvider(new TestDepthProvider(
            "test", "low", 1, low, SceneDepthSource.HORIZON_LOD));
        CompatRegistry.registerSceneDepthProvider(new TestDepthProvider(
            "test", "high", 10, high, SceneDepthSource.EXTERNAL_SHADERPACK));

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertEquals(SceneDepthSource.EXTERNAL_SHADERPACK, environment.depthSource());
        assertTrue(environment.hasDepthProvider());
        assertEquals(high, CompatRegistry.sceneDepthTarget(fallback));
    }

    @Test
    void fallsBackWhenTheBestDepthProviderHasNoTargetThisFrame() {
        RenderTarget fallback = mock(RenderTarget.class);
        CompatRegistry.registerSceneDepthProvider(new TestDepthProvider(
            "test", "shadow", 10, null, SceneDepthSource.EXTERNAL_SHADERPACK));

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertEquals(SceneDepthSource.MAIN_TARGET, environment.depthSource());
        assertEquals(fallback, CompatRegistry.sceneDepthTarget(fallback));
    }

    @Test
    void reportsShaderPackShadowPass() {
        CompatRegistry.registerShaderPackProvider(new ShaderPackProvider() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("test", "iris");
            }

            @Override
            public int priority() {
                return 1;
            }

            @Override
            public ShaderPackState state() {
                return ShaderPackState.SHADOW_PASS;
            }
        });

        RenderEnvironment environment = CompatRegistry.environment(256.0);

        assertTrue(environment.shadowPass());
        assertFalse(environment.screenCompositionAllowed());
    }

    @Test
    void registeredProtectionBridgeDeniesWhenItThrows() {
        CompatRegistry.registerProtectionBridge(new ProtectionBridge() {
            @Override
            public ResourceLocation id() {
                return ResourceLocation.fromNamespaceAndPath("test", "claims");
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean allows(BlockProtectionContext context) {
                throw new IllegalStateException("claim lookup failed");
            }
        });

        assertTrue(CompatRegistry.environment(256.0).protectionAvailable());
        assertFalse(CompatRegistry.protection().allows(mock(BlockProtectionContext.class)));
    }

    @Test
    void closesRegistrationAfterFreeze() {
        CompatRegistry.freezeClientProviders();
        CompatRegistry.freezeBridges();

        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerHorizonProvider(
            new HorizonProvider() {
                @Override
                public ResourceLocation id() {
                    return ResourceLocation.fromNamespaceAndPath("test", "late");
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
            }));
        assertThrows(IllegalStateException.class, () -> CompatRegistry.registerPhysicsBridge(
            PhysicsBridge.vanilla()));
    }

    private record TestDepthProvider(
        String namespace,
        String path,
        int priority,
        RenderTarget target,
        SceneDepthSource source
    ) implements SceneDepthProvider {
        private TestDepthProvider(String namespace, String path, int priority, RenderTarget target) {
            this(namespace, path, priority, target, SceneDepthSource.MAIN_TARGET);
        }

        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(this.namespace, this.path);
        }

        @Override
        public RenderTarget depthTarget() {
            return this.target;
        }
    }
}
