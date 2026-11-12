package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.gameplay.EffectTickBudget;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.BlockJournal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

class GameplayEffectContextTest {
    @Test
    void runsEveryCloseActionEvenWhenOneThrows() {
        GameplayEffectContext context = context();
        AtomicInteger completed = new AtomicInteger();
        context.onClose(() -> {
            throw new RuntimeException("cleanup failure");
        });
        context.onClose(completed::incrementAndGet);

        context.close();

        assertEquals(1, completed.get());
    }

    private static GameplayEffectContext context() {
        ServerLevel level = mock(ServerLevel.class);
        EffectScope scope = mock(EffectScope.class);
        GameplayEffectSpec spec = mock(GameplayEffectSpec.class);
        when(spec.typeId()).thenReturn(
            ResourceLocation.fromNamespaceAndPath("quasar_lib", "cleanup_test")
        );
        when(spec.budget()).thenReturn(EffectTickBudget.unlimited());
        return new GameplayEffectContext(
            level,
            scope,
            spec,
            UUID.fromString("00000000-0000-0000-0000-000000000099"),
            EffectCapabilities.NONE,
            new BlockJournal()
        );
    }
}
