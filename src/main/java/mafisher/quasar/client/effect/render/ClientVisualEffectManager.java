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
import mafisher.quasar.api.client.render.EffectDepthOcclusion;
import mafisher.quasar.api.client.render.EffectInstancedPass;
import mafisher.quasar.api.client.render.EffectLod;
import mafisher.quasar.api.client.render.EffectLodProfile;
import mafisher.quasar.api.client.render.EffectRenderContext;
import mafisher.quasar.api.client.render.EffectRenderPass;
import mafisher.quasar.api.client.render.EffectScreenLodProfile;
import mafisher.quasar.api.client.render.EffectScreenMetrics;
import mafisher.quasar.api.client.render.EffectStaticMeshPass;
import mafisher.quasar.api.client.particle.EffectParticlePass;
import mafisher.quasar.api.client.particle.ParticleVisualEffectRenderer;
import mafisher.quasar.api.client.render.InstancedVisualEffectRenderer;
import mafisher.quasar.api.client.screen.ScreenEffectMask;
import mafisher.quasar.api.client.screen.ScreenEffectRenderer;
import mafisher.quasar.api.client.screen.ScreenEffectSettings;
import mafisher.quasar.api.client.render.StaticMeshVisualEffectRenderer;
import mafisher.quasar.api.client.render.VisualEffectInstance;
import mafisher.quasar.api.client.render.VisualEffectRenderer;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.compat.RenderEnvironment;
import mafisher.quasar.api.compat.SceneDepthFrame;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.budget.EffectQualityTier;
import mafisher.quasar.api.effect.EffectTimeline;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.scale.SegmentGridLayout;
import mafisher.quasar.api.scale.SegmentLink;
import mafisher.quasar.api.scale.StartSegmentedVisualEffectPayload;
import mafisher.quasar.QuasarClientConfig;
import mafisher.quasar.client.effect.frame.ClientEffectClock;
import mafisher.quasar.client.effect.inspect.InspectFrame;
import mafisher.quasar.client.effect.quality.ClientFarPlane;
import mafisher.quasar.client.effect.quality.EffectLodSelection;
import mafisher.quasar.client.effect.quality.EffectLodTracker;
import mafisher.quasar.client.effect.quality.EffectQualityScoring;
import mafisher.quasar.client.effect.quality.EffectQualityTracker;
import mafisher.quasar.client.effect.quality.EffectScreenProjection;
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
    private final Map<UUID, EffectQualityTracker.State> qualityStates = new LinkedHashMap<>();
    private final Map<UUID, Boolean> frameGroupOcclusionEnabled = new HashMap<>();
    private final Set<UUID> frameSkippedByDepth = new LinkedHashSet<>();
    private final Set<UUID> inspectCapturedIds = new HashSet<>();
    private final float[] inspectMatrix = new float[16];
    private final ClientEffectSpatialIndex spatialIndex = new ClientEffectSpatialIndex();
    private final Set<ClientEffectSpatialIndex.Key> unindexedInstances = new LinkedHashSet<>();
    private final Set<ClientEffectSpatialIndex.Key> unindexedGroups = new LinkedHashSet<>();
    private final VisualEffectRendererRegistry rendererRegistry;
    private final GpuMeshCache meshCache = new GpuMeshCache();
    private final InstancedBatchRenderer instancedRenderer = new InstancedBatchRenderer(this.meshCache);
    private final ParticleBatchRenderer particleRenderer = new ParticleBatchRenderer(this.meshCache);
    private final CachedMeshRenderer cachedMeshRenderer = new CachedMeshRenderer(this.meshCache);
    private final StaticMeshBatchRenderer staticMeshRenderer =
        new StaticMeshBatchRenderer(this.instancedRenderer, this.cachedMeshRenderer, this.meshCache);
    private final List<VisibleEffect> visibleEffects = new ArrayList<>();
    private final List<ScreenEffectFrame> screenFrames = new ArrayList<>();
    private final Set<RenderType> usedRenderTypes = new LinkedHashSet<>();
    private final Set<String> passIds = new HashSet<>();
    private final List<String> staticPassIds = new ArrayList<>();
    private final Matrix4f viewMatrix = new Matrix4f();
    private final List<InstancedBatchBuilder.InstancedDraw> instancedDraws = new ArrayList<>();
    private final List<ParticleBatchBuilder.Draw> particleDraws = new ArrayList<>();
    private final List<UUID> finishedEffects = new ArrayList<>();
    private final FrameSnapshotCache frameCache = new FrameSnapshotCache();
    private final ClientAttachmentResolver attachmentResolver;
    private final ClientDepthOcclusion depthOcclusion;
    private final Map<UUID, Entity> frameEntities = new HashMap<>();
    private boolean frameEntitiesBuilt;
    private long frameEntitiesGameTime = Long.MIN_VALUE;
    private boolean screenEffectStateChanged;
    private RenderEnvironment frameEnvironment = RenderEnvironment.VANILLA;
    private InstancedBatchBuilder instancedBatchBuilder;
    private StaticMeshBatchBuilder staticMeshBatchBuilder;
    private ParticleBatchBuilder particleBatchBuilder;
    private List<ScreenEffectBatch> screenBatches = List.of();
    private int screenBatchTick = Integer.MIN_VALUE;
    private ClientLevel currentLevel;
    private EffectCameraSnapshot frameCamera;
    private EffectScreenProjection frameProjection;

    public ClientVisualEffectManager(VisualEffectRendererRegistry rendererRegistry, ResourceManager resources) {
        this(rendererRegistry, resources, new ClientDepthOcclusion(new GlDepthProbeBackend()));
    }

    ClientVisualEffectManager(VisualEffectRendererRegistry rendererRegistry, ResourceManager resources,
        ClientDepthOcclusion depthOcclusion) {
        this.rendererRegistry = Objects.requireNonNull(rendererRegistry, "rendererRegistry");
        this.depthOcclusion = Objects.requireNonNull(depthOcclusion, "depthOcclusion");
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
        rendererRegistry.onResourceReload(Objects.requireNonNull(resources, "resources"));
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
        RenderEnvironment environment, @Nullable SceneDepthFrame depthFrame) {
        this.ensureFrame(event, environment);
        this.depthOcclusion.beginFrame(depthFrame, this.frameCamera);
        if (this.visibleEffects.isEmpty()) {
            this.instancedDraws.clear();
            this.particleDraws.clear();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientQualityController.beginWorldGpu();
        try {
            Vec3 cameraPosition = event.getCamera().getPosition();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            InstancedBatchBuilder instancedBatches = this.instancedBatchBuilder(cameraPosition);
            StaticMeshBatchBuilder staticBatches = this.staticMeshBatchBuilder(cameraPosition);
            ParticleBatchBuilder particleBatches = this.particleBatchBuilder(cameraPosition);
            this.viewMatrix.set(RenderSystem.getModelViewMatrix());
            poseStack.pushPose();
            poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
            try {
                this.usedRenderTypes.clear();
                for (VisibleEffect visible : this.visibleEffects) {
                    this.renderWorldPasses(visible, poseStack, buffers, instancedBatches,
                        staticBatches, particleBatches);
                }
                this.staticMeshRenderer.draw(staticBatches, this.viewMatrix,
                    RenderSystem.getProjectionMatrix());
            } finally {
                poseStack.popPose();
                for (RenderType renderType : this.usedRenderTypes) {
                    buffers.endBatch(renderType);
                }
            }
            instancedBatches.drawInto(this.instancedDraws);
            this.instancedRenderer.draw(this.instancedDraws, this.viewMatrix,
                RenderSystem.getProjectionMatrix());
            this.particleDraws.clear();
            this.particleDraws.addAll(particleBatches.draws());
            this.particleRenderer.draw(this.particleDraws, this.viewMatrix,
                RenderSystem.getProjectionMatrix(), this.frameCamera, depthFrame);
        } finally {
            ClientQualityController.endWorldGpu();
        }
    }

    private void renderWorldPasses(VisibleEffect visible, PoseStack poseStack,
        MultiBufferSource.BufferSource buffers, InstancedBatchBuilder instancedBatches,
        StaticMeshBatchBuilder staticBatches, ParticleBatchBuilder particleBatches) {
        if (!this.shouldRenderDepth(visible)) {
            this.frameSkippedByDepth.add(visible.effect().instanceId());
            return;
        }
        VisualEffectRenderer renderer = visible.renderer();
        EffectRenderContext context = visible.context();
        this.collectParticlePasses(renderer, context, particleBatches);
        if (renderer instanceof StaticMeshVisualEffectRenderer staticRenderer) {
            List<EffectStaticMeshPass> staticPasses = staticRenderer.staticMeshPasses(context);
            if (!staticPasses.isEmpty()) {
                this.validateStaticPassIds(staticPasses, visible);
                EffectTransform staticTransform = context.transform();
                for (EffectStaticMeshPass pass : staticPasses) {
                    staticBatches.add(pass, EffectRenderTypes.staticBatchType(pass.renderType()),
                        staticTransform);
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

    private void collectParticlePasses(VisualEffectRenderer renderer, EffectRenderContext context,
        ParticleBatchBuilder particleBatches) {
        if (renderer instanceof ParticleVisualEffectRenderer particleRenderer) {
            List<EffectParticlePass> particlePasses = particleRenderer.particlePasses(context);
            if (!particlePasses.isEmpty()) {
                particleBatches.add(context, particlePasses);
            }
        }
    }

    private boolean shouldRenderDepth(VisibleEffect visible) {
        if (visible.occlusionBounds() == null) {
            return true;
        }
        boolean enabled = visible.effect().payload().segmentLink() == null
            ? visible.depthOcclusionEnabled()
            : this.frameGroupOcclusionEnabled.getOrDefault(visible.ownerId(), false);
        return this.shouldRenderDepth(visible.ownerId(), visible.occlusionBounds(), enabled);
    }

    boolean shouldRenderDepth(UUID ownerId, @Nullable AABB bounds, boolean enabled) {
        return bounds == null || this.depthOcclusion.shouldDraw(ownerId, bounds, enabled);
    }

    /** Returns one diagnostic line for the client compatibility command. */
    public String depthOcclusionStatus() {
        return this.depthOcclusion.status();
    }

    /** Copies visible and hidden effect state into the inspector's bounded frame. */
    public void captureInspect(InspectFrame frame) {
        Objects.requireNonNull(frame, "frame");
        frame.counts(this.activeEffects.size(), this.segmentedGroups.size(),
            this.particleDraws.size(), 0);
        this.inspectCapturedIds.clear();
        for (VisibleEffect visible : this.visibleEffects) {
            InspectFrame.EffectSlot slot = frame.addEffect();
            if (slot == null) {
                break;
            }
            fillVisible(slot, visible);
            this.inspectCapturedIds.add(visible.effect().instanceId());
        }
        for (VisualEffectInstance effect : this.activeEffects.values()) {
            if (this.inspectCapturedIds.contains(effect.instanceId())) {
                continue;
            }
            InspectFrame.EffectSlot slot = frame.addEffect();
            if (slot == null) {
                break;
            }
            fillHidden(slot, effect, frame.gameTime());
        }
        captureDraws(frame);
    }

    /** Copies screen-effect batches after composition has produced them. */
    public void captureInspectScreens(InspectFrame frame) {
        Objects.requireNonNull(frame, "frame");
        frame.resetScreens();
        for (ScreenEffectBatch batch : this.screenBatches) {
            InspectFrame.ScreenSlot slot = frame.addScreen();
            if (slot == null) {
                break;
            }
            ScreenEffectFrame first = batch.frames().getFirst();
            EffectTransform transform = first.context().transform();
            slot.ownerId = first.ownerId();
            slot.operationId = batch.settings().operation().id().toString();
            slot.red = batch.mask().red();
            slot.green = batch.mask().green();
            slot.blue = batch.mask().blue();
            slot.strength = batch.mask().strength();
            slot.x = transform.position().x();
            slot.y = transform.position().y();
            slot.z = transform.position().z();
            slot.frameCount = batch.frames().size();
            slot.nearestDistance = batch.nearestCameraDistance();
        }
    }

    private void fillVisible(InspectFrame.EffectSlot slot, VisibleEffect visible) {
        EffectRenderContext context = visible.context();
        VisualEffectInstance effect = visible.effect();
        EffectTransform transform = context.transform();
        slot.instanceId = effect.instanceId();
        slot.effectId = effect.effectId();
        slot.visible = true;
        slot.segmented = effect.payload().segmentLink() != null;
        slot.depthCulled = this.frameSkippedByDepth.contains(effect.instanceId());
        slot.x = transform.position().x();
        slot.y = transform.position().y();
        slot.z = transform.position().z();
        slot.yaw = transform.yaw();
        slot.pitch = transform.pitch();
        slot.roll = transform.roll();
        slot.ageTicks = context.ageTicks();
        slot.progress = context.progress();
        slot.scale = context.scale();
        AABB bounds = visible.cullingBounds();
        slot.minX = bounds.minX;
        slot.minY = bounds.minY;
        slot.minZ = bounds.minZ;
        slot.maxX = bounds.maxX;
        slot.maxY = bounds.maxY;
        slot.maxZ = bounds.maxZ;
        slot.spatialRadius = visible.spatialRadius();
        slot.lodLevel = context.lod().level();
        slot.lodPreviousLevel = context.lod().previousLevel();
        slot.lodBlend = context.lod().blend();
        slot.cameraDistance = context.lod().cameraDistance();
        slot.gapInRadii = context.lod().gapInRadii();
        slot.quality = context.quality();
        slot.effectiveQuality = context.effectiveQuality();
        slot.coverage = context.screenMetrics().coverage();
        slot.centreDistance = context.screenMetrics().centreDistance();
        slot.score = context.screenMetrics().score();
        slot.projected = context.screenMetrics().projected();
        slot.passCount = countPasses(visible.renderer(), context);
        slot.instanceCount = countInstances(visible.renderer(), context);
        slot.particleCount = countParticles(visible.renderer(), context);
    }

    private void fillHidden(InspectFrame.EffectSlot slot, VisualEffectInstance effect, long gameTime) {
        EffectTransform transform = effect.interpolatedTransform(gameTime, 1.0F);
        slot.instanceId = effect.instanceId();
        slot.effectId = effect.effectId();
        slot.visible = false;
        slot.segmented = effect.payload().segmentLink() != null;
        slot.x = transform.position().x();
        slot.y = transform.position().y();
        slot.z = transform.position().z();
        slot.yaw = transform.yaw();
        slot.pitch = transform.pitch();
        slot.roll = transform.roll();
        slot.ageTicks = EffectTimeline.ageTicks(gameTime, effect.payload().startGameTime());
        slot.progress = EffectTimeline.progress(
            gameTime, 1.0F, effect.payload().startGameTime(), effect.payload().durationTicks());
        slot.scale = effect.payload().scale();
        double radius = this.instanceSpatialRadii.getOrDefault(
            effect.instanceId(), Double.POSITIVE_INFINITY);
        slot.spatialRadius = radius;
        double visibleRadius = Double.isFinite(radius) ? radius : 0.5;
        slot.minX = slot.x - visibleRadius;
        slot.minY = slot.y - visibleRadius;
        slot.minZ = slot.z - visibleRadius;
        slot.maxX = slot.x + visibleRadius;
        slot.maxY = slot.y + visibleRadius;
        slot.maxZ = slot.z + visibleRadius;
        slot.quality = ClientQualityController.publishedTier();
        slot.effectiveQuality = slot.quality;
        slot.lodLevel = 0;
        slot.lodPreviousLevel = 0;
    }

    private void captureDraws(InspectFrame frame) {
        for (InstancedBatchBuilder.InstancedDraw draw : this.instancedDraws) {
            InspectFrame.DrawSlot slot = frame.addDraw();
            if (slot == null) {
                break;
            }
            slot.passId = draw.key().id();
            slot.renderType = draw.key().renderType().toString();
            slot.mode = this.instancedRenderer.mode();
            slot.instanceCount = draw.instanceCount();
            slot.bytes = (int)Math.min(Integer.MAX_VALUE,
                InstanceBufferLayout.byteSize(draw.instanceCount()));
            slot.rowCount = Math.min(InspectFrame.MAX_ROWS, draw.instanceCount());
            for (int row = 0; row < slot.rowCount; row++) {
                writeRow(slot, row, draw.instance(row));
            }
        }
    }

    private void writeRow(InspectFrame.DrawSlot slot, int row, InstancedBatchBuilder.Instance instance) {
        int offset = row * InspectFrame.FLOATS_PER_ROW;
        instance.model().get(this.inspectMatrix);
        System.arraycopy(this.inspectMatrix, 0, slot.rows, offset, 16);
        slot.rows[offset + 16] = instance.red();
        slot.rows[offset + 17] = instance.green();
        slot.rows[offset + 18] = instance.blue();
        slot.rows[offset + 19] = instance.alpha();
        slot.rows[offset + 20] = instance.effectDataX();
        slot.rows[offset + 21] = instance.effectDataY();
        slot.rows[offset + 22] = instance.effectDataZ();
        slot.rows[offset + 23] = instance.effectDataW();
    }

    private static int countPasses(VisualEffectRenderer renderer, EffectRenderContext context) {
        if (renderer instanceof StaticMeshVisualEffectRenderer staticRenderer) {
            int staticPasses = staticRenderer.staticMeshPasses(context).size();
            if (renderer instanceof InstancedVisualEffectRenderer instanced) {
                return staticPasses + instanced.instancedPasses(context).size();
            }
            return staticPasses + renderer.renderPasses(context).size();
        }
        if (renderer instanceof InstancedVisualEffectRenderer instanced) {
            return instanced.instancedPasses(context).size();
        }
        if (renderer instanceof ParticleVisualEffectRenderer particles) {
            return renderer.renderPasses(context).size() + particles.particlePasses(context).size();
        }
        return renderer.renderPasses(context).size();
    }

    private static int countInstances(VisualEffectRenderer renderer, EffectRenderContext context) {
        if (renderer instanceof InstancedVisualEffectRenderer instanced) {
            return instanced.instancedPasses(context).size();
        }
        return 0;
    }

    private static int countParticles(VisualEffectRenderer renderer, EffectRenderContext context) {
        if (!(renderer instanceof ParticleVisualEffectRenderer particles)) {
            return 0;
        }
        int total = 0;
        for (EffectParticlePass pass : particles.particlePasses(context)) {
            total += pass.field().maxParticles();
        }
        return total;
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
            if (!(visible.renderer() instanceof ScreenEffectRenderer screenRenderer)
                || !this.shouldRenderDepth(visible)) {
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
            this.frameCamera = null;
            this.frameProjection = null;
            this.visibleEffects.clear();
            this.frameGroupOcclusionEnabled.clear();
            this.frameSkippedByDepth.clear();
            this.screenBatches = List.of();
            this.screenBatchTick = Integer.MIN_VALUE;
            return;
        }

        if (!this.frameCache.shouldBuild(event.getRenderTick())) {
            return;
        }
        this.frameCamera = captureCamera(event);
        this.frameProjection = this.frameCamera == null ? null : EffectScreenProjection.from(this.frameCamera);
        if (this.frameCamera == null) {
            this.visibleEffects.clear();
            this.frameGroupOcclusionEnabled.clear();
            this.frameSkippedByDepth.clear();
            this.screenBatches = List.of();
            this.screenBatchTick = Integer.MIN_VALUE;
            return;
        }
        this.beginAttachmentFrame();
        long gameTime = ClientEffectClock.estimate(level.getGameTime());
        this.removeFinishedEffects(level, gameTime);
        this.visibleEffects.clear();
        this.frameGroupOcclusionEnabled.clear();
        this.frameSkippedByDepth.clear();
        this.screenBatches = List.of();
        this.screenBatchTick = Integer.MIN_VALUE;
        if (this.activeEffects.isEmpty()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        this.forEachVisibleEffect(event, gameTime, partialTick, (effect, context, renderer,
            ownerId, cullingBounds, spatialRadius, occlusionBounds, depthOcclusionEnabled) ->
            this.visibleEffects.add(new VisibleEffect(effect, context, renderer, ownerId,
                cullingBounds, spatialRadius, occlusionBounds, depthOcclusionEnabled)));
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
        if (!this.coarseVisible(context, event, radius)) {
            return;
        }
        AABB cullingBounds = this.validatedCullingBounds(renderer, context);
        if (!event.getFrustum().isVisible(cullingBounds)) {
            return;
        }
        EffectScreenMetrics metrics = this.screenMetrics(context, radius);
        EffectQualityTier effective = this.effectiveQuality(instanceId, quality, metrics, gameTime, partialTick);
        context = context.withScreenMetrics(metrics).withEffectiveQuality(effective);
        EffectDepthOcclusion occlusion = renderer.depthOcclusion();
        AABB occlusionBounds = occlusion.enabled()
            ? (occlusion.bounds() == null ? cullingBounds : occlusion.bounds())
            : null;
        consumer.accept(effect, context.withLod(selectLod(instanceId, renderer, event, context, radius, effective,
            gameTime, partialTick)), renderer, instanceId, cullingBounds, radius, occlusionBounds,
            occlusion.enabled());
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
        if (!this.coarseVisible(context, event, member.spatialRadius())) {
            return;
        }
        AABB cullingBounds = this.validatedCullingBounds(renderer, context);
        if (!event.getFrustum().isVisible(cullingBounds)) {
            return;
        }
        EffectScreenMetrics metrics = this.screenMetrics(context, member.spatialRadius());
        EffectQualityTier effective = this.effectiveQuality(member.instanceId(), quality, metrics,
            gameTime, partialTick);
        context = context.withScreenMetrics(metrics).withEffectiveQuality(effective);
        UUID coordinatorId = member.link().coordinatorId();
        AABB groupBounds = group.isIndexable() ? group.indexBounds() : null;
        EffectDepthOcclusion occlusion = renderer.depthOcclusion();
        boolean occlusionEnabled = occlusion.enabled() && groupBounds != null;
        this.frameGroupOcclusionEnabled.merge(coordinatorId, occlusionEnabled,
            (current, next) -> current && next);
        consumer.accept(effect, context.withLod(selectLod(member.instanceId(), renderer, event, context,
            member.spatialRadius(), effective, gameTime, partialTick)), renderer, coordinatorId,
            cullingBounds, member.spatialRadius(), groupBounds, occlusionEnabled);
    }

    private EffectScreenMetrics screenMetrics(EffectRenderContext context, double spatialRadius) {
        EffectScreenProjection projection = this.frameProjection;
        if (projection == null) {
            return EffectScreenMetrics.UNKNOWN;
        }
        EffectPoint position = context.transform().position();
        return projection.project(position.x(), position.y(), position.z(), spatialRadius);
    }

    private EffectQualityTier effectiveQuality(UUID key, EffectQualityTier global,
        EffectScreenMetrics metrics, long gameTime, float partialTick) {
        boolean automatic = ClientQualityController.automatic();
        EffectQualityTier target = EffectQualityScoring.effectiveTier(global, metrics, automatic);
        if (!automatic || !metrics.projected()) {
            return target;
        }
        EffectQualityTracker.State state = this.qualityStates.computeIfAbsent(key,
            ignored -> new EffectQualityTracker.State());
        return EffectQualityTracker.update(state, target, gameTime + (double)partialTick);
    }

    /**
     * Selects the LOD for one visible instance or segment cell. Selection uses the interpolated transform, the
     * renderer's lifetime-conservative spatial radius, and the frame projection. A small per-instance state adds
     * threshold hysteresis and a transition blend.
     */
    private EffectLod selectLod(
        UUID key,
        VisualEffectRenderer renderer,
        net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        EffectRenderContext context,
        double spatialRadius,
        EffectQualityTier effectiveQuality,
        long gameTime,
        float partialTick
    ) {
        Vec3 cameraPosition = event.getCamera().getPosition();
        EffectPoint position = context.transform().position();
        EffectLodSelection.Raw raw = EffectLodSelection.measure(cameraPosition.x, cameraPosition.y,
            cameraPosition.z, position.x(), position.y(), position.z(), spatialRadius);
        double gap = raw.gapInRadii();
        if (gap != Double.MAX_VALUE) {
            gap *= effectiveQuality.lodDistanceMultiplier();
            if (!Double.isFinite(gap)) {
                gap = Double.MAX_VALUE;
            }
        }
        EffectLodTracker.State state = this.lodStates.computeIfAbsent(key,
            ignored -> new EffectLodTracker.State());
        double renderGameTime = gameTime + (double)partialTick;

        EffectScreenLodProfile screenProfile = renderer.screenLodProfile();
        EffectScreenMetrics metrics = context.screenMetrics();
        if (screenProfile != EffectScreenLodProfile.FULL && metrics.projected()) {
            double loss = EffectLodSelection.screenLoss(metrics, effectiveQuality);
            int target = screenProfile.selectLoss(loss);
            int current = state.level();
            if (current < 0) {
                current = target;
            }
            current = Math.min(current, screenProfile.levelCount() - 1);
            double currentBoundary = current < screenProfile.levelCount() - 1
                ? screenProfile.lossThreshold(current) : 0.0;
            double previousBoundary = current > 0 ? screenProfile.lossThreshold(current - 1) : 0.0;
            return EffectLodTracker.update(state, screenProfile.levelCount(), target, loss,
                currentBoundary, previousBoundary, raw.cameraDistance(), gap, renderGameTime);
        }

        EffectLodProfile profile = renderer.lodProfile();
        int target = profile.select(gap);
        int current = state.level();
        if (current < 0) {
            current = target;
        }
        current = Math.min(current, profile.levelCount() - 1);
        double currentBoundary = current < profile.levelCount() - 1 ? profile.threshold(current) : 0.0;
        double previousBoundary = current > 0 ? profile.threshold(current - 1) : 0.0;
        return EffectLodTracker.update(state, profile.levelCount(), target, gap,
            currentBoundary, previousBoundary, raw.cameraDistance(), gap, renderGameTime);
    }

    private boolean coarseVisible(
        EffectRenderContext context,
        net.neoforged.neoforge.client.event.RenderLevelStageEvent event,
        double spatialRadius
    ) {
        if (!Double.isFinite(spatialRadius) || spatialRadius < 0.0) {
            return true;
        }
        EffectPoint position = context.transform().position();
        AABB coarse = new AABB(
            position.x() - spatialRadius, position.y() - spatialRadius, position.z() - spatialRadius,
            position.x() + spatialRadius, position.y() + spatialRadius, position.z() + spatialRadius
        );
        return event.getFrustum().isVisible(coarse);
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
        this.qualityStates.remove(instanceId);
        this.attachmentResolver.forget(instanceId);
        this.removeIndexEntry(ClientEffectSpatialIndex.Key.instance(instanceId));

        SegmentLink link = removed.payload().segmentLink();
        if (link == null) {
            this.depthOcclusion.forget(instanceId);
            return;
        }
        this.depthOcclusion.forget(link.coordinatorId());
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
        this.depthOcclusion.forget(coordinatorId);
        if (group == null) {
            return;
        }
        for (UUID instanceId : group.memberIds()) {
            this.activeEffects.remove(instanceId);
            this.instanceSpatialRadii.remove(instanceId);
            this.lodStates.remove(instanceId);
            this.qualityStates.remove(instanceId);
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
            EffectLod.FULL, quality, EffectScreenMetrics.UNKNOWN, quality,
            this.frameEnvironment, this.frameCamera, daylight);
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

    private StaticMeshBatchBuilder staticMeshBatchBuilder(Vec3 cameraPosition) {
        int maxInstances = this.instancedRenderer.maxInstancesPerDraw();
        if (this.staticMeshBatchBuilder == null
            || this.staticMeshBatchBuilder.maxInstancesPerDraw() != maxInstances) {
            this.staticMeshBatchBuilder = new StaticMeshBatchBuilder(maxInstances);
        }
        this.staticMeshBatchBuilder.reset(cameraPosition);
        return this.staticMeshBatchBuilder;
    }

    private ParticleBatchBuilder particleBatchBuilder(Vec3 cameraPosition) {
        if (this.particleBatchBuilder == null) {
            this.particleBatchBuilder = new ParticleBatchBuilder();
        }
        this.particleBatchBuilder.reset(QuasarClientConfig.particleBudget(), cameraPosition.x,
            cameraPosition.y, cameraPosition.z);
        return this.particleBatchBuilder;
    }

    private void validateStaticPassIds(List<EffectStaticMeshPass> passes, VisibleEffect visible) {
        this.staticPassIds.clear();
        for (EffectStaticMeshPass pass : passes) {
            if (this.staticPassIds.contains(pass.id())) {
                throw new IllegalStateException("Duplicate static mesh pass ID '" + pass.id()
                    + "' for effect " + visible.effect().effectId());
            }
            this.staticPassIds.add(pass.id());
        }
    }

    private void invalidateFrame() {
        this.frameCache.invalidate();
        this.screenBatchTick = Integer.MIN_VALUE;
        this.visibleEffects.clear();
        this.frameGroupOcclusionEnabled.clear();
        this.frameSkippedByDepth.clear();
        this.screenFrames.clear();
        this.screenBatches = List.of();
        this.particleDraws.clear();
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
        this.particleRenderer.onResourceReload();
        this.depthOcclusion.reset();
        EffectRenderTypes.onResourceReload();
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
        this.frameProjection = null;
        ClientEffectClock.reset();
        this.activeEffects.clear();
        this.segmentedGroups.clear();
        this.instanceSpatialRadii.clear();
        this.lodStates.clear();
        this.qualityStates.clear();
        this.attachmentResolver.clear();
        this.frameEntities.clear();
        this.frameEntitiesBuilt = false;
        this.frameEntitiesGameTime = Long.MIN_VALUE;
        this.spatialIndex.clear();
        this.unindexedInstances.clear();
        this.unindexedGroups.clear();
        this.depthOcclusion.reset();
        this.instancedRenderer.close();
        this.particleRenderer.close();
        this.meshCache.close();
    }

    private record VisibleEffect(VisualEffectInstance effect, EffectRenderContext context,
        VisualEffectRenderer renderer, UUID ownerId, AABB cullingBounds, double spatialRadius,
        @Nullable AABB occlusionBounds, boolean depthOcclusionEnabled) {
        private VisibleEffect {
            Objects.requireNonNull(effect, "effect");
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(cullingBounds, "cullingBounds");
        }
    }

    @FunctionalInterface
    private interface VisibleEffectConsumer {
        @SuppressWarnings("checkstyle:ParameterNumber")
        void accept(VisualEffectInstance effect, EffectRenderContext context,
            VisualEffectRenderer renderer, UUID ownerId, AABB cullingBounds, double spatialRadius,
            @Nullable AABB occlusionBounds, boolean depthOcclusionEnabled);
    }
}
