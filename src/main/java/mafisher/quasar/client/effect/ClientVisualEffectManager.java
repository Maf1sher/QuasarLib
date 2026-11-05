package mafisher.quasar.client.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import mafisher.quasar.effect.EffectTimeline;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import mafisher.quasar.effect.network.StopVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateVisualEffectPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Instance-owned state and rendering for transient client visual effects. */
public final class ClientVisualEffectManager {
    private static final int MAX_ACTIVE_EFFECTS = 128;
    private final Map<UUID, VisualEffectInstance> activeEffects = new LinkedHashMap<>();
    private final VisualEffectRendererRegistry rendererRegistry;
    private ClientLevel currentLevel;

    ClientVisualEffectManager(VisualEffectRendererRegistry rendererRegistry) {
        this.rendererRegistry = rendererRegistry;
    }

    void start(StartVisualEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        if (this.rendererRegistry.find(payload.effectId()).isEmpty()) {
            return;
        }
        this.activeEffects.put(payload.instanceId(), new VisualEffectInstance(payload, level.getGameTime()));
        while (this.activeEffects.size() > MAX_ACTIVE_EFFECTS) {
            UUID oldest = this.activeEffects.keySet().iterator().next();
            this.activeEffects.remove(oldest);
        }
    }

    void stop(StopVisualEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        this.activeEffects.remove(payload.instanceId());
    }

    void update(UpdateVisualEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        VisualEffectInstance effect = this.activeEffects.get(payload.instanceId());
        if (effect != null) {
            this.activeEffects.put(payload.instanceId(),
                effect.withTransform(payload.transform(), payload.updateGameTime()));
        }
    }

    void render(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null) {
            return;
        }
        long gameTime = level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        this.removeFinishedEffects(level, gameTime);

        if (this.activeEffects.isEmpty()) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Set<RenderType> usedRenderTypes = new LinkedHashSet<>();
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        try {
            for (VisualEffectInstance effect : this.activeEffects.values()) {
                this.rendererRegistry.find(effect.effectId()).ifPresent(renderer -> {
                    EffectRenderContext context = this.createRenderContext(effect, gameTime, partialTick);
                    EffectTransform transform = context.transform();
                    AABB renderBounds = this.validatedCullingBounds(renderer, context);
                    if (!event.getFrustum().isVisible(renderBounds)) {
                        return;
                    }
                    List<EffectRenderPass> renderPasses = List.copyOf(renderer.renderPasses(context));
                    Set<String> passIds = new HashSet<>();
                    for (EffectRenderPass renderPass : renderPasses) {
                        if (!passIds.add(renderPass.id())) {
                            throw new IllegalStateException("Duplicate render pass ID '" + renderPass.id()
                                + "' for effect " + effect.effectId());
                        }
                    }
                    poseStack.pushPose();
                    try {
                        poseStack.translate(transform.position().x(), transform.position().y(),
                            transform.position().z());
                        EffectRenderTransform.applyRotation(poseStack, transform.yaw(), transform.pitch(),
                            transform.roll());
                        for (EffectRenderPass renderPass : renderPasses) {
                            usedRenderTypes.add(renderPass.renderType());
                            renderer.render(context, renderPass, poseStack, buffers);
                        }
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

    List<ScreenEffectFrame> screenEffects(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null) {
            return List.of();
        }

        long gameTime = level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        this.removeFinishedEffects(level, gameTime);
        if (this.activeEffects.isEmpty()) {
            return List.of();
        }

        List<ScreenEffectFrame> screenEffects = new ArrayList<>();
        for (VisualEffectInstance effect : this.activeEffects.values()) {
            VisualEffectRenderer renderer = this.rendererRegistry.find(effect.effectId()).orElse(null);
            if (!(renderer instanceof ScreenEffectRenderer screenRenderer)) {
                continue;
            }

            EffectRenderContext context = this.createRenderContext(effect, gameTime, partialTick);
            if (!event.getFrustum().isVisible(this.validatedCullingBounds(renderer, context))) {
                continue;
            }
            ScreenEffectSettings settings = Objects.requireNonNull(screenRenderer.screenEffectSettings(context),
                "screenEffectSettings");
            ScreenEffectMask mask = Objects.requireNonNull(screenRenderer.screenEffectMask(context),
                "screenEffectMask");
            if (mask.strength() > 0.0F) {
                screenEffects.add(new ScreenEffectFrame(context, screenRenderer, settings, mask));
            }
        }
        return List.copyOf(screenEffects);
    }

    void renderScreenMask(net.neoforged.neoforge.client.event.RenderLevelStageEvent event, ScreenEffectFrame effect) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType maskRenderType = Objects.requireNonNull(
            effect.renderer().screenMaskRenderType(effect.context()), "screenMaskRenderType");
        EffectTransform transform = effect.context().transform();

        RenderSystem.backupProjectionMatrix();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            // AFTER_LEVEL does not provide the camera-oriented world pose stack used by AFTER_PARTICLES.
            RenderSystem.setProjectionMatrix(new Matrix4f(event.getProjectionMatrix()),
                VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.set(event.getModelViewMatrix());
            RenderSystem.applyModelViewMatrix();

            poseStack.pushPose();
            try {
                poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
                poseStack.pushPose();
                try {
                    poseStack.translate(transform.position().x(), transform.position().y(),
                        transform.position().z());
                    EffectRenderTransform.applyRotation(poseStack, transform.yaw(), transform.pitch(),
                        transform.roll());
                    effect.renderer().renderScreenMask(effect.context(), maskRenderType, poseStack, buffers);
                } finally {
                    poseStack.popPose();
                }
            } finally {
                poseStack.popPose();
                buffers.endBatch(maskRenderType);
            }
        } finally {
            try {
                modelViewStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            } finally {
                RenderSystem.restoreProjectionMatrix();
            }
        }
    }

    private AABB validatedCullingBounds(VisualEffectRenderer renderer, EffectRenderContext context) {
        AABB bounds = Objects.requireNonNull(renderer.cullingBounds(context), "cullingBounds");
        if (!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.minZ)
            || !Double.isFinite(bounds.maxX) || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)
            || bounds.minX > bounds.maxX || bounds.minY > bounds.maxY || bounds.minZ > bounds.maxZ) {
            throw new IllegalStateException("Renderer returned invalid world-space culling bounds for effect "
                + context.effect().effectId());
        }
        return bounds;
    }

    private void removeFinishedEffects(ClientLevel level, long gameTime) {
        ResourceLocation currentDimension = level.dimension().location();
        this.activeEffects.entrySet().removeIf(entry -> {
            StartVisualEffectPayload payload = entry.getValue().payload();
            return !payload.dimensionId().equals(currentDimension)
                || EffectTimeline.isFinished(gameTime, payload.startGameTime(), payload.durationTicks());
        });
    }

    private EffectRenderContext createRenderContext(VisualEffectInstance effect, long gameTime, float partialTick) {
        StartVisualEffectPayload payload = effect.payload();
        EffectTransform transform = effect.interpolatedTransform(gameTime, partialTick);
        float age = (float)EffectTimeline.ageTicks(gameTime, partialTick, payload.startGameTime());
        float progress = EffectTimeline.progress(gameTime, partialTick, payload.startGameTime(),
            payload.durationTicks());
        return new EffectRenderContext(effect, transform, age, progress, partialTick);
    }

    void onLevelUnload(ClientLevel level) {
        if (this.currentLevel == level) {
            this.activeEffects.clear();
            this.currentLevel = null;
        }
    }

    void onResourceReload(ResourceManager resourceManager) {
        this.rendererRegistry.onResourceReload(resourceManager);
    }

    private void synchronizeLevel(ClientLevel level) {
        if (this.currentLevel != level) {
            this.activeEffects.clear();
            this.currentLevel = level;
        }
    }
}
