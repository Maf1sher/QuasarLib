package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ScreenEffectSettingsTest {
    @Test
    void diagnosticPreviewHasNoSettings() {
        assertEquals(ScreenEffectOperation.DIAGNOSTIC_MASK_PREVIEW,
            ScreenEffectSettings.DIAGNOSTIC_PREVIEW.operation());
        assertNull(ScreenEffectSettings.DIAGNOSTIC_PREVIEW.settings());
        assertThrows(IllegalStateException.class,
            () -> ScreenEffectSettings.DIAGNOSTIC_PREVIEW.settings(WaveDistortionSettings.class));
    }

    @Test
    void rejectsMissingOperation() {
        assertThrows(NullPointerException.class, () -> new ScreenEffectSettings(null));
    }

    @Test
    void waveDistortionStoresTypedSettings() {
        ScreenEffectSettings settings = ScreenEffectSettings.waveDistortion(
            WaveDistortionSettings.DEBUG_DEFAULT
        );

        assertEquals(ScreenEffectOperation.MASK_SCOPED_WAVE_DISTORTION, settings.operation());
        assertSame(WaveDistortionSettings.DEBUG_DEFAULT,
            settings.settings(WaveDistortionSettings.class));
        assertThrows(IllegalArgumentException.class,
            () -> settings.settings(SceneBlurSettings.class));
    }

    @Test
    void builtInFactoriesRejectNullSettings() {
        assertThrows(NullPointerException.class, () -> ScreenEffectSettings.waveDistortion(null));
        assertThrows(NullPointerException.class, () -> ScreenEffectSettings.sceneBlur(null));
        assertThrows(NullPointerException.class, () -> ScreenEffectSettings.bloom(null));
        assertThrows(NullPointerException.class, () -> ScreenEffectSettings.blackHole(null));
    }

    @Test
    void customOperationCarriesPayload() {
        ScreenEffectOperation operation = ScreenEffectOperation.create(
            ResourceLocation.fromNamespaceAndPath("test", "custom_operation")
        );
        Object payload = new Object();

        ScreenEffectSettings settings = ScreenEffectSettings.custom(operation, payload);

        assertSame(operation, settings.operation());
        assertSame(payload, settings.settings(Object.class));
    }

    @Test
    void oneArgumentConstructorHasNoSettings() {
        ScreenEffectOperation operation = ScreenEffectOperation.create(
            ResourceLocation.fromNamespaceAndPath("test", "no_settings")
        );

        assertNull(new ScreenEffectSettings(operation).settings());
    }
}
