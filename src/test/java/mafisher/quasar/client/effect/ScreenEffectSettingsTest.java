package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ScreenEffectSettingsTest {
    @Test
    void diagnosticPreviewSelectsTheExistingMaskOperation() {
        assertEquals(ScreenEffectOperation.DIAGNOSTIC_MASK_PREVIEW,
            ScreenEffectSettings.DIAGNOSTIC_PREVIEW.operation());
    }

    @Test
    void rejectsMissingOperation() {
        assertThrows(NullPointerException.class, () -> new ScreenEffectSettings(null));
    }

    @Test
    void waveDistortionRequiresItsSettings() {
        assertThrows(NullPointerException.class,
            () -> new ScreenEffectSettings(ScreenEffectOperation.MASK_SCOPED_WAVE_DISTORTION));
        assertEquals(ScreenEffectOperation.MASK_SCOPED_WAVE_DISTORTION,
            ScreenEffectSettings.waveDistortion(WaveDistortionSettings.DEBUG_DEFAULT).operation());
    }

    @Test
    void rejectsWaveSettingsForTheDiagnosticOperation() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectSettings(
            ScreenEffectOperation.DIAGNOSTIC_MASK_PREVIEW, WaveDistortionSettings.DEBUG_DEFAULT));
    }

    @Test
    void sceneBlurRequiresItsSettings() {
        assertThrows(NullPointerException.class,
            () -> new ScreenEffectSettings(ScreenEffectOperation.MASK_SCOPED_SCENE_BLUR));
        assertDoesNotThrow(() -> ScreenEffectSettings.sceneBlur(SceneBlurSettings.DEBUG_DEFAULT));
    }

    @Test
    void bloomRequiresItsSettings() {
        assertThrows(NullPointerException.class,
            () -> new ScreenEffectSettings(ScreenEffectOperation.MASK_SCOPED_BLOOM));
        assertEquals(ScreenEffectOperation.MASK_SCOPED_BLOOM,
            ScreenEffectSettings.bloom(BloomSettings.DEBUG_DEFAULT).operation());
    }

    @Test
    void blackHoleOperationRequiresItsSettings() {
        assertThrows(NullPointerException.class,
            () -> new ScreenEffectSettings(ScreenEffectOperation.MASK_SCOPED_BLACK_HOLE));
        assertEquals(ScreenEffectOperation.MASK_SCOPED_BLACK_HOLE,
            ScreenEffectSettings.blackHole(BlackHoleScreenSettings.CINEMATIC_DEFAULT).operation());
    }

    @Test
    void rejectsSettingsBelongingToAnotherOperation() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectSettings(
            ScreenEffectOperation.MASK_SCOPED_SCENE_BLUR,
            WaveDistortionSettings.DEBUG_DEFAULT,
            SceneBlurSettings.DEBUG_DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectSettings(
            ScreenEffectOperation.MASK_SCOPED_WAVE_DISTORTION,
            WaveDistortionSettings.DEBUG_DEFAULT,
            SceneBlurSettings.DEBUG_DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectSettings(
            ScreenEffectOperation.MASK_SCOPED_BLOOM,
            WaveDistortionSettings.DEBUG_DEFAULT,
            null,
            BloomSettings.DEBUG_DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectSettings(
            ScreenEffectOperation.MASK_SCOPED_BLOOM,
            null,
            SceneBlurSettings.DEBUG_DEFAULT,
            BloomSettings.DEBUG_DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> new ScreenEffectSettings(
            ScreenEffectOperation.MASK_SCOPED_BLACK_HOLE,
            null,
            null,
            BloomSettings.DEBUG_DEFAULT,
            BlackHoleScreenSettings.CINEMATIC_DEFAULT));
    }
}
