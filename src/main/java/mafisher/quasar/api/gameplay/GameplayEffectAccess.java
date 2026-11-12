package mafisher.quasar.api.gameplay;

import java.util.Objects;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.BlockJournal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Internal bridge for the per-level gameplay runtime. It keeps {@link GameplayEffectContext}
 * construction and service-only operations package-private while the state owner lives in the
 * implementation package.
 */
@ApiStatus.Internal
public final class GameplayEffectAccess {
    private GameplayEffectAccess() {
    }

    public static GameplayEffectContext create(
        ServerLevel level,
        EffectScope scope,
        GameplayEffectSpec spec,
        UUID sessionId,
        EffectCapabilities capabilities,
        BlockJournal blockJournal
    ) {
        return new GameplayEffectContext(level, scope, spec, sessionId, capabilities, blockJournal);
    }

    public static GameplayEffectContext restore(
        ServerLevel level,
        EffectScope scope,
        GameplayEffectSpec spec,
        UUID sessionId,
        EffectCapabilities capabilities,
        BlockJournal blockJournal,
        int initialAgeTicks,
        CompoundTag initialState
    ) {
        return new GameplayEffectContext(
            level, scope, spec, sessionId, capabilities, blockJournal, initialAgeTicks, initialState
        );
    }

    public static int applyBlockChanges(GameplayEffectContext context, int maxChanges) {
        return context.applyBlockChanges(maxChanges);
    }

    public static BlockJournal blockJournal(GameplayEffectContext context) {
        return context.blockJournal();
    }

    public static boolean consumeStateDirty(GameplayEffectContext context) {
        return context.consumeStateDirty();
    }

    public static CompoundTag rawPersistentState(GameplayEffectContext context) {
        return context.rawPersistentState();
    }

    public static boolean isStopRequested(GameplayEffectContext context) {
        return context.isStopRequested();
    }

    public static void markAudioStarted(GameplayEffectContext context) {
        context.markAudioStarted();
    }

    public static void flushAudioTriggers(GameplayEffectContext context) {
        context.flushAudioTriggers();
    }

    public static void endAt(GameplayEffectContext context, int ageTicks) {
        context.endAt(ageTicks);
    }

    public static boolean destructivePolicyAllows(
        ServerLevel level,
        GameplayEffectSpec spec,
        EffectCapabilities capabilities
    ) {
        if (!capabilities.destructive()) {
            return true;
        }
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(spec, "spec");
        return DestructiveEffectPolicies.allows(
            new DestructiveEffectPolicyContext(
                level,
                spec.typeId(),
                spec.ownerId(),
                spec.anchor(),
                capabilities
            )
        );
    }
}
