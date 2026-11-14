package mafisher.quasar.api.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mafisher.quasar.api.client.render.EffectRenderTypeOptions.Transparency;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.junit.jupiter.api.Test;

class ScreenEffectMasksTest {
    @Test
    void fullscreenIsCoverageOnly() {
        assertEquals(Transparency.TRANSLUCENT, ScreenEffectMasks.FULLSCREEN_OPTIONS.transparency());
        assertFalse(ScreenEffectMasks.FULLSCREEN_OPTIONS.depthTest());
        assertTrue(ScreenEffectMasks.FULLSCREEN_OPTIONS.writeColor());
        assertFalse(ScreenEffectMasks.FULLSCREEN_OPTIONS.writeDepth());
        assertFalse(ScreenEffectMasks.FULLSCREEN_OPTIONS.cull());
        assertFalse(ScreenEffectMasks.FULLSCREEN_OPTIONS.lightmap());
    }

    @Test
    void fullscreenIsCached() {
        assertSame(ScreenEffectMasks.fullscreen(), ScreenEffectMasks.fullscreen());
    }

    @Test
    void drawFullscreenEmitsOneQuad() {
        RenderType renderType = ScreenEffectMasks.fullscreen();
        MultiBufferSource bufferSource = mock(MultiBufferSource.class);
        VertexConsumer consumer = mock(VertexConsumer.class, RETURNS_SELF);
        when(bufferSource.getBuffer(renderType)).thenReturn(consumer);

        ScreenEffectMasks.drawFullscreen(renderType, new PoseStack(), bufferSource);

        verify(bufferSource).getBuffer(renderType);
        verify(consumer, times(4)).addVertex(any(PoseStack.Pose.class),
            anyFloat(), anyFloat(), anyFloat());
    }
}
