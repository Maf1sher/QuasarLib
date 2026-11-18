package mafisher.quasar.content.orbital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import mafisher.quasar.api.client.screen.ScreenEffectInputs;
import org.junit.jupiter.api.Test;

class ContentOrbitalLaserBeamOperationTest {
    @Test
    void requestsTheBeamCompositionFloorAndBothOptionalCaptures() {
        ContentOrbitalLaserBeamOperation operation = new ContentOrbitalLaserBeamOperation();

        assertEquals(ContentOrbitalLaserSettings.BEAM_COMPOSITION_FLOOR,
            operation.compositionScaleFloor(), 0.0F);
        assertEquals(1.0F, ContentOrbitalLaserBeamOperation.compositeSolidity(), 0.0F);
        assertSame(ScreenEffectInputs.ALL, operation.requiredInputs());
    }
}
