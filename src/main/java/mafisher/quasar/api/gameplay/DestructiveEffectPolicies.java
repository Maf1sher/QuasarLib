package mafisher.quasar.api.gameplay;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import mafisher.quasar.QuasarConfig;
import mafisher.quasar.QuasarLib;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Internal registry that ANDs every destructive-effect policy. */
final class DestructiveEffectPolicies {
    private static final Logger LOGGER = LoggerFactory.getLogger(DestructiveEffectPolicies.class);
    private static final ResourceLocation CONFIG_POLICY_ID =
        ResourceLocation.fromNamespaceAndPath(QuasarLib.MOD_ID, "config");
    private static final Map<ResourceLocation, DestructiveEffectPolicy> POLICIES = new LinkedHashMap<>();
    private static boolean frozen;

    private DestructiveEffectPolicies() {
    }

    static void registerConfigPolicy() {
        register(CONFIG_POLICY_ID, context -> QuasarConfig.destructiveEffectsEnabled());
    }

    static void register(ResourceLocation id, DestructiveEffectPolicy policy) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(policy, "policy");
        if (frozen) {
            throw new IllegalStateException("Destructive effect policy registration is already closed");
        }
        if (POLICIES.putIfAbsent(id, policy) != null) {
            throw new IllegalStateException("Destructive effect policy is already registered: " + id);
        }
    }

    static boolean allows(DestructiveEffectPolicyContext context) {
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

    static void freeze() {
        frozen = true;
    }

    static void clearForTesting() {
        POLICIES.clear();
        frozen = false;
    }

    static int size() {
        return POLICIES.size();
    }
}
