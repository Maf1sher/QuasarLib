package mafisher.quasar.client.effect.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import mafisher.quasar.api.client.screen.ScreenEffectInputs;
import mafisher.quasar.api.client.screen.ScreenEffectOperation;
import mafisher.quasar.api.client.screen.ScreenEffectOperationRenderer;
import mafisher.quasar.client.effect.render.ScreenEffectBatch;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/** Client-only registry of screen-space compositor operations. */
@ApiStatus.Internal
public final class ScreenEffectOperationRegistry {
    private final Map<ResourceLocation, ScreenEffectOperationRenderer> operations = new HashMap<>();
    private Map<ResourceLocation, ScreenEffectOperationRenderer> frozenOperations;
    private boolean builtInsRegistered;

    public ScreenEffectOperationRegistry() {
    }

    public void registerBuiltIns() {
        if (this.builtInsRegistered) {
            return;
        }
        BuiltInScreenEffectOperations.register(this);
        this.builtInsRegistered = true;
    }

    public void register(ScreenEffectOperation operation, ScreenEffectOperationRenderer renderer) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(renderer, "renderer");
        if (this.frozenOperations != null) {
            throw new IllegalStateException("Screen effect operation registration is closed after client setup: "
                + operation.id());
        }
        if (this.operations.putIfAbsent(operation.id(), renderer) != null) {
            throw new IllegalStateException("A screen effect operation is already registered for "
                + operation.id());
        }
    }

    public Optional<ScreenEffectOperationRenderer> find(ScreenEffectOperation operation) {
        Objects.requireNonNull(operation, "operation");
        Map<ResourceLocation, ScreenEffectOperationRenderer> current = this.frozenOperations;
        if (current != null) {
            return Optional.ofNullable(current.get(operation.id()));
        }
        return Optional.ofNullable(this.operations.get(operation.id()));
    }

    /** Returns the union of optional captures required by every batch in the frame. */
    ScreenEffectInputs requiredInputs(List<ScreenEffectBatch> batches) {
        Objects.requireNonNull(batches, "batches");
        ScreenEffectInputs inputs = ScreenEffectInputs.NONE;
        for (ScreenEffectBatch batch : batches) {
            ScreenEffectOperationRenderer renderer = find(batch.settings().operation()).orElse(null);
            inputs = inputs.union(renderer == null
                ? ScreenEffectInputs.ALL
                : renderer.requiredInputs());
        }
        return inputs;
    }

    /** Makes the current registrations immutable and rejects any later registration. */
    public void freeze() {
        if (this.frozenOperations == null) {
            this.frozenOperations = Map.copyOf(this.operations);
        }
    }

    /** Number of registered operations, used by startup diagnostics. */
    public int size() {
        Map<ResourceLocation, ScreenEffectOperationRenderer> current = this.frozenOperations;
        return current != null ? current.size() : this.operations.size();
    }
}
