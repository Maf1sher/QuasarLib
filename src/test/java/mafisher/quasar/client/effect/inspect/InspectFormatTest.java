package mafisher.quasar.client.effect.inspect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class InspectFormatTest {
    @Test
    void hudAndStatusStayBounded() {
        InspectFrame frame = new InspectFrame();
        frame.begin(1200L, 60L, 1L);
        for (int index = 0; index < 20; index++) {
            InspectFrame.EffectSlot effect = frame.addEffect();
            effect.instanceId = UUID.randomUUID();
            effect.effectId = ResourceLocation.fromNamespaceAndPath("quasar_content", "effect_" + index);
            effect.visible = true;
            effect.ageTicks = index;
            effect.progress = 0.5F;
            effect.spatialRadius = 8.0;
            effect.minX = -1.0;
            effect.minY = -1.0;
            effect.minZ = -1.0;
            effect.maxX = 1.0;
            effect.maxY = 1.0;
            effect.maxZ = 1.0;
            effect.quality = EffectQualityTier.HIGH;
            effect.effectiveQuality = EffectQualityTier.HIGH;
        }
        frame.counts(20, 2, 3, 4);

        List<String> hud = InspectFormat.hudLines(frame);
        List<String> status = InspectFormat.statusLines(frame);

        assertFalse(hud.isEmpty());
        assertFalse(status.isEmpty());
        assertTrue(status.size() < 40);
        assertTrue(hud.size() < 15);
        assertTrue(status.getFirst().contains("standalone=20"));
    }

    @Test
    void emptyFrameReportsWaiting() {
        InspectFrame frame = new InspectFrame();

        assertTrue(InspectFormat.hudLines(frame).getFirst().contains("waiting"));
        assertTrue(InspectFormat.statusLines(frame).getFirst().contains("waiting"));
    }
}
