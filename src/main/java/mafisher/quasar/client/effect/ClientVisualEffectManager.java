package mafisher.quasar.client.effect;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import mafisher.quasar.effect.EffectPoint;
import mafisher.quasar.effect.EffectQualityTier;
import mafisher.quasar.effect.EffectTimeline;
import mafisher.quasar.effect.EffectTransform;
import mafisher.quasar.effect.network.ResumeEffectsPayload;
import mafisher.quasar.effect.network.StartSegmentedVisualEffectPayload;
import mafisher.quasar.effect.network.StartVisualEffectPayload;
import mafisher.quasar.effect.network.StopSegmentedVisualEffectPayload;
import mafisher.quasar.effect.network.StopVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateSegmentedVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateVisualEffectPayload;
import mafisher.quasar.effect.scale.SegmentGridLayout;
import mafisher.quasar.effect.scale.SegmentIds;
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
import mafisher.quasar.compat.RenderEnvironment;
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
    private static final int MAX_ACTIVE_STANDALONE_EFFECTS = 128;
    private static final double INDEX_QUERY_MARGIN = 16.0;
    private static final Logger LOGGER = LoggerFactory.getLogger("quasar_lib");

    private final Map<UUID, VisualEffectInstance> activeEffects = new LinkedHashMap<>();
    private final Map<UUID, ClientSegmentGroup> segmentedGroups = new LinkedHashMap<>();
    private final Map<UUID, Double> instanceSpatialRadii = new LinkedHashMap<>();
    private final Map<UUID, EffectLodTracker.State> lodStates = new LinkedHashMap<>();
    private final ClientEffectSpatialIndex spatialIndex = new ClientEffectSpatialIndex();
    private final Set<ClientEffectSpatialIndex.Key> unindexedInstances = new LinkedHashSet<>();
    private final Set<ClientEffectSpatialIndex.Key> unindexedGroups = new LinkedHashSet<>();
    private final VisualEffectRendererRegistry rendererRegistry;
    private final GpuMeshCache meshCache = new GpuMeshCache();
    private final InstancedBatchRenderer instancedRenderer = new InstancedBatchRenderer(this.meshCache);
    private final CachedMeshRenderer cachedMeshRenderer = new CachedMeshRenderer(this.meshCache);
    private RenderEnvironment frameEnvironment = RenderEnvironment.VANILLA;
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
        ClientEffectClock.observe(payload.serverGameTime(), level.getGameTime());
        this.removeInstance(payload.instanceId());
        VisualEffectInstance effect = new VisualEffectInstance(payload, payload.startGameTime());
        double radius = this.spatialRadius(renderer, effect);
        this.activeEffects.put(payload.instanceId(), effect);
        this.instanceSpatialRadii.put(payload.instanceId(), radius);

        SegmentLink segmentLink = payload.segmentLink();
        if (segmentLink != null) {
            ClientSegmentGroup group = this.segmentedGroup(segmentLink, payload, payload.startGameTime());
            group.add(new ClientSegmentGroup.SegmentMember(
                payload.instanceId(), payload, segmentLink, radius
            ));
            this.indexGroup(segmentLink.coordinatorId());
        } else {
            this.indexInstance(payload.instanceId());
            while (this.standaloneCount() > MAX_ACTIVE_STANDALONE_EFFECTS) {
                UUID oldest = this.oldestStandaloneId();
                if (oldest == null) {
                    break;
                }
                this.removeInstance(oldest);
            }
        }
    }

    /** Starts every cell of one segmented coordinator from one aggregated payload. */
    void startSegmented(StartSegmentedVisualEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        this.removeGroup(payload.coordinatorId());
        if (this.rendererRegistry.find(payload.effectId()).isEmpty()) {
            return;
        }

        ClientEffectClock.observe(payload.serverGameTime(), level.getGameTime());
        SegmentGridLayout layout = payload.layout();
        EffectTransform base = payload.transform();
        this.segmentedGroups.put(
            payload.coordinatorId(),
            new ClientSegmentGroup(layout, payload.heights(), base, payload.startGameTime())
        );
        for (int index = 0; index < payload.segmentCount(); index++) {
            int column = layout.column(index);
            int row = layout.row(index);
            int layer = layout.layer(index);
            EffectTransform cell = layout.transform(base, column, row, layer, payload.heights());
            UUID segmentId = SegmentIds.segmentId(payload.coordinatorId(), index);
            long segmentSeed = SegmentIds.segmentSeed(payload.seed(), column, row, layer);
            this.start(new StartVisualEffectPayload(
                payload.effectId(),
                segmentId,
                payload.dimensionId(),
                cell.position().x(),
                cell.position().y(),
                cell.position().z(),
                cell.yaw(),
                cell.pitch(),
                cell.roll(),
                payload.segmentScale(),
                payload.durationTicks(),
                payload.startGameTime(),
                payload.serverGameTime(),
                segmentSeed,
                new SegmentLink(payload.coordinatorId(), index, layout)
            ));
        }
    }

    /** Removes every cell of one segmented coordinator. */
    void stopSegmented(StopSegmentedVisualEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        this.removeGroup(payload.coordinatorId());
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
        for (StartSegmentedVisualEffectPayload effect : payload.segmentedEffects()) {
            this.startSegmented(effect);
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
            ClientEffectClock.observe(payload.updateGameTime(), level.getGameTime());
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
        ClientEffectClock.observe(payload.updateGameTime(), level.getGameTime());
        group.applyBase(payload.transform(), payload.updateGameTime());
        this.indexGroup(payload.coordinatorId());
    }

    void render(net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        RenderEnvironment environment) {
        this.frameEnvironment = Objects.requireNonNull(environment, "environment");
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null) {
            return;
        }
        long gameTime = ClientEffectClock.estimate(level.getGameTime());
        this.removeFinishedEffects(level, gameTime);
        if (this.activeEffects.isEmpty()) {
            return;
        }

        ClientQualityController.beginWorldGpu();
        try {
            Vec3 cameraPosition = event.getCamera().getPosition();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            Set<RenderType> usedRenderTypes = new LinkedHashSet<>();
            InstancedBatchBuilder instancedBatches = new InstancedBatchBuilder(
                this.instancedRenderer.maxInstancesPerDraw(),
                cameraPosition.x, cameraPosition.y, cameraPosition.z
            );
            Matrix4f viewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
            poseStack.pushPose();
            poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
            Matrix4f baseView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
            try {
                this.forEachVisibleEffect(event, (effect, context, renderer) -> {
                    if (renderer instanceof StaticMeshVisualEffectRenderer staticRenderer) {
                        List<EffectStaticMeshPass> staticPasses = staticRenderer.staticMeshPasses(context);
                        if (!staticPasses.isEmpty()) {
                            EffectTransform staticTransform = context.transform();
                            poseStack.pushPose();
                            try {
                                poseStack.translate(staticTransform.position().x(),
                                    staticTransform.position().y(), staticTransform.position().z());
                                EffectRenderTransform.applyRotation(poseStack, staticTransform.yaw(),
                                    staticTransform.pitch(), staticTransform.roll());
                                this.cachedMeshRenderer.draw(staticPasses, poseStack, viewMatrix,
                                    RenderSystem.getProjectionMatrix());
                            } finally {
                                poseStack.popPose();
                            }
                        }
                    }
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
        } finally {
            ClientQualityController.endWorldGpu();
        }
    }

    List<ScreenEffectBatch> screenEffects(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null) {
            return List.of();
        }

        long gameTime = ClientEffectClock.estimate(level.getGameTime());
        this.removeFinishedEffects(level, gameTime);
        if (this.activeEffects.isEmpty()) {
            return List.of();
        }

        List<ScreenEffectFrame> frames = new ArrayList<>();
        this.forEachVisibleEffect(event, (effect, context, renderer) -> {
            if (!(renderer instanceof ScreenEffectRenderer screenRenderer)) {
                return;
            }
            ScreenEffectSettings settings = Objects.requireNonNull(screenRenderer.screenEffectSettings(context),
                "screenEffectSettings");
            ScreenEffectMask mask = Objects.requireNonNull(screenRenderer.screenEffectMask(context),
                "screenEffectMask");
            if (mask.strength() <= 0.0F) {
                return;
            }
            RenderType maskRenderType = Objects.requireNonNull(
                screenRenderer.screenMaskRenderType(context), "screenMaskRenderType");
            SegmentLink link = effect.payload().segmentLink();
            UUID ownerId = link == null ? effect.instanceId() : link.coordinatorId();
            frames.add(new ScreenEffectFrame(ownerId, context, screenRenderer, settings, mask, maskRenderType));
        });
        return ScreenEffectBatch.group(frames);
    }

    void renderScreenMask(net.neoforged.neoforge.client.event.RenderLevelStageEvent event, ScreenEffectBatch batch) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType maskRenderType = batch.maskRenderType();

        RenderSystem.backupProjectionMatrix();
        var modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            // AFTER_LEVEL does not provide the camera-oriented world pose stack used by AFTER_PARTICLES.
            RenderSystem.setProjectionMatrix(new Matrix4f(event.getProjectionMatrix()),
                VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.set(event.getModelViewMatrix());
            RenderSystem.applyModelViewMatrix();

            try {
                for (ScreenEffectFrame frame : batch.frames()) {
                    EffectTransform transform = frame.context().transform();
                    poseStack.pushPose();
                    try {
                        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
                        poseStack.pushPose();
                        try {
                            poseStack.translate(transform.position().x(), transform.position().y(),
                                transform.position().z());
                            EffectRenderTransform.applyRotation(poseStack, transform.yaw(), transform.pitch(),
                                transform.roll());
                            batch.renderer().renderScreenMask(frame.context(), maskRenderType, poseStack, buffers);
                        } finally {
                            poseStack.popPose();
                        }
                    } finally {
                        poseStack.popPose();
                    }
                }
            } finally {
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
        long gameTime = ClientEffectClock.estimate(level.getGameTime());
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
        EffectQualityTier quality = ClientQualityController.tier();
        context = context.withQuality(quality);
        double radius = this.instanceSpatialRadii.getOrDefault(instanceId, Double.POSITIVE_INFINITY);
        if (!this.isVisible(renderer, context, event, radius)) {
            return;
        }
        consumer.accept(effect, context.withLod(selectLod(instanceId, renderer, event, context, radius, quality,
            gameTime, partialTick)), renderer);
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
        EffectQualityTier quality = ClientQualityController.tier();
        context = context.withQuality(quality);
        if (!this.isVisible(renderer, context, event, member.spatialRadius())) {
            return;
        }
        consumer.accept(effect, context.withLod(selectLod(member.instanceId(), renderer, event, context,
            member.spatialRadius(), quality, gameTime, partialTick)), renderer);
    }

    /**
     * Selects the LOD for one visible instance or segment cell. Selection uses the interpolated transform and the
     * renderer's lifetime-conservative spatial radius, so it never needs a per-frame renderer callback. A small
     * per-instance state adds threshold hysteresis and a transition blend.
     */
    private EffectLod selectLod(
        UUID key,
        VisualEffectRenderer renderer,
        net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        EffectRenderContext context,
        double spatialRadius,
        EffectQualityTier quality,
        long gameTime,
        float partialTick
    ) {
        Vec3 cameraPosition = event.getCamera().getPosition();
        EffectPoint position = context.transform().position();
        EffectLodProfile profile = renderer.lodProfile();
        EffectLod selected = EffectLodSelection.select(profile, cameraPosition.x, cameraPosition.y,
            cameraPosition.z, position.x(), position.y(), position.z(), spatialRadius,
            quality.lodDistanceMultiplier());
        EffectLodTracker.State state = this.lodStates.computeIfAbsent(key,
            ignored -> new EffectLodTracker.State());
        return EffectLodTracker.update(state, profile, selected.cameraDistance(),
            selected.gapInRadii(), gameTime + (double)partialTick);
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
        double radius = Math.max(64.0, Math.max(far, this.frameEnvironment.horizonDistance())) + INDEX_QUERY_MARGIN;
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
        this.lodStates.remove(instanceId);
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

    /** Removes one coordinator group and every payload carrier it owns. */
    private void removeGroup(UUID coordinatorId) {
        ClientSegmentGroup group = this.segmentedGroups.remove(coordinatorId);
        this.removeIndexEntry(ClientEffectSpatialIndex.Key.group(coordinatorId));
        if (group == null) {
            return;
        }
        for (UUID instanceId : group.memberIds()) {
            this.activeEffects.remove(instanceId);
            this.instanceSpatialRadii.remove(instanceId);
            this.lodStates.remove(instanceId);
            this.removeIndexEntry(ClientEffectSpatialIndex.Key.instance(instanceId));
        }
    }

    private int standaloneCount() {
        int count = 0;
        for (VisualEffectInstance effect : this.activeEffects.values()) {
            if (effect.payload().segmentLink() == null) {
                count++;
            }
        }
        return count;
    }

    private UUID oldestStandaloneId() {
        for (Map.Entry<UUID, VisualEffectInstance> entry : this.activeEffects.entrySet()) {
            if (entry.getValue().payload().segmentLink() == null) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Interpolated origin used by transform-following audio cues, or null when no visual exists. */
    Vec3 effectOrigin(UUID effectId) {
        ClientLevel level = this.currentLevel;
        if (level == null) {
            return null;
        }
        VisualEffectInstance instance = this.activeEffects.get(effectId);
        if (instance != null) {
            EffectPoint position = instance.interpolatedTransform(level.getGameTime(), 0.0F).position();
            return new Vec3(position.x(), position.y(), position.z());
        }
        ClientSegmentGroup group = this.segmentedGroups.get(effectId);
        if (group != null) {
            EffectPoint position = group.interpolatedBase(level.getGameTime(), 0.0F).position();
            return new Vec3(position.x(), position.y(), position.z());
        }
        return null;
    }

    private EffectRenderContext createRenderContext(VisualEffectInstance effect, long gameTime, float partialTick) {
        StartVisualEffectPayload payload = effect.payload();
        EffectTransform transform = effect.interpolatedTransform(gameTime, partialTick);
        float age = (float)EffectTimeline.ageTicks(gameTime, partialTick, payload.startGameTime());
        float progress = EffectTimeline.progress(gameTime, partialTick, payload.startGameTime(),
            payload.durationTicks());
        return new EffectRenderContext(effect, transform, age, progress, partialTick,
            EffectLod.FULL, EffectQualityTier.HIGH, this.frameEnvironment);
    }

    void onLevelUnload(ClientLevel level) {
        if (this.currentLevel == level) {
            this.clearState();
            this.currentLevel = null;
        }
    }

    /** Drops every client-side instance and GPU resource without waiting for a level reference. */
    void clear() {
        this.clearState();
        this.currentLevel = null;
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
        ClientEffectClock.reset();
        this.activeEffects.clear();
        this.segmentedGroups.clear();
        this.instanceSpatialRadii.clear();
        this.lodStates.clear();
        this.spatialIndex.clear();
        this.unindexedInstances.clear();
        this.unindexedGroups.clear();
        this.instancedRenderer.close();
        this.meshCache.close();
    }

    @FunctionalInterface
    private interface VisibleEffectConsumer {
        void accept(VisualEffectInstance effect, EffectRenderContext context, VisualEffectRenderer renderer);
    }
}
