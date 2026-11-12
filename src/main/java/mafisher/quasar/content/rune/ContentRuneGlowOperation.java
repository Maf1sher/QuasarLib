package mafisher.quasar.content.rune;

import mafisher.quasar.api.client.ScreenEffectInputs;
import mafisher.quasar.api.client.ScreenEffectOperationContext;
import mafisher.quasar.api.client.ScreenEffectOperationRenderer;
import mafisher.quasar.content.ContentShaders;
import net.minecraft.client.renderer.ShaderInstance;

/** Content-owned masked screen glow, built only on the public operation context. */
public final class ContentRuneGlowOperation implements ScreenEffectOperationRenderer {
    @Override
    public ScreenEffectInputs requiredInputs() {
        return ScreenEffectInputs.NONE;
    }

    @Override
    public void render(ScreenEffectOperationContext context) {
        ContentRuneGlowSettings settings = context.settings(ContentRuneGlowSettings.class);
        ShaderInstance shader = ContentShaders.RUNE_GLOW.instance()
            .orElseThrow(() -> new IllegalStateException("Content rune glow shader is not loaded"));
        context.bindCompositeInputs(shader);
        context.setUniform(shader, "GlowColor", settings.red(), settings.green(), settings.blue());
        context.setUniform(shader, "GlowStrength", settings.strength());
        context.setUniform(shader, "GlowPulse", settings.pulseSpeed());
        context.setUniform(shader, "GlowTime", context.effect().ageTicks());
        context.drawFullscreen(context.destination(), shader);
    }
}
