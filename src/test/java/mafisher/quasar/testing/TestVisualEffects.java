package mafisher.quasar.testing;

import java.util.UUID;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.VisualEffectInstance;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Stable visual-effect payloads and render contexts for headless renderer tests. */
public final class TestVisualEffects {
    public static final ResourceLocation EFFECT_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_content", "test");
    public static final ResourceLocation DIMENSION =
        ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    private TestVisualEffects() {
    }

    public static StartVisualEffectPayload payload(
        ResourceLocation effectId,
        UUID instanceId,
        Vec3 position,
        float scale,
        int durationTicks,
        long seed
    ) {
        return payload(effectId, instanceId, position, 0.0F, 0.0F, 0.0F, scale, durationTicks, seed);
    }

    public static StartVisualEffectPayload payload(
        ResourceLocation effectId,
        UUID instanceId,
        Vec3 position,
        float yaw,
        float pitch,
        float roll,
        float scale,
        int durationTicks,
        long seed
    ) {
        return new StartVisualEffectPayload(
            effectId,
            instanceId,
            DIMENSION,
            position.x,
            position.y,
            position.z,
            yaw,
            pitch,
            roll,
            scale,
            durationTicks,
            0L,
            seed
        );
    }

    public static EffectRenderContext context(
        StartVisualEffectPayload payload,
        float ageTicks,
        float progress
    ) {
        return new EffectRenderContext(
            new VisualEffectInstance(payload),
            payload.transform(),
            ageTicks,
            progress,
            0.5F
        );
    }

    public static EffectRenderContext context(
        ResourceLocation effectId,
        UUID instanceId,
        Vec3 position,
        float scale,
        int durationTicks,
        float progress
    ) {
        StartVisualEffectPayload payload = payload(
            effectId, instanceId, position, scale, durationTicks, 12_345L
        );
        return context(payload, progress * durationTicks, progress);
    }
}
