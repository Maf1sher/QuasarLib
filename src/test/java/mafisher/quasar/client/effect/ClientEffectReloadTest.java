package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

class ClientEffectReloadTest {
    @Test
    void resourceReloadIsDispatchedInsteadOfAppliedInline() {
        List<Runnable> queued = new ArrayList<>();

        ClientEffectFrameRuntime.onResourceReload(mock(ResourceManager.class), queued::add);

        assertEquals(1, queued.size(), "the reload must be handed to the client thread");
    }
}
