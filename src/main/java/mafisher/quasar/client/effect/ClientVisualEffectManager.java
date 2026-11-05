package mafisher.quasar.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import mafisher.quasar.effect.EffectTimeline;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import mafisher.quasar.effect.network.StopVisualEffectPayload;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Client-side owner for transient visual effect instances. */
public final class ClientVisualEffectManager {
    private static final int MAX_ACTIVE_EFFECTS = 128;
    private static final Map<UUID, VisualEffectInstance> ACTIVE_EFFECTS = new LinkedHashMap<>();

    private ClientVisualEffectManager() {
    }

    public static void start(StartVisualEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        VisualEffectRendererRegistry.registerBuiltIns();
        if (VisualEffectRendererRegistry.find(payload.effectId()).isEmpty()) {
            return;
        }
        ACTIVE_EFFECTS.put(payload.instanceId(), new VisualEffectInstance(payload));
        while (ACTIVE_EFFECTS.size() > MAX_ACTIVE_EFFECTS) {
            UUID oldest = ACTIVE_EFFECTS.keySet().iterator().next();
            ACTIVE_EFFECTS.remove(oldest);
        }
    }

    public static void stop(StopVisualEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        ACTIVE_EFFECTS.remove(payload.instanceId());
    }

    public static void render(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ACTIVE_EFFECTS.clear();
            return;
        }
        ResourceLocation currentDimension = minecraft.level.dimension().location();
        long gameTime = minecraft.level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        ACTIVE_EFFECTS.entrySet().removeIf(entry -> {
            StartVisualEffectPayload payload = entry.getValue().payload();
            return !payload.dimensionId().equals(currentDimension)
                || EffectTimeline.isFinished(gameTime, payload.startGameTime(), payload.durationTicks());
        });

        if (ACTIVE_EFFECTS.isEmpty()) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Set<RenderType> usedRenderTypes = new HashSet<>();
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        try {
            for (VisualEffectInstance effect : ACTIVE_EFFECTS.values()) {
                VisualEffectRendererRegistry.find(effect.effectId()).ifPresent(renderer -> {
                    StartVisualEffectPayload payload = effect.payload();
                    double cullingRadius = payload.scale();
                    AABB renderBounds = new AABB(
                        payload.x() - cullingRadius,
                        payload.y() - cullingRadius,
                        payload.z() - cullingRadius,
                        payload.x() + cullingRadius,
                        payload.y() + cullingRadius,
                        payload.z() + cullingRadius
                    );
                    if (!event.getFrustum().isVisible(renderBounds)) {
                        return;
                    }
                    double age = EffectTimeline.ageTicks(gameTime, partialTick, payload.startGameTime());
                    float progress = EffectTimeline.progress(
                        gameTime, partialTick, payload.startGameTime(), payload.durationTicks()
                    );
                    poseStack.pushPose();
                    try {
                        poseStack.translate(payload.x(), payload.y(), payload.z());
                        usedRenderTypes.add(renderer.renderType());
                        renderer.render(effect, (float)age, progress, poseStack, buffers);
                    } finally {
                        poseStack.popPose();
                    }
                });
            }
        } finally {
            poseStack.popPose();
            for (RenderType renderType : usedRenderTypes) {
                buffers.endBatch(renderType);
            }
        }
    }
}
