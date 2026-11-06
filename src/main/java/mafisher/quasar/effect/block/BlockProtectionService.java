package mafisher.quasar.effect.block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registry and query surface for per-change block protection callbacks. */
public final class BlockProtectionService {
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");
    private static final Map<ResourceLocation, BlockProtection> PROTECTIONS = new LinkedHashMap<>();
    private static boolean frozen;

    private BlockProtectionService() {
    }

    static void register(ResourceLocation id, BlockProtection protection) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(protection, "protection");
        if (frozen) {
            throw new IllegalStateException("Block protection registration is already closed");
        }
        if (PROTECTIONS.putIfAbsent(id, protection) != null) {
            throw new IllegalStateException("Block protection is already registered: " + id);
        }
    }

    static void freeze() {
        frozen = true;
    }

    static void clearForTesting() {
        PROTECTIONS.clear();
        frozen = false;
    }

    static int size() {
        return PROTECTIONS.size();
    }

    /** Returns {@code true} when every protection allows the change. */
    public static boolean allows(BlockProtectionContext context) {
        Objects.requireNonNull(context, "context");
        for (Map.Entry<ResourceLocation, BlockProtection> entry : PROTECTIONS.entrySet()) {
            try {
                if (!entry.getValue().allows(context)) {
                    return false;
                }
            } catch (RuntimeException exception) {
                LOGGER.error(
                    "Block protection {} failed for {} at {}; vetoing the change",
                    entry.getKey(),
                    context.effectTypeId(),
                    context.position(),
                    exception
                );
                return false;
            }
        }
        return true;
    }
}
