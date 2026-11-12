package mafisher.quasar.content;

import mafisher.quasar.client.effect.ScreenEffectInputs;
import mafisher.quasar.client.effect.ScreenEffectOperationContext;
import mafisher.quasar.client.effect.ScreenEffectOperationRenderer;
import net.minecraft.client.renderer.ShaderInstance;

/** Content-owned masked screen glow, built only on the public operation context. */
final class ContentRuneGlowOperation implements ScreenEffectOperationRenderer {
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
