package mafisher.quasar.client.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.client.EffectLod;
import mafisher.quasar.api.client.EffectRenderContext;
import mafisher.quasar.api.client.QuasarRenderTypes;
import mafisher.quasar.api.client.ScreenEffectMask;
import mafisher.quasar.api.client.ScreenEffectOperation;
import mafisher.quasar.api.client.ScreenEffectRenderer;
import mafisher.quasar.api.client.ScreenEffectSettings;
import mafisher.quasar.api.client.VisualEffectInstance;
import mafisher.quasar.api.effect.EffectQualityTier;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.scale.SegmentGridLayout;
import mafisher.quasar.api.scale.SegmentLink;
import mafisher.quasar.testing.TestVisualEffects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ScreenEffectBatchTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "test");
    private static final ResourceLocation DIMENSION = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final UUID COORDINATOR = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID OTHER_COORDINATOR = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final SegmentGridLayout LAYOUT = new SegmentGridLayout(2, 2, 4.0);
    private static final RenderType MASK_TYPE = QuasarRenderTypes.screenEffectMask(
        ResourceLocation.fromNamespaceAndPath("test", "textures/effect/mask.png"));
    private static final RenderType OTHER_MASK_TYPE = QuasarRenderTypes.screenEffectMask(
        ResourceLocation.fromNamespaceAndPath("test", "textures/effect/other_mask.png"));
    private static final ScreenEffectMask MASK = new ScreenEffectMask(0.2F, 0.4F, 0.6F, 0.8F);
    private static final ScreenEffectMask OTHER_MASK = new ScreenEffectMask(0.2F, 0.4F, 0.6F, 0.5F);
    private static final ScreenEffectSettings SETTINGS = ScreenEffectSettings.DIAGNOSTIC_PREVIEW;
    private static final ScreenEffectSettings OTHER_SETTINGS =
        ScreenEffectSettings.custom(ScreenEffectOperation.DIAGNOSTIC_MASK_PREVIEW, new Object());

    @Test
    void groupsCompatibleSegmentedCells() {
        ScreenEffectRenderer renderer = new StubRenderer();
        List<ScreenEffectBatch> batches = ScreenEffectBatch.group(List.of(
            segmentedFrame(renderer, COORDINATOR, 0, SETTINGS, MASK, MASK_TYPE),
            segmentedFrame(renderer, COORDINATOR, 1, SETTINGS, MASK, MASK_TYPE)));

        assertEquals(1, batches.size());
        assertEquals(2, batches.getFirst().frames().size());
        assertSame(MASK_TYPE, batches.getFirst().maskRenderType());
        assertSame(renderer, batches.getFirst().renderer());
    }

    @Test
    void keepsInterleavedCoordinatorsSeparate() {
        ScreenEffectRenderer renderer = new StubRenderer();
        List<ScreenEffectBatch> batches = ScreenEffectBatch.group(List.of(
            segmentedFrame(renderer, COORDINATOR, 0, SETTINGS, MASK, MASK_TYPE),
            segmentedFrame(renderer, OTHER_COORDINATOR, 0, SETTINGS, MASK, MASK_TYPE),
            segmentedFrame(renderer, COORDINATOR, 1, SETTINGS, MASK, MASK_TYPE)));

        assertEquals(2, batches.size());
        assertEquals(2, batches.get(0).frames().size());
        assertEquals(1, batches.get(1).frames().size());
        assertEquals(COORDINATOR, batches.get(0).frames().get(1).ownerId());
        assertEquals(OTHER_COORDINATOR, batches.get(1).frames().getFirst().ownerId());
    }

    @Test
    void keepsStandaloneInstancesSeparate() {
        ScreenEffectRenderer renderer = new StubRenderer();
        List<ScreenEffectBatch> batches = ScreenEffectBatch.group(List.of(
            standaloneFrame(renderer, UUID.randomUUID()),
            standaloneFrame(renderer, UUID.randomUUID())));

        assertEquals(2, batches.size());
        assertEquals(1, batches.get(0).frames().size());
        assertEquals(1, batches.get(1).frames().size());
    }

    @Test
    void splitsOnDifferentSettings() {
        ScreenEffectRenderer renderer = new StubRenderer();
        List<ScreenEffectBatch> batches = ScreenEffectBatch.group(List.of(
            segmentedFrame(renderer, COORDINATOR, 0, SETTINGS, MASK, MASK_TYPE),
            segmentedFrame(renderer, COORDINATOR, 1, OTHER_SETTINGS, MASK, MASK_TYPE)));

        assertEquals(2, batches.size());
    }

    @Test
    void splitsOnDifferentMask() {
        ScreenEffectRenderer renderer = new StubRenderer();
        List<ScreenEffectBatch> batches = ScreenEffectBatch.group(List.of(
            segmentedFrame(renderer, COORDINATOR, 0, SETTINGS, MASK, MASK_TYPE),
            segmentedFrame(renderer, COORDINATOR, 1, SETTINGS, OTHER_MASK, MASK_TYPE)));

        assertEquals(2, batches.size());
    }

    @Test
    void splitsOnDifferentMaskRenderType() {
        ScreenEffectRenderer renderer = new StubRenderer();
        List<ScreenEffectBatch> batches = ScreenEffectBatch.group(List.of(
            segmentedFrame(renderer, COORDINATOR, 0, SETTINGS, MASK, MASK_TYPE),
            segmentedFrame(renderer, COORDINATOR, 1, SETTINGS, MASK, OTHER_MASK_TYPE)));

        assertEquals(2, batches.size());
    }

    @Test
    void emptyInputProducesNoBatches() {
        assertTrue(ScreenEffectBatch.group(List.of()).isEmpty());
    }

    @Test
    void ordersBatchesFarthestFirst() {
        ScreenEffectRenderer renderer = new StubRenderer();
        UUID nearId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID farId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<ScreenEffectBatch> batches = ScreenEffectBatch.group(List.of(
            standaloneFrameAt(renderer, nearId, 32.0),
            standaloneFrameAt(renderer, farId, 128.0)));

        List<ScreenEffectBatch> ordered = ScreenEffectBatch.depthOrdered(batches);

        assertEquals(2, ordered.size());
        assertEquals(farId, ordered.get(0).frames().getFirst().ownerId());
        assertEquals(nearId, ordered.get(1).frames().getFirst().ownerId());
    }

    @Test
    void keepsEqualDistancesDeterministicByOwner() {
        ScreenEffectRenderer renderer = new StubRenderer();
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<ScreenEffectBatch> batches = ScreenEffectBatch.group(List.of(
            standaloneFrameAt(renderer, second, 64.0),
            standaloneFrameAt(renderer, first, 64.0)));

        List<ScreenEffectBatch> ordered = ScreenEffectBatch.depthOrdered(batches);

        assertEquals(first, ordered.get(0).frames().getFirst().ownerId());
        assertEquals(second, ordered.get(1).frames().getFirst().ownerId());
    }

    @Test
    void segmentedBatchIsOrderedByItsNearestCell() {
        ScreenEffectRenderer renderer = new StubRenderer();
        UUID nearCoordinator = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID farCoordinator = UUID.fromString("00000000-0000-0000-0000-000000000011");
        List<ScreenEffectBatch> batches = ScreenEffectBatch.group(List.of(
            segmentedFrameAt(renderer, nearCoordinator, 0, 200.0),
            segmentedFrameAt(renderer, nearCoordinator, 1, 20.0),
            segmentedFrameAt(renderer, farCoordinator, 0, 100.0)));

        List<ScreenEffectBatch> ordered = ScreenEffectBatch.depthOrdered(batches);

        assertEquals(farCoordinator, ordered.get(0).frames().getFirst().ownerId());
        assertEquals(nearCoordinator, ordered.get(1).frames().getFirst().ownerId());
    }

    private static ScreenEffectFrame segmentedFrame(ScreenEffectRenderer renderer, UUID coordinatorId,
        int segmentIndex, ScreenEffectSettings settings, ScreenEffectMask mask, RenderType maskRenderType) {
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            EFFECT_ID, UUID.randomUUID(), DIMENSION, segmentIndex, 64.0, 0.0, 0.0F, 0.0F, 0.0F, 1.0F, 100, 0L, 1L,
            new SegmentLink(coordinatorId, segmentIndex, LAYOUT));
        return new ScreenEffectFrame(coordinatorId, context(payload), renderer, settings, mask, maskRenderType);
    }

    private static ScreenEffectFrame standaloneFrame(ScreenEffectRenderer renderer, UUID instanceId) {
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            EFFECT_ID, instanceId, DIMENSION, 0.0, 64.0, 0.0, 0.0F, 0.0F, 0.0F, 1.0F, 100, 0L, 1L);
        return new ScreenEffectFrame(instanceId, context(payload), renderer, SETTINGS, MASK, MASK_TYPE);
    }

    private static ScreenEffectFrame standaloneFrameAt(ScreenEffectRenderer renderer, UUID instanceId,
        double cameraDistance) {
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            EFFECT_ID, instanceId, DIMENSION, 0.0, 64.0, 0.0, 0.0F, 0.0F, 0.0F, 1.0F, 100, 0L, 1L);
        return new ScreenEffectFrame(instanceId, context(payload, cameraDistance), renderer, SETTINGS, MASK,
            MASK_TYPE);
    }

    private static ScreenEffectFrame segmentedFrameAt(ScreenEffectRenderer renderer, UUID coordinatorId,
        int segmentIndex, double cameraDistance) {
        StartVisualEffectPayload payload = new StartVisualEffectPayload(
            EFFECT_ID, UUID.randomUUID(), DIMENSION, segmentIndex, 64.0, 0.0, 0.0F, 0.0F, 0.0F, 1.0F, 100, 0L, 1L,
            new SegmentLink(coordinatorId, segmentIndex, LAYOUT));
        return new ScreenEffectFrame(coordinatorId, context(payload, cameraDistance), renderer, SETTINGS, MASK,
            MASK_TYPE);
    }

    private static EffectRenderContext context(StartVisualEffectPayload payload) {
        return TestVisualEffects.context(payload, 10.0F, 0.5F);
    }

    private static EffectRenderContext context(StartVisualEffectPayload payload, double cameraDistance) {
        return new EffectRenderContext(
            new VisualEffectInstance(payload), payload.transform(), 10.0F, 0.5F, 0.0F,
            new EffectLod(0, cameraDistance, 0.0), EffectQualityTier.HIGH
        );
    }

    private static final class StubRenderer implements ScreenEffectRenderer {
        @Override
        public ScreenEffectMask screenEffectMask(EffectRenderContext context) {
            return MASK;
        }

        @Override
        public RenderType screenMaskRenderType(EffectRenderContext context) {
            return MASK_TYPE;
        }

        @Override
        public void renderScreenMask(EffectRenderContext context, RenderType maskRenderType, PoseStack poseStack,
            MultiBufferSource bufferSource) {
        }
    }
}
