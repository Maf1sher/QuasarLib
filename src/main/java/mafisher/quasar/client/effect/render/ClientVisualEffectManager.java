package mafisher.quasar.client.effect.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import mafisher.quasar.api.client.render.EffectCameraSnapshot;
import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectLod;
import mafisher.quasar.api.client.render.EffectLodProfile;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderPass;
import mafisher.quasar.api.client.render.EffectStaticMeshPass;
import mafisher.quasar.api.client.render.InstancedVisualEffectRenderer;
import mafisher.quasar.api.client.screen.ScreenEffectMask;
import mafisher.quasar.api.client.screen.ScreenEffectRenderer;
import mafisher.quasar.api.client.screen.ScreenEffectSettings;
import mafisher.quasar.api.client.render.StaticMeshVisualEffectRenderer;
import mafisher.quasar.api.client.render.VisualEffectInstance;
import mafisher.quasar.api.client.render.VisualEffectRenderer;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.compat.RenderEnvironment;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.scale.SegmentGridLayout;
import mafisher.quasar.api.scale.SegmentLink;
import mafisher.quasar.api.scale.StartSegmentedVisualEffectPayload;
import mafisher.quasar.client.effect.frame.ClientEffectClock;
import mafisher.quasar.client.effect.quality.ClientFarPlane;
import mafisher.quasar.client.effect.quality.EffectLodSelection;
import mafisher.quasar.client.effect.quality.EffectLodTracker;
import mafisher.quasar.effect.network.EffectParameterBatchPayload;
import mafisher.quasar.effect.network.ResumeEffectsPayload;
import mafisher.quasar.effect.network.StopSegmentedVisualEffectPayload;
import mafisher.quasar.effect.network.StopVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateSegmentedVisualEffectPayload;
import mafisher.quasar.effect.network.UpdateVisualEffectPayload;
import mafisher.quasar.effect.scale.SegmentIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import mafisher.quasar.client.effect.quality.ClientQualityController;

/** Instance-owned state and rendering for transient client visual effects. */
@ApiStatus.Internal
public final class ClientVisualEffectManager {
    private static final int MAX_ACTIVE_STANDALONE_EFFECTS = 128;
    private static final double INDEX_QUERY_MARGIN = 16.0;
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientVisualEffectManager.class);

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
    private final List<VisibleEffect> visibleEffects = new ArrayList<>();
    private final List<ScreenEffectFrame> screenFrames = new ArrayList<>();
    private final Set<RenderType> usedRenderTypes = new LinkedHashSet<>();
    private final Set<String> passIds = new HashSet<>();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f baseView = new Matrix4f();
    private final List<InstancedBatchBuilder.InstancedDraw> instancedDraws = new ArrayList<>();
    private final List<UUID> finishedEffects = new ArrayList<>();
    private final FrameSnapshotCache frameCache = new FrameSnapshotCache();
    private final ClientAttachmentResolver attachmentResolver;
    private final Map<UUID, Entity> frameEntities = new HashMap<>();
    private boolean frameEntitiesBuilt;
    private long frameEntitiesGameTime = Long.MIN_VALUE;
    private boolean screenEffectStateChanged;
    private RenderEnvironment frameEnvironment = RenderEnvironment.VANILLA;
    private InstancedBatchBuilder instancedBatchBuilder;
    private List<ScreenEffectBatch> screenBatches = List.of();
    private int screenBatchTick = Integer.MIN_VALUE;
    private ClientLevel currentLevel;
    private EffectCameraSnapshot frameCamera;

    public ClientVisualEffectManager(VisualEffectRendererRegistry rendererRegistry) {
        this.rendererRegistry = rendererRegistry;
        this.attachmentResolver = new ClientAttachmentResolver(this.activeEffects,
            new ClientAttachmentResolver.ParentFrames() {
                @Override
                public EffectTransform entity(UUID entityId, float partialTick) {
                    return ClientVisualEffectManager.this.entityFrame(entityId, partialTick);
                }

                @Override
                public EffectTransform subLevel(UUID subLevelId, float partialTick) {
                    return CompatRegistry.subLevels().pose(subLevelId).orElse(null);
                }

                @Override
                public boolean subLevelAvailable() {
                    return CompatRegistry.subLevels().available();
                }
            });
    }

    public void start(StartVisualEffectPayload payload) {
        this.invalidateFrame();
        this.screenEffectStateChanged = true;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        VisualEffectRenderer renderer = this.rendererRegistry.find(payload.effectId()).orElse(null);
        if (renderer == null) {
            LOGGER.warn(
                "No client renderer is registered for effect {} (instance {}); dropping the start payload. "
                    + "Check that RegisterVisualEffectRenderersEvent registration runs before the first effect.",
                payload.effectId(),
                payload.instanceId()
            );
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
    public void startSegmented(StartSegmentedVisualEffectPayload payload) {
        this.invalidateFrame();
        this.screenEffectStateChanged = true;
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
    public void stopSegmented(StopSegmentedVisualEffectPayload payload) {
        this.invalidateFrame();
        this.screenEffectStateChanged = true;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        this.removeGroup(payload.coordinatorId());
    }

    public void stop(StopVisualEffectPayload payload) {
        this.invalidateFrame();
        this.screenEffectStateChanged = true;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        this.removeInstance(payload.instanceId());
    }

    /** Replaces the local visual snapshot for one dimension with the server's resume list. */
    public void resume(ResumeEffectsPayload payload) {
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

    public void update(UpdateVisualEffectPayload payload) {
        this.invalidateFrame();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        VisualEffectInstance effect = this.activeEffects.get(payload.instanceId());
        if (effect != null && effect.payload().segmentLink() == null
            && effect.payload().attachment() == null) {
            ClientEffectClock.observe(payload.updateGameTime(), level.getGameTime());
            this.activeEffects.put(payload.instanceId(),
                effect.withTransform(payload.transform(), payload.updateGameTime()));
            this.indexInstance(payload.instanceId());
        }
    }

    /** Replaces parameter overrides for standalone visual instances. */
    public void updateParameters(EffectParameterBatchPayload payload) {
        this.invalidateFrame();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null || !level.dimension().location().equals(payload.dimensionId())) {
            return;
        }
        for (EffectParameterBatchPayload.Entry update : payload.updates()) {
            VisualEffectInstance effect = this.activeEffects.get(update.instanceId());
            if (effect != null && effect.payload().segmentLink() == null) {
                this.activeEffects.put(update.instanceId(), effect.withParameters(update.state()));
            }
        }
    }

    /** Stores one aggregated coordinator update; segment transforms are derived only for visible cells. */
    public void updateSegmented(UpdateSegmentedVisualEffectPayload payload) {
        this.invalidateFrame();
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

    public void render(net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        RenderEnvironment environment) {
        this.ensureFrame(event, environment);
        if (this.visibleEffects.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientQualityController.beginWorldGpu();
        try {
            Vec3 cameraPosition = event.getCamera().getPosition();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            InstancedBatchBuilder instancedBatches = this.instancedBatchBuilder(cameraPosition);
            this.viewMatrix.set(RenderSystem.getModelViewMatrix());
            poseStack.pushPose();
            poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
            this.baseView.set(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
            try {
                this.usedRenderTypes.clear();
                for (VisibleEffect visible : this.visibleEffects) {
                    this.renderWorldPasses(visible, poseStack, buffers, instancedBatches);
                }
            } finally {
                poseStack.popPose();
                for (RenderType renderType : this.usedRenderTypes) {
                    buffers.endBatch(renderType);
                }
            }
            instancedBatches.drawInto(this.instancedDraws);
            this.instancedRenderer.draw(this.instancedDraws, this.baseView,
                RenderSystem.getProjectionMatrix());
        } finally {
            ClientQualityController.endWorldGpu();
        }
    }

    private void renderWorldPasses(VisibleEffect visible, PoseStack poseStack,
        MultiBufferSource.BufferSource buffers, InstancedBatchBuilder instancedBatches) {
        VisualEffectRenderer renderer = visible.renderer();
        EffectRenderContext context = visible.context();
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
                    this.cachedMeshRenderer.draw(staticPasses, poseStack, this.viewMatrix,
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
        List<EffectRenderPass> renderPasses = renderer.renderPasses(context);
        this.passIds.clear();
        for (EffectRenderPass renderPass : renderPasses) {
            if (!this.passIds.add(renderPass.id())) {
                throw new IllegalStateException("Duplicate render pass ID '" + renderPass.id()
                    + "' for effect " + visible.effect().effectId());
            }
        }
        poseStack.pushPose();
        try {
            poseStack.translate(transform.position().x(), transform.position().y(),
                transform.position().z());
            EffectRenderTransform.applyRotation(poseStack, transform.yaw(), transform.pitch(),
                transform.roll());
            for (EffectRenderPass renderPass : renderPasses) {
                this.usedRenderTypes.add(renderPass.renderType());
                renderer.render(context, renderPass, poseStack, buffers);
            }
        } finally {
            poseStack.popPose();
        }
    }

    public List<ScreenEffectBatch> screenEffects(net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        RenderEnvironment environment) {
        this.ensureFrame(event, environment);
        if (this.visibleEffects.isEmpty()) {
            return List.of();
        }
        if (this.screenBatchTick == this.frameCache.tick()) {
            return this.screenBatches;
        }

        this.screenFrames.clear();
        for (VisibleEffect visible : this.visibleEffects) {
            if (!(visible.renderer() instanceof ScreenEffectRenderer screenRenderer)) {
                continue;
            }
            EffectRenderContext context = visible.context();
            ScreenEffectSettings settings = Objects.requireNonNull(screenRenderer.screenEffectSettings(context),
                "screenEffectSettings");
            ScreenEffectMask mask = Objects.requireNonNull(screenRenderer.screenEffectMask(context),
                "screenEffectMask");
            if (mask.strength() <= 0.0F) {
                continue;
            }
            RenderType maskRenderType = Objects.requireNonNull(
                screenRenderer.screenMaskRenderType(context), "screenMaskRenderType");
            SegmentLink link = visible.effect().payload().segmentLink();
            UUID ownerId = link == null ? visible.effect().instanceId() : link.coordinatorId();
            this.screenFrames.add(new ScreenEffectFrame(ownerId, context, screenRenderer, settings, mask,
                maskRenderType));
        }
        this.screenBatches = ScreenEffectBatch.group(this.screenFrames);
        this.screenBatchTick = this.frameCache.tick();
        return this.screenBatches;
    }

    public void renderScreenMask(
        net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        ScreenEffectBatch batch
    ) {
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

    private void ensureFrame(
        net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        RenderEnvironment environment
    ) {
        this.frameEnvironment = Objects.requireNonNull(environment, "environment");
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        this.synchronizeLevel(level);
        if (level == null) {
            this.visibleEffects.clear();
            this.screenBatches = List.of();
            this.screenBatchTick = Integer.MIN_VALUE;
            return;
        }

        if (!this.frameCache.shouldBuild(event.getRenderTick())) {
            return;
        }
        this.frameCamera = captureCamera(event);
        if (this.frameCamera == null) {
            this.visibleEffects.clear();
            this.screenBatches = List.of();
            this.screenBatchTick = Integer.MIN_VALUE;
            return;
        }
        this.beginAttachmentFrame();
        long gameTime = ClientEffectClock.estimate(level.getGameTime());
        this.removeFinishedEffects(level, gameTime);
        this.visibleEffects.clear();
        this.screenBatches = List.of();
        this.screenBatchTick = Integer.MIN_VALUE;
        if (this.activeEffects.isEmpty()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        this.forEachVisibleEffect(event, gameTime, partialTick, (effect, context, renderer) ->
            this.visibleEffects.add(new VisibleEffect(effect, context, renderer)));
    }

    private void forEachVisibleEffect(
        net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        long gameTime,
        float partialTick,
        VisibleEffectConsumer consumer
    ) {
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
        EffectTransform transform = this.attachmentResolver.resolve(effect, gameTime, partialTick);
        if (transform == null) {
            return;
        }
        EffectQualityTier quality = ClientQualityController.tier();
        EffectRenderContext context = this.createRenderContext(
            effect, transform, gameTime, partialTick, quality
        );
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
        EffectQualityTier quality = ClientQualityController.tier();
        EffectRenderContext context = this.createRenderContext(effect, gameTime, partialTick, quality);
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
        double far = ClientFarPlane.current();
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
        if (effect.payload().attachment() != null) {
            // Parent movement invalidates stored bounds; visit attached instances every frame.
            this.unindexedInstances.add(key);
            return;
        }
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
        this.finishedEffects.clear();
        for (Map.Entry<UUID, VisualEffectInstance> entry : this.activeEffects.entrySet()) {
            StartVisualEffectPayload payload = entry.getValue().payload();
            if (!payload.dimensionId().equals(currentDimension)
                || EffectTimeline.isFinished(gameTime, payload.startGameTime(), payload.durationTicks())) {
                this.finishedEffects.add(entry.getKey());
            }
        }
        for (UUID instanceId : this.finishedEffects) {
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
        this.attachmentResolver.forget(instanceId);
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
            this.attachmentResolver.forget(instanceId);
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

    /** Returns whether any active instance can contribute a screen mask this frame. */
    public boolean hasActiveScreenEffects() {
        for (VisualEffectInstance instance : this.activeEffects.values()) {
            VisualEffectRenderer renderer = this.rendererRegistry.find(instance.effectId()).orElse(null);
            if (renderer instanceof ScreenEffectRenderer) {
                return true;
            }
        }
        return false;
    }

    /** Consumes the one-frame conservative capture request raised by a start, stop, or resume. */
    public boolean consumeScreenEffectStateChange() {
        boolean changed = this.screenEffectStateChanged;
        this.screenEffectStateChanged = false;
        return changed;
    }

    /** Interpolated origin used by transform-following audio cues, or null when no visual exists. */
    public Vec3 effectOrigin(UUID effectId) {
        ClientLevel level = this.currentLevel;
        if (level == null) {
            return null;
        }
        VisualEffectInstance instance = this.activeEffects.get(effectId);
        if (instance != null) {
            this.beginAttachmentFrame();
            EffectTransform resolved = this.attachmentResolver.resolve(
                instance, level.getGameTime(), 0.0F
            );
            if (resolved == null) {
                return null;
            }
            EffectPoint position = resolved.position();
            return new Vec3(position.x(), position.y(), position.z());
        }
        ClientSegmentGroup group = this.segmentedGroups.get(effectId);
        if (group != null) {
            EffectPoint position = group.interpolatedBase(level.getGameTime(), 0.0F).position();
            return new Vec3(position.x(), position.y(), position.z());
        }
        return null;
    }

    private EffectRenderContext createRenderContext(VisualEffectInstance effect, long gameTime, float partialTick,
        EffectQualityTier quality) {
        return this.createRenderContext(
            effect, effect.interpolatedTransform(gameTime, partialTick), gameTime, partialTick, quality
        );
    }

    private EffectRenderContext createRenderContext(
        VisualEffectInstance effect,
        EffectTransform transform,
        long gameTime,
        float partialTick,
        EffectQualityTier quality
    ) {
        StartVisualEffectPayload payload = effect.payload();
        float age = (float)EffectTimeline.ageTicks(gameTime, partialTick, payload.startGameTime());
        float progress = EffectTimeline.progress(gameTime, partialTick, payload.startGameTime(),
            payload.durationTicks());
        float daylight = EffectLighting.daylight(this.currentLevel, transform.position());
        return new EffectRenderContext(effect, transform, age, progress, partialTick,
            EffectLod.FULL, quality, this.frameEnvironment, this.frameCamera, daylight);
    }

    private void beginAttachmentFrame() {
        this.attachmentResolver.beginFrame();
    }

    @Nullable
    private EffectTransform entityFrame(UUID entityId, float partialTick) {
        ClientLevel level = this.currentLevel;
        if (level == null) {
            return null;
        }
        Entity entity = this.entityById(level, entityId);
        if (entity == null) {
            return null;
        }
        return new EffectTransform(
            new EffectPoint(
                Mth.lerp(partialTick, entity.xOld, entity.getX()),
                Mth.lerp(partialTick, entity.yOld, entity.getY()),
                Mth.lerp(partialTick, entity.zOld, entity.getZ())
            ),
            Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot()),
            Mth.lerp(partialTick, entity.xRotO, entity.getXRot()),
            0.0F
        );
    }

    @Nullable
    private Entity entityById(ClientLevel level, UUID entityId) {
        long gameTime = level.getGameTime();
        if (!this.frameEntitiesBuilt || this.frameEntitiesGameTime != gameTime) {
            this.frameEntities.clear();
            for (Entity entity : level.players()) {
                this.frameEntities.put(entity.getUUID(), entity);
            }
            for (Entity entity : level.entitiesForRendering()) {
                this.frameEntities.put(entity.getUUID(), entity);
            }
            this.frameEntitiesBuilt = true;
            this.frameEntitiesGameTime = gameTime;
        }
        return this.frameEntities.get(entityId);
    }

    private static EffectCameraSnapshot captureCamera(
        net.neoforged.neoforge.client.event.RenderLevelStageEvent event
    ) {
        Matrix4f modelView = new Matrix4f(event.getModelViewMatrix());
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        if (!EffectCameraSnapshot.isUsable(modelView, projection)) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = Math.max(1, minecraft.getMainRenderTarget().width);
        int height = Math.max(1, minecraft.getMainRenderTarget().height);
        return new EffectCameraSnapshot(
            event.getCamera().getPosition(),
            modelView,
            projection,
            width,
            height
        );
    }

    private InstancedBatchBuilder instancedBatchBuilder(Vec3 cameraPosition) {
        int maxInstances = this.instancedRenderer.maxInstancesPerDraw();
        if (this.instancedBatchBuilder == null
            || this.instancedBatchBuilder.maxInstancesPerDraw() != maxInstances) {
            this.instancedBatchBuilder = new InstancedBatchBuilder(
                maxInstances, cameraPosition.x, cameraPosition.y, cameraPosition.z
            );
        } else {
            this.instancedBatchBuilder.reset(cameraPosition.x, cameraPosition.y, cameraPosition.z);
        }
        return this.instancedBatchBuilder;
    }

    private void invalidateFrame() {
        this.frameCache.invalidate();
        this.screenBatchTick = Integer.MIN_VALUE;
        this.visibleEffects.clear();
        this.screenFrames.clear();
        this.screenBatches = List.of();
    }

    public void onLevelUnload(ClientLevel level) {
        if (this.currentLevel == level) {
            this.clearState();
            this.currentLevel = null;
        }
    }

    /** Drops every client-side instance and GPU resource without waiting for a level reference. */
    public void clear() {
        this.clearState();
        this.currentLevel = null;
    }

    public void onResourceReload(ResourceManager resourceManager) {
        this.rendererRegistry.onResourceReload(resourceManager);
        this.instancedRenderer.onResourceReload();
    }

    private void synchronizeLevel(ClientLevel level) {
        if (this.currentLevel != level) {
            this.clearState();
            this.currentLevel = level;
        }
    }

    private void clearState() {
        this.invalidateFrame();
        this.frameCamera = null;
        ClientEffectClock.reset();
        this.activeEffects.clear();
        this.segmentedGroups.clear();
        this.instanceSpatialRadii.clear();
        this.lodStates.clear();
        this.attachmentResolver.clear();
        this.frameEntities.clear();
        this.frameEntitiesBuilt = false;
        this.frameEntitiesGameTime = Long.MIN_VALUE;
        this.spatialIndex.clear();
        this.unindexedInstances.clear();
        this.unindexedGroups.clear();
        this.instancedRenderer.close();
        this.meshCache.close();
    }

    private record VisibleEffect(VisualEffectInstance effect, EffectRenderContext context,
        VisualEffectRenderer renderer) {
        private VisibleEffect {
            Objects.requireNonNull(effect, "effect");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(renderer, "renderer");
        }
    }

    @FunctionalInterface
    private interface VisibleEffectConsumer {
        void accept(VisualEffectInstance effect, EffectRenderContext context, VisualEffectRenderer renderer);
    }
}
