package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.junit.jupiter.api.Test;

class ClientEffectRenderEventsTest {
    @Test
    void forwardsEveryStageUsedByTheClientRuntime() {
        assertTrue(ClientEffectRenderEvents.handles(RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES));
        assertTrue(ClientEffectRenderEvents.handles(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS));
        assertTrue(ClientEffectRenderEvents.handles(RenderLevelStageEvent.Stage.AFTER_PARTICLES));
        assertTrue(ClientEffectRenderEvents.handles(RenderLevelStageEvent.Stage.AFTER_LEVEL));
    }

    @Test
    void ignoresUnrelatedStages() {
        assertFalse(ClientEffectRenderEvents.handles(RenderLevelStageEvent.Stage.AFTER_SKY));
        assertFalse(ClientEffectRenderEvents.handles(RenderLevelStageEvent.Stage.AFTER_WEATHER));
    }
}
