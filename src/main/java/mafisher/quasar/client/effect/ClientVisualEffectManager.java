package mafisher.quasar.client.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectTimeline;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.network.ResumeEffectsPayload;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import mafisher.quasar.effect.network.StopVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateSegmentedVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateVisualEffectPayload;
import mafisher.quasar.effect.scale.SegmentLink;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Instance-owned state and rendering for transient client visual effects. */
public final class ClientVisualEffectManager {
    private static final int MAX_ACTIVE_EFFECTS = 128;
    private static final double INDEX_QUERY_MARGIN = 16.0;
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");

    private final Map<UUID, VisualEffectInstance> activeEffects = new LinkedHashMap<>();
    private final Map<UUID, ClientSegmentGroup> segmentedGroups = new LinkedHashMap<>();
    private final Map<UUID, Double> instanceSpatialRadii = new LinkedHashMap<>();
    private final ClientEffectSpatialIndex spatialIndex = new ClientEffectSpatialIndex();
    private final Set<ClientEffectSpatialIndex.Key> unindexedInstances = new LinkedHashSet<>();
    private final Set<ClientEffectSpatialIndex.Key> unindexedGroups = new LinkedHashSet<>();
    private final VisualEffectRendererRegistry rendererRegistry;
    private final InstancedBatchRenderer instancedRenderer = new InstancedBatchRenderer();
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
        VisualEffectRenderer renderer = this.rendererRegistry.find(payload.effectId()).orElse(null);
        if (renderer == null) {
            return;
        }
        this.removeInstance(payload.instanceId());
        VisualEffectInstance effect = new VisualEffectInstance(payload, level.getGameTime());
        double radius = this.spatialRadius(renderer, effect);
        this.activeEffects.put(payload.instanceId(), effect);
        this.instanceSpatialRadii.put(payload.instanceId(), radius);

        SegmentLink segmentLink = payload.segmentLink();
        if (segmentLink != null) {
            ClientSegmentGroup group = this.segmentedGroup(segmentLink, payload, level.getGameTime());
            group.add(new ClientSegmentGroup.SegmentMember(
                payload.instanceId(), payload, segmentLink, radius
            ));
            this.indexGroup(segmentLink.coordinatorId());
        } else {
            this.indexInstance(payload.instanceId());
        }
        while (this.activeEffects.size() > MAX_ACTIVE_EFFECTS) {
            UUID oldest = this.activeEffects.keySet().iterator().next();
            this.removeInstance(oldest);
        }
    }

    void stop(StopVisualEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        this.removeInstance(payload.instanceId());
    }

    /** Replaces the local visual snapshot for one dimension with the server's resume list. */
    void resume(ResumeEffectsPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        this.clearState();
        for (StartVisualEffectPayload effect : payload.effects()) {
            this.start(effect);
        }
    }

    void update(UpdateVisualEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        VisualEffectInstance effect = this.activeEffects.get(payload.instanceId());
        if (effect != null && effect.payload().segmentLink() == null) {
            this.activeEffects.put(payload.instanceId(),
                effect.withTransform(payload.transform(), payload.updateGameTime()));
            this.indexInstance(payload.instanceId());
        }
    }

    /** Stores one aggregated coordinator update; segment transforms are derived only for visible cells. */
    void updateSegmented(UpdateSegmentedVisualEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        ClientSegmentGroup group = this.segmentedGroups.get(payload.coordinatorId());
        if (group == null) {
            return;
        }
        group.applyBase(payload.transform(), payload.updateGameTime());
        this.indexGroup(payload.coordinatorId());
    }

    void render(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null) {
            return;
        }
        long gameTime = level.getGameTime();
        this.removeFinishedEffects(level, gameTime);
        if (this.activeEffects.isEmpty()) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Set<RenderType> usedRenderTypes = new LinkedHashSet<>();
        InstancedBatchBuilder instancedBatches = new InstancedBatchBuilder(
            this.instancedRenderer.maxInstancesPerDraw()
        );
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        Matrix4f baseView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
        try {
            this.forEachVisibleEffect(event, (effect, context, renderer) -> {
                if (renderer instanceof InstancedVisualEffectRenderer instanced) {
                    List<EffectInstancedPass> instancedPasses = instanced.instancedPasses(context);
                    if (!instancedPasses.isEmpty()) {
                        instancedBatches.add(context, instancedPasses);
                        return;
                    }
                }
                EffectTransform transform = context.transform();
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
        } finally {
            poseStack.popPose();
            for (RenderType renderType : usedRenderTypes) {
                buffers.endBatch(renderType);
            }
        }
        this.instancedRenderer.draw(instancedBatches.draws(), baseView, RenderSystem.getProjectionMatrix());
    }

    List<ScreenEffectFrame> screenEffects(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null) {
            return List.of();
        }

        long gameTime = level.getGameTime();
        this.removeFinishedEffects(level, gameTime);
        if (this.activeEffects.isEmpty()) {
            return List.of();
        }

        List<ScreenEffectFrame> screenEffects = new ArrayList<>();
        this.forEachVisibleEffect(event, (effect, context, renderer) -> {
            if (!(renderer instanceof ScreenEffectRenderer screenRenderer)) {
                return;
            }
            ScreenEffectSettings settings = Objects.requireNonNull(screenRenderer.screenEffectSettings(context),
                "screenEffectSettings");
            ScreenEffectMask mask = Objects.requireNonNull(screenRenderer.screenEffectMask(context),
                "screenEffectMask");
            if (mask.strength() > 0.0F) {
                screenEffects.add(new ScreenEffectFrame(context, screenRenderer, settings, mask));
            }
        });
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

    private void forEachVisibleEffect(
        net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        VisibleEffectConsumer consumer
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        long gameTime = level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        AABB query = this.queryBox(event.getCamera().getPosition());

        this.spatialIndex.query(query, entry -> {
            if (entry.key().kind() == ClientEffectSpatialIndex.Kind.INSTANCE) {
                this.visitInstance(entry.key().id(), event, gameTime, partialTick, consumer);
                return;
            }
            ClientSegmentGroup group = this.segmentedGroups.get(entry.key().id());
            if (group != null) {
                group.forEachCellIn(query,
                    member -> this.visitMember(group, member, event, gameTime, partialTick, consumer));
            }
        });
        for (ClientEffectSpatialIndex.Key key : this.unindexedInstances) {
            this.visitInstance(key.id(), event, gameTime, partialTick, consumer);
        }
        for (ClientEffectSpatialIndex.Key key : this.unindexedGroups) {
            ClientSegmentGroup group = this.segmentedGroups.get(key.id());
            if (group != null) {
                group.forEachCellIn(AABB.INFINITE,
                    member -> this.visitMember(group, member, event, gameTime, partialTick, consumer));
            }
        }
    }

    private void visitInstance(
        UUID instanceId,
        net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        long gameTime,
        float partialTick,
        VisibleEffectConsumer consumer
    ) {
        VisualEffectInstance effect = this.activeEffects.get(instanceId);
        if (effect == null) {
            return;
        }
        VisualEffectRenderer renderer = this.rendererRegistry.find(effect.effectId()).orElse(null);
        if (renderer == null) {
            return;
        }
        EffectRenderContext context = this.createRenderContext(effect, gameTime, partialTick);
        double radius = this.instanceSpatialRadii.getOrDefault(instanceId, Double.POSITIVE_INFINITY);
        if (!this.isVisible(renderer, context, event, radius)) {
            return;
        }
        consumer.accept(effect, context.withLod(selectLod(renderer, event, context, radius)), renderer);
    }

    private void visitMember(
        ClientSegmentGroup group,
        ClientSegmentGroup.SegmentMember member,
        net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        long gameTime,
        float partialTick,
        VisibleEffectConsumer consumer
    ) {
        VisualEffectRenderer renderer = this.rendererRegistry.find(member.payload().effectId()).orElse(null);
        if (renderer == null) {
            return;
        }
        VisualEffectInstance effect = group.renderInstance(member);
        EffectRenderContext context = this.createRenderContext(effect, gameTime, partialTick);
        if (!this.isVisible(renderer, context, event, member.spatialRadius())) {
            return;
        }
        consumer.accept(effect, context.withLod(selectLod(renderer, event, context, member.spatialRadius())),
            renderer);
    }

    /**
     * Selects the LOD for one visible instance or segment cell. Selection uses the interpolated transform and the
     * renderer's lifetime-conservative spatial radius, so it never needs a per-frame renderer callback.
     */
    private static EffectLod selectLod(
        VisualEffectRenderer renderer,
        net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        EffectRenderContext context,
        double spatialRadius
    ) {
        Vec3 cameraPosition = event.getCamera().getPosition();
        EffectPoint position = context.transform().position();
        return EffectLodSelection.select(renderer.lodProfile(), cameraPosition.x, cameraPosition.y,
            cameraPosition.z, position.x(), position.y(), position.z(), spatialRadius);
    }

    private boolean isVisible(
        VisualEffectRenderer renderer,
        EffectRenderContext context,
        net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        double spatialRadius
    ) {
        if (Double.isFinite(spatialRadius) && spatialRadius >= 0.0) {
            EffectPoint position = context.transform().position();
            AABB coarse = new AABB(
                position.x() - spatialRadius, position.y() - spatialRadius, position.z() - spatialRadius,
                position.x() + spatialRadius, position.y() + spatialRadius, position.z() + spatialRadius
            );
            if (!event.getFrustum().isVisible(coarse)) {
                return false;
            }
        }
        return event.getFrustum().isVisible(this.validatedCullingBounds(renderer, context));
    }

    private AABB queryBox(Vec3 cameraPosition) {
        Minecraft minecraft = Minecraft.getInstance();
        double far = minecraft.gameRenderer == null ? 256.0 : minecraft.gameRenderer.getDepthFar();
        double radius = Math.max(64.0, far) + INDEX_QUERY_MARGIN;
        return new AABB(
            cameraPosition.x - radius, Double.NEGATIVE_INFINITY, cameraPosition.z - radius,
            cameraPosition.x + radius, Double.POSITIVE_INFINITY, cameraPosition.z + radius
        );
    }

    private double spatialRadius(VisualEffectRenderer renderer, VisualEffectInstance effect) {
        EffectRenderContext context = new EffectRenderContext(
            effect, effect.currentTransform(), 0.0F, 0.0F, 0.0F
        );
        double radius = renderer.spatialRadius(context);
        if (Double.isNaN(radius) || radius < 0.0) {
            LOGGER.warn("Renderer returned invalid spatial radius for effect {}; keeping it unindexed",
                effect.effectId());
            return Double.POSITIVE_INFINITY;
        }
        return radius;
    }

    private void indexInstance(UUID instanceId) {
        VisualEffectInstance effect = this.activeEffects.get(instanceId);
        Double radius = this.instanceSpatialRadii.get(instanceId);
        if (effect == null || radius == null) {
            return;
        }
        ClientEffectSpatialIndex.Key key = ClientEffectSpatialIndex.Key.instance(instanceId);
        this.removeIndexEntry(key);
        if (ClientEffectSpatialIndex.isIndexableRadius(radius)) {
            AABB bounds = this.instanceBounds(effect, radius);
            if (ClientEffectSpatialIndex.isIndexableBounds(bounds)) {
                this.spatialIndex.put(key, bounds);
                return;
            }
        }
        this.unindexedInstances.add(key);
    }

    private void indexGroup(UUID coordinatorId) {
        ClientSegmentGroup group = this.segmentedGroups.get(coordinatorId);
        ClientEffectSpatialIndex.Key key = ClientEffectSpatialIndex.Key.group(coordinatorId);
        if (group == null || group.isEmpty()) {
            this.removeIndexEntry(key);
            return;
        }
        this.removeIndexEntry(key);
        if (group.isIndexable()) {
            AABB bounds = group.indexBounds();
            if (ClientEffectSpatialIndex.isIndexableBounds(bounds)) {
                this.spatialIndex.put(key, bounds);
                return;
            }
        }
        this.unindexedGroups.add(key);
    }

    private void removeIndexEntry(ClientEffectSpatialIndex.Key key) {
        this.spatialIndex.remove(key);
        if (key.kind() == ClientEffectSpatialIndex.Kind.INSTANCE) {
            this.unindexedInstances.remove(key);
        } else {
            this.unindexedGroups.remove(key);
        }
    }

    private AABB instanceBounds(VisualEffectInstance effect, double spatialRadius) {
        EffectTransform previous = effect.previousTransform();
        EffectTransform current = effect.currentTransform();
        double dx = current.position().x() - previous.position().x();
        double dy = current.position().y() - previous.position().y();
        double dz = current.position().z() - previous.position().z();
        double movement = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double total = spatialRadius + movement / 2.0;
        double x = (previous.position().x() + current.position().x()) / 2.0;
        double y = (previous.position().y() + current.position().y()) / 2.0;
        double z = (previous.position().z() + current.position().z()) / 2.0;
        return new AABB(x - total, y - total, z - total, x + total, y + total, z + total);
    }

    private ClientSegmentGroup segmentedGroup(
        SegmentLink link,
        StartVisualEffectPayload payload,
        long gameTime
    ) {
        ClientSegmentGroup group = this.segmentedGroups.get(link.coordinatorId());
        if (group == null) {
            EffectTransform base = ClientSegmentGroup.baseFromSegment(link, payload.transform());
            group = new ClientSegmentGroup(link.layout(), base, gameTime);
            this.segmentedGroups.put(link.coordinatorId(), group);
            return group;
        }
        if (!group.layout().equals(link.layout())) {
            LOGGER.warn("Segment layout changed for coordinator {}; replacing the client group",
                link.coordinatorId());
            this.removeIndexEntry(ClientEffectSpatialIndex.Key.group(link.coordinatorId()));
            this.segmentedGroups.remove(link.coordinatorId());
            EffectTransform base = ClientSegmentGroup.baseFromSegment(link, payload.transform());
            group = new ClientSegmentGroup(link.layout(), base, gameTime);
            this.segmentedGroups.put(link.coordinatorId(), group);
        }
        return group;
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
        List<UUID> finished = new ArrayList<>();
        for (Map.Entry<UUID, VisualEffectInstance> entry : this.activeEffects.entrySet()) {
            StartVisualEffectPayload payload = entry.getValue().payload();
            if (!payload.dimensionId().equals(currentDimension)
                || EffectTimeline.isFinished(gameTime, payload.startGameTime(), payload.durationTicks())) {
                finished.add(entry.getKey());
            }
        }
        for (UUID instanceId : finished) {
            this.removeInstance(instanceId);
        }
    }

    /** Removes one instance and detaches it from its coordinator group, dropping empty groups. */
    private void removeInstance(UUID instanceId) {
        VisualEffectInstance removed = this.activeEffects.remove(instanceId);
        if (removed == null) {
            return;
        }
        this.instanceSpatialRadii.remove(instanceId);
        this.removeIndexEntry(ClientEffectSpatialIndex.Key.instance(instanceId));

        SegmentLink link = removed.payload().segmentLink();
        if (link == null) {
            return;
        }
        ClientSegmentGroup group = this.segmentedGroups.get(link.coordinatorId());
        if (group == null) {
            return;
        }
        group.remove(link);
        if (group.isEmpty()) {
            this.segmentedGroups.remove(link.coordinatorId());
            this.removeIndexEntry(ClientEffectSpatialIndex.Key.group(link.coordinatorId()));
        } else {
            this.indexGroup(link.coordinatorId());
        }
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
            this.clearState();
            this.currentLevel = null;
        }
    }

    void onResourceReload(ResourceManager resourceManager) {
        this.rendererRegistry.onResourceReload(resourceManager);
    }

    private void synchronizeLevel(ClientLevel level) {
        if (this.currentLevel != level) {
            this.clearState();
            this.currentLevel = level;
        }
    }

    private void clearState() {
        this.activeEffects.clear();
        this.segmentedGroups.clear();
        this.instanceSpatialRadii.clear();
        this.spatialIndex.clear();
        this.unindexedInstances.clear();
        this.unindexedGroups.clear();
        this.instancedRenderer.close();
    }

    @FunctionalInterface
    private interface VisibleEffectConsumer {
        void accept(VisualEffectInstance effect, EffectRenderContext context, VisualEffectRenderer renderer);
    }
}
