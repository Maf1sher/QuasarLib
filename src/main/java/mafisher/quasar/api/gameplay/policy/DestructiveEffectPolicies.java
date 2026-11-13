package mafisher.quasar.api.gameplay.policy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import mafisher.quasar.QuasarConfig;
import mafisher.quasar.QuasarLib;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Internal registry that ANDs every destructive-effect policy. */
@ApiStatus.Internal
public final class DestructiveEffectPolicies {
    private static final Logger LOGGER = LoggerFactory.getLogger(DestructiveEffectPolicies.class);
    private static final ResourceLocation CONFIG_POLICY_ID =
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "config");
    private static final Map<ResourceLocation, DestructiveEffectPolicy> POLICIES = new LinkedHashMap<>();
    private static boolean frozen;

    private DestructiveEffectPolicies() {
    }

    public static void registerConfigPolicy() {
        register(CONFIG_POLICY_ID, context -> QuasarConfig.destructiveEffectsEnabled());
    }

    public static void register(ResourceLocation id, DestructiveEffectPolicy policy) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(policy, "policy");
        if (frozen) {
            throw new IllegalStateException("Destructive effect policy registration is already closed");
        }
        if (POLICIES.putIfAbsent(id, policy) != null) {
            throw new IllegalStateException("Destructive effect policy is already registered: " + id);
        }
    }

    public static boolean allows(DestructiveEffectPolicyContext context) {
        Objects.requireNonNull(context, "context");
        for (Map.Entry<ResourceLocation, DestructiveEffectPolicy> entry : POLICIES.entrySet()) {
            try {
                if (!entry.getValue().allows(context)) {
                    return false;
                }
            } catch (RuntimeException exception) {
                LOGGER.error(
                    "Destructive effect policy {} failed for {}; denying the effect",
                    entry.getKey(),
                    context.typeId(),
                    exception
                );
                return false;
            }
        }
        return true;
    }

    public static void freeze() {
        frozen = true;
    }

    public static void clearForTesting() {
        POLICIES.clear();
        frozen = false;
    }

    public static int size() {
        return POLICIES.size();
    }
}
