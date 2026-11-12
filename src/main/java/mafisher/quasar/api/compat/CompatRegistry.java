package mafisher.quasar.api.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registration and per-frame resolution of optional capability providers.
 *
 * <p>Providers are registered through {@link RegisterCompatProvidersEvent} when client loading
 * completes and through {@link RegisterCompatBridgesEvent} during common setup, then frozen.
 * Registration only
 * mutates the registry under its lock; resolution snapshots the registry under
 * the lock and invokes provider callbacks after releasing it. Resolution is
 * deterministic: higher priority wins, a failing provider is skipped, duplicate
 * IDs are rejected across every provider kind, and exactly one horizon provider
 * may be active. A missing or failing provider degrades the affected capability
 * instead of failing an effect.</p>
 */
public final class CompatRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompatRegistry.class);
    private static final Object LOCK = new Object();
    private static final Map<ResourceLocation, DepthEntry> DEPTH_PROVIDERS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ShaderPackEntry> SHADER_PACK_PROVIDERS = new LinkedHashMap<>();
    private static final AtomicBoolean LOGGED_DEPTH_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_HORIZON_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_SHADER_PACK_FAILURE = new AtomicBoolean();
    private static HorizonEntry horizonProvider;
    private static PhysicsBridge physicsBridge = PhysicsBridge.vanilla();
    private static ProtectionBridge protectionBridge = ProtectionBridge.vanilla();
    private static boolean physicsRegistered;
    private static boolean protectionRegistered;
    private static boolean clientFrozen;
    private static boolean bridgesFrozen;

    private CompatRegistry() {
    }

    /** Registers a scene-depth provider. Must run before client providers are frozen. */
    public static void registerSceneDepthProvider(SceneDepthProvider provider) {
        Objects.requireNonNull(provider, "provider");
        synchronized (LOCK) {
            requireClientOpen();
            ResourceLocation id = Objects.requireNonNull(provider.id(), "provider id");
            requireUnusedId(id);
            DEPTH_PROVIDERS.put(id, new DepthEntry(id, provider));
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
            if (horizonProvider != null) {
                throw new IllegalStateException(
                    "A horizon provider is already registered: " + horizonProvider.id()
                );
            }
            requireUnusedId(id);
            horizonProvider = new HorizonEntry(id, provider);
        }
    }

    /** Registers a shaderpack-state provider. Must run before client providers are frozen. */
    public static void registerShaderPackProvider(ShaderPackProvider provider) {
        Objects.requireNonNull(provider, "provider");
        synchronized (LOCK) {
            requireClientOpen();
            ResourceLocation id = Objects.requireNonNull(provider.id(), "provider id");
            requireUnusedId(id);
            SHADER_PACK_PROVIDERS.put(id, new ShaderPackEntry(id, provider));
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
            ResourceLocation id = Objects.requireNonNull(bridge.id(), "bridge id");
            requireUnusedId(id);
            physicsBridge = new GuardedPhysicsBridge(id, bridge);
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
            ResourceLocation id = Objects.requireNonNull(bridge.id(), "bridge id");
            requireUnusedId(id);
            protectionBridge = new GuardedProtectionBridge(id, bridge);
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
        return resolveEnvironment(snapshot(), vanillaFarPlane);
    }

    /**
     * Resolves the depth target for composition: the highest-priority provider
     * that supplies one this frame, or {@code fallback} when none does.
     */
    public static RenderTarget sceneDepthTarget(RenderTarget fallback) {
        Objects.requireNonNull(fallback, "fallback");
        DepthResolution depth = resolveDepth(snapshot().depthProviders());
        return depth == null ? fallback : depth.target();
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

    private static CompatSnapshot snapshot() {
        synchronized (LOCK) {
            return new CompatSnapshot(
                List.copyOf(DEPTH_PROVIDERS.values()),
                horizonProvider,
                List.copyOf(SHADER_PACK_PROVIDERS.values()),
                physicsBridge,
                protectionBridge
            );
        }
    }

    private static RenderEnvironment resolveEnvironment(CompatSnapshot snapshot, double vanillaFarPlane) {
        double vanilla = sanitizeDistance(vanillaFarPlane);
        HorizonResolution horizon = resolveHorizon(snapshot.horizonProvider());
        DepthResolution depth = resolveDepth(snapshot.depthProviders());
        ShaderPackResolution shaderPack = resolveShaderPack(snapshot.shaderPackProviders());
        boolean physicsAvailable = snapshot.physicsBridge().available();
        boolean protectionAvailable = snapshot.protectionBridge().available();
        List<ResourceLocation> active = activeProviders(
            snapshot, depth, horizon, shaderPack, physicsAvailable, protectionAvailable);
        return new RenderEnvironment(
            horizon.provided() ? Math.max(vanilla, horizon.distance()) : vanilla,
            horizon.provided() ? Math.max(vanilla, horizon.farPlane()) : vanilla,
            horizon.provided(),
            depth == null ? SceneDepthSource.MAIN_TARGET : depth.source(),
            shaderPack == null ? ShaderPackState.INACTIVE : shaderPack.state(),
            FogPolicy.RESPECT,
            physicsAvailable,
            protectionAvailable,
            active
        );
    }

    private static List<ResourceLocation> activeProviders(CompatSnapshot snapshot, DepthResolution depth,
        HorizonResolution horizon, ShaderPackResolution shaderPack, boolean physicsAvailable,
        boolean protectionAvailable) {
        List<ResourceLocation> active = new ArrayList<>(5);
        if (depth != null) {
            active.add(depth.id());
        }
        if (horizon.provided()) {
            active.add(horizon.id());
        }
        if (shaderPack != null && shaderPack.state() != ShaderPackState.INACTIVE) {
            active.add(shaderPack.id());
        }
        if (physicsAvailable) {
            active.add(snapshot.physicsBridge().id());
        }
        if (protectionAvailable) {
            active.add(snapshot.protectionBridge().id());
        }
        return active;
    }

    private static HorizonResolution resolveHorizon(HorizonEntry entry) {
        if (entry == null) {
            return HorizonResolution.none();
        }
        try {
            return new HorizonResolution(
                true,
                entry.id(),
                sanitizeDistance(entry.provider().horizonDistance()),
                sanitizeDistance(entry.provider().farPlane())
            );
        } catch (RuntimeException exception) {
            logHorizonFailure(entry.id(), exception);
            return HorizonResolution.none();
        }
    }

    private static DepthResolution resolveDepth(List<DepthEntry> entries) {
        List<Prioritized<DepthEntry>> candidates = prioritizeDepth(entries);
        for (Prioritized<DepthEntry> candidate : candidates) {
            DepthEntry entry = candidate.value();
            try {
                RenderTarget target = entry.provider().depthTarget();
                if (target == null) {
                    continue;
                }
                SceneDepthSource source = entry.provider().source();
                if (source == null || source == SceneDepthSource.MAIN_TARGET) {
                    continue;
                }
                return new DepthResolution(entry.id(), target, source);
            } catch (RuntimeException exception) {
                logDepthFailure(entry.id(), exception);
            }
        }
        return null;
    }

    private static List<Prioritized<DepthEntry>> prioritizeDepth(List<DepthEntry> entries) {
        List<Prioritized<DepthEntry>> candidates = new ArrayList<>(entries.size());
        for (DepthEntry entry : entries) {
            try {
                candidates.add(new Prioritized<>(entry, entry.provider().priority()));
            } catch (RuntimeException exception) {
                logDepthFailure(entry.id(), exception);
            }
        }
        candidates.sort((left, right) -> Integer.compare(right.priority(), left.priority()));
        return candidates;
    }

    private static ShaderPackResolution resolveShaderPack(List<ShaderPackEntry> entries) {
        List<Prioritized<ShaderPackEntry>> candidates = prioritizeShaderPack(entries);
        for (Prioritized<ShaderPackEntry> candidate : candidates) {
            ShaderPackEntry entry = candidate.value();
            try {
                ShaderPackState state = entry.provider().state();
                if (state == null) {
                    throw new IllegalStateException("Shaderpack provider returned a null state");
                }
                return new ShaderPackResolution(entry.id(), state);
            } catch (RuntimeException exception) {
                logShaderPackFailure(entry.id(), exception);
            }
        }
        return null;
    }

    private static List<Prioritized<ShaderPackEntry>> prioritizeShaderPack(List<ShaderPackEntry> entries) {
        List<Prioritized<ShaderPackEntry>> candidates = new ArrayList<>(entries.size());
        for (ShaderPackEntry entry : entries) {
            try {
                candidates.add(new Prioritized<>(entry, entry.provider().priority()));
            } catch (RuntimeException exception) {
                logShaderPackFailure(entry.id(), exception);
            }
        }
        candidates.sort((left, right) -> Integer.compare(right.priority(), left.priority()));
        return candidates;
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
        if (id.equals(PhysicsBridge.VANILLA_ID) || id.equals(ProtectionBridge.VANILLA_ID)) {
            throw new IllegalStateException("The compat fallback ID is reserved: " + id);
        }
        if (idInUse(id)) {
            throw new IllegalStateException("A compat provider with this ID is already registered: " + id);
        }
    }

    private static boolean idInUse(ResourceLocation id) {
        if (DEPTH_PROVIDERS.containsKey(id) || SHADER_PACK_PROVIDERS.containsKey(id)) {
            return true;
        }
        if (horizonProvider != null && horizonProvider.id().equals(id)) {
            return true;
        }
        return (physicsRegistered && physicsBridge.id().equals(id))
            || (protectionRegistered && protectionBridge.id().equals(id));
    }

    private static double sanitizeDistance(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return RenderEnvironment.VANILLA.farPlane();
        }
        return Math.min(value, 1_000_000.0);
    }

    private static void logDepthFailure(ResourceLocation id, RuntimeException exception) {
        if (LOGGED_DEPTH_FAILURE.compareAndSet(false, true)) {
            LOGGER.warn("Scene depth provider {} failed; skipping it", id, exception);
        }
    }

    private static void logHorizonFailure(ResourceLocation id, RuntimeException exception) {
        if (LOGGED_HORIZON_FAILURE.compareAndSet(false, true)) {
            LOGGER.warn("Horizon provider {} failed; using the vanilla far plane", id, exception);
        }
    }

    private static void logShaderPackFailure(ResourceLocation id, RuntimeException exception) {
        if (LOGGED_SHADER_PACK_FAILURE.compareAndSet(false, true)) {
            LOGGER.warn("Shaderpack provider {} failed; treating it as inactive", id, exception);
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
            LOGGED_DEPTH_FAILURE.set(false);
            LOGGED_HORIZON_FAILURE.set(false);
            LOGGED_SHADER_PACK_FAILURE.set(false);
        }
    }

    private record DepthEntry(ResourceLocation id, SceneDepthProvider provider) {
    }

    private record ShaderPackEntry(ResourceLocation id, ShaderPackProvider provider) {
    }

    private record HorizonEntry(ResourceLocation id, HorizonProvider provider) {
    }

    private record CompatSnapshot(List<DepthEntry> depthProviders, HorizonEntry horizonProvider,
        List<ShaderPackEntry> shaderPackProviders, PhysicsBridge physicsBridge,
        ProtectionBridge protectionBridge) {
    }

    private record HorizonResolution(boolean provided, ResourceLocation id, double distance, double farPlane) {
        private static HorizonResolution none() {
            return new HorizonResolution(false, null, 0.0, 0.0);
        }
    }

    private record DepthResolution(ResourceLocation id, RenderTarget target, SceneDepthSource source) {
    }

    private record ShaderPackResolution(ResourceLocation id, ShaderPackState state) {
    }

    private record Prioritized<T>(T value, int priority) {
    }
}
