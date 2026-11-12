package mafisher.quasar.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.concurrent.atomic.AtomicInteger;
import mafisher.quasar.api.block.BlockProtectionContext;
import mafisher.quasar.api.compat.HorizonProvider;
import mafisher.quasar.api.compat.PhysicsBridge;
import mafisher.quasar.api.compat.ProtectionBridge;
import mafisher.quasar.api.compat.SceneDepthProvider;
import mafisher.quasar.api.compat.SceneDepthSource;
import mafisher.quasar.api.compat.ShaderPackProvider;
import mafisher.quasar.api.compat.ShaderPackState;
import net.minecraft.resources.ResourceLocation;

/** Small provider fixtures shared by the compat registry tests. */
final class CompatTestProviders {
    private CompatTestProviders() {
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    static SceneDepthProvider depth(String path, int priority, RenderTarget target, SceneDepthSource source) {
        return new SceneDepthProvider() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id(path);
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public RenderTarget depthTarget() {
                return target;
            }

            @Override
            public SceneDepthSource source() {
                return source;
            }
        };
    }

    static ShaderPackProvider shaderPack(String path, int priority, ShaderPackState state) {
        return new ShaderPackProvider() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id(path);
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public ShaderPackState state() {
                return state;
            }
        };
    }

    static HorizonProvider horizon(String path, double distance, double farPlane) {
        return new HorizonProvider() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id(path);
            }

            @Override
            public int priority() {
                return 1;
            }

            @Override
            public double horizonDistance() {
                return distance;
            }

            @Override
            public double farPlane() {
                return farPlane;
            }
        };
    }

    static PhysicsBridge physics(String path, boolean available, AtomicInteger calls) {
        return new PhysicsBridge() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id(path);
            }

            @Override
            public boolean available() {
                calls.incrementAndGet();
                return available;
            }
        };
    }

    static ProtectionBridge protection(String path, boolean available, AtomicInteger calls) {
        return new ProtectionBridge() {
            @Override
            public ResourceLocation id() {
                return CompatTestProviders.id(path);
            }

            @Override
            public boolean available() {
                calls.incrementAndGet();
                return available;
            }

            @Override
            public boolean allows(BlockProtectionContext context) {
                return true;
            }
        };
    }
}
