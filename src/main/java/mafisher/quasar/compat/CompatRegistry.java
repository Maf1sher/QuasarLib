package mafisher.quasar.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import mafisher.quasar.QuasarLib;
import mafisher.quasar.effect.block.BlockProtectionContext;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registration and per-frame resolution of optional capability providers.
 *
 * <p>Providers are registered during setup through
 * {@link RegisterCompatProvidersEvent} (client) and
 * {@link RegisterCompatBridgesEvent} (common), then frozen. Resolution is
 * deterministic: higher priority wins, duplicate IDs are rejected, and exactly
 * one horizon provider may be active. A missing or failing provider degrades
 * the affected capability instead of failing an effect.</p>
 */
public final class CompatRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuasarLib.MOD_ID);
    private static final Object LOCK = new Object();
    private static final double FALLBACK_DISTANCE = 256.0;
    private static final Map<ResourceLocation, SceneDepthProvider> DEPTH_PROVIDERS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ShaderPackProvider> SHADER_PACK_PROVIDERS = new LinkedHashMap<>();
    private static HorizonProvider horizonProvider;
    private static PhysicsBridge physicsBridge = PhysicsBridge.vanilla();
    private static ProtectionBridge protectionBridge = ProtectionBridge.vanilla();
    private static boolean physicsRegistered;
    private static boolean protectionRegistered;
    private static boolean clientFrozen;
    private static boolean bridgesFrozen;
    private static boolean loggedDepthFailure;
    private static boolean loggedHorizonFailure;
    private static boolean loggedShaderPackFailure;

    private CompatRegistry() {
    }

    /** Registers a scene-depth provider. Must run before client providers are frozen. */
    public static void registerSceneDepthProvider(SceneDepthProvider provider) {
        Objects.requireNonNull(provider, "provider");
        synchronized (LOCK) {
            requireClientOpen();
            ResourceLocation id = Objects.requireNonNull(provider.id(), "provider id");
            requireUnusedId(id);
            DEPTH_PROVIDERS.put(id, provider);
        }
    }

    /**
     * Registers the single active horizon provider.
     *
     * @throws IllegalStateException if another horizon provider is already registered
     */
    public static void registerHorizonProvider(HorizonProvider provider) {
        Objects.requireNonNull(provider, "provider");
        synchronized (LOCK) {
            requireClientOpen();
            ResourceLocation id = Objects.requireNonNull(provider.id(), "provider id");
            requireUnusedId(id);
            if (horizonProvider != null) {
                throw new IllegalStateException(
                    "A horizon provider is already registered: " + horizonProvider.id()
                );
            }
            horizonProvider = provider;
        }
    }

    /** Registers a shaderpack-state provider. Must run before client providers are frozen. */
    public static void registerShaderPackProvider(ShaderPackProvider provider) {
        Objects.requireNonNull(provider, "provider");
        synchronized (LOCK) {
            requireClientOpen();
            ResourceLocation id = Objects.requireNonNull(provider.id(), "provider id");
            requireUnusedId(id);
            SHADER_PACK_PROVIDERS.put(id, provider);
        }
    }

    /** Registers the optional physics bridge. Must run before bridges are frozen. */
    public static void registerPhysicsBridge(PhysicsBridge bridge) {
        Objects.requireNonNull(bridge, "bridge");
        synchronized (LOCK) {
            requireBridgesOpen();
            if (physicsRegistered) {
                throw new IllegalStateException(
                    "A physics bridge is already registered: " + physicsBridge.id()
                );
            }
            physicsBridge = bridge;
            physicsRegistered = true;
        }
    }

    /** Registers the optional protection bridge. Must run before bridges are frozen. */
    public static void registerProtectionBridge(ProtectionBridge bridge) {
        Objects.requireNonNull(bridge, "bridge");
        synchronized (LOCK) {
            requireBridgesOpen();
            if (protectionRegistered) {
                throw new IllegalStateException(
                    "A protection bridge is already registered: " + protectionBridge.id()
                );
            }
            protectionBridge = new GuardedProtectionBridge(bridge);
            protectionRegistered = true;
        }
    }

    /** Closes client provider registration. */
    public static void freezeClientProviders() {
        synchronized (LOCK) {
            clientFrozen = true;
        }
    }

    /** Closes bridge registration. */
    public static void freezeBridges() {
        synchronized (LOCK) {
            bridgesFrozen = true;
        }
    }

    /**
     * Resolves one immutable environment for the current frame.
     *
     * @param vanillaFarPlane the vanilla far plane used when no horizon provider is active
     */
    public static RenderEnvironment environment(double vanillaFarPlane) {
        synchronized (LOCK) {
            double vanilla = sanitizeDistance(vanillaFarPlane);
            double horizonDistance = vanilla;
            double farPlane = vanilla;
            boolean horizonProvided = false;
            HorizonProvider horizon = horizonProvider;
            if (horizon != null) {
                try {
                    horizonDistance = Math.max(vanilla, sanitizeDistance(horizon.horizonDistance()));
                    farPlane = Math.max(vanilla, sanitizeDistance(horizon.farPlane()));
                    horizonProvided = true;
                } catch (RuntimeException exception) {
                    if (!loggedHorizonFailure) {
                        loggedHorizonFailure = true;
                        LOGGER.warn("Horizon provider {} failed; using the vanilla far plane",
                            horizon.id(), exception);
                    }
                }
            }

            SceneDepthSource depthSource = SceneDepthSource.MAIN_TARGET;
            SceneDepthProvider depth = bestDepthProvider();
            if (depth != null) {
                try {
                    if (depth.depthTarget() != null) {
                        depthSource = depth.source();
                    }
                } catch (RuntimeException exception) {
                    logDepthFailure(depth, exception);
                }
            }

            ShaderPackState shaderPackState = ShaderPackState.INACTIVE;
            ShaderPackProvider shaderPack = bestShaderPackProvider();
            if (shaderPack != null) {
                try {
                    shaderPackState = shaderPack.state();
                } catch (RuntimeException exception) {
                    if (!loggedShaderPackFailure) {
                        loggedShaderPackFailure = true;
                        LOGGER.warn("Shaderpack provider {} failed; assuming no shaderpack",
                            shaderPack.id(), exception);
                    }
                }
            }

            List<ResourceLocation> active = new ArrayList<>();
            active.addAll(DEPTH_PROVIDERS.keySet());
            if (horizon != null) {
                active.add(horizon.id());
            }
            active.addAll(SHADER_PACK_PROVIDERS.keySet());
            if (physicsBridge.available()) {
                active.add(physicsBridge.id());
            }
            if (protectionBridge.available()) {
                active.add(protectionBridge.id());
            }
            return new RenderEnvironment(
                horizonDistance,
                farPlane,
                horizonProvided,
                depthSource,
                shaderPackState,
                FogPolicy.RESPECT,
                physicsBridge.available(),
                protectionBridge.available(),
                active
            );
        }
    }

    /**
     * Resolves the depth target for composition: the highest-priority provider
     * that supplies one this frame, or {@code fallback} when none does.
     */
    public static RenderTarget sceneDepthTarget(RenderTarget fallback) {
        Objects.requireNonNull(fallback, "fallback");
        synchronized (LOCK) {
            SceneDepthProvider depth = bestDepthProvider();
            if (depth != null) {
                try {
                    RenderTarget target = depth.depthTarget();
                    if (target != null) {
                        return target;
                    }
                } catch (RuntimeException exception) {
                    logDepthFailure(depth, exception);
                }
            }
            return fallback;
        }
    }

    /** Returns the active physics bridge; the vanilla no-op bridge when absent. */
    public static PhysicsBridge physics() {
        synchronized (LOCK) {
            return physicsBridge;
        }
    }

    /** Returns the active protection bridge; the block-protection fallback when absent. */
    public static ProtectionBridge protection() {
        synchronized (LOCK) {
            return protectionBridge;
        }
    }

    /** Whether a custom protection bridge is registered; {@code false} for the vanilla fallback. */
    public static boolean hasProtectionBridge() {
        synchronized (LOCK) {
            return protectionRegistered;
        }
    }

    private static void requireClientOpen() {
        if (clientFrozen) {
            throw new IllegalStateException("Client compat provider registration is already closed");
        }
    }

    private static void requireBridgesOpen() {
        if (bridgesFrozen) {
            throw new IllegalStateException("Compat bridge registration is already closed");
        }
    }

    private static void requireUnusedId(ResourceLocation id) {
        if (DEPTH_PROVIDERS.containsKey(id) || SHADER_PACK_PROVIDERS.containsKey(id)
            || (horizonProvider != null && horizonProvider.id().equals(id))) {
            throw new IllegalStateException("A compat provider with this ID is already registered: " + id);
        }
    }

    private static double sanitizeDistance(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return FALLBACK_DISTANCE;
        }
        return Math.min(value, 1_000_000.0);
    }

    private static SceneDepthProvider bestDepthProvider() {
        SceneDepthProvider best = null;
        for (SceneDepthProvider provider : DEPTH_PROVIDERS.values()) {
            if (best == null || provider.priority() > best.priority()) {
                best = provider;
            }
        }
        return best;
    }

    private static ShaderPackProvider bestShaderPackProvider() {
        ShaderPackProvider best = null;
        for (ShaderPackProvider provider : SHADER_PACK_PROVIDERS.values()) {
            if (best == null || provider.priority() > best.priority()) {
                best = provider;
            }
        }
        return best;
    }

    private static void logDepthFailure(SceneDepthProvider provider, RuntimeException exception) {
        if (!loggedDepthFailure) {
            loggedDepthFailure = true;
            LOGGER.warn("Scene depth provider {} failed; using the main target",
                provider.id(), exception);
        }
    }

    static void clearForTesting() {
        synchronized (LOCK) {
            DEPTH_PROVIDERS.clear();
            SHADER_PACK_PROVIDERS.clear();
            horizonProvider = null;
            physicsBridge = PhysicsBridge.vanilla();
            protectionBridge = ProtectionBridge.vanilla();
            physicsRegistered = false;
            protectionRegistered = false;
            clientFrozen = false;
            bridgesFrozen = false;
            loggedDepthFailure = false;
            loggedHorizonFailure = false;
            loggedShaderPackFailure = false;
        }
    }

    /** Wraps a bridge so a throwing provider denies the change and logs once. */
    private static final class GuardedProtectionBridge implements ProtectionBridge {
        private final ProtectionBridge delegate;
        private boolean loggedFailure;

        private GuardedProtectionBridge(ProtectionBridge delegate) {
            this.delegate = delegate;
        }

        @Override
        public ResourceLocation id() {
            return this.delegate.id();
        }

        @Override
        public boolean available() {
            return this.delegate.available();
        }

        @Override
        public boolean allows(BlockProtectionContext context) {
            try {
                return this.delegate.allows(context);
            } catch (RuntimeException exception) {
                if (!this.loggedFailure) {
                    this.loggedFailure = true;
                    LOGGER.error("Protection bridge {} failed; denying block changes from it",
                        this.delegate.id(), exception);
                }
                return false;
            }
        }
    }
}
