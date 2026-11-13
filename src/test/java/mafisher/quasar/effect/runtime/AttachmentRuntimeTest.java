package mafisher.quasar.effect.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import mafisher.quasar.api.compat.CompatRegistry;
import mafisher.quasar.api.compat.CompatTestReset;
import mafisher.quasar.api.compat.SubLevelProvider;
import mafisher.quasar.api.effect.EffectAttachment;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectStartStatus;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.effect.VisualEffectHandle;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.effect.VisualEffectStartResult;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.effect.network.EffectTransformBatchPayload;
import mafisher.quasar.testing.GlobalStateTest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class AttachmentRuntimeTest {
    private static final ResourceLocation EFFECT_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "attachment_test");
    private static final UUID ENTITY_PARENT =
        UUID.fromString("00000000-0000-0000-0000-000000000010");

    private ServerLevel level;
    private ServerPlayer player;
    private Entity entityParent;
    private Entity currentParent;
    private RecordingPacketSink packets;
    private RecordingEffectStore store;
    private LevelEffectRuntime runtime;
    private long gameTime = 100L;
    private double parentX;

    @BeforeEach
    void setUp() {
        CompatTestReset.reset();
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.getGameTime()).thenAnswer(invocation -> this.gameTime);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getEntity(any(UUID.class))).thenAnswer(invocation -> this.currentParent);

        this.player = mock(ServerPlayer.class);
        when(this.player.level()).thenReturn(this.level);
        when(this.player.position()).thenReturn(Vec3.ZERO);
        when(this.player.getUUID()).thenReturn(
            UUID.fromString("00000000-0000-0000-0000-000000000020")
        );
        when(this.level.players()).thenReturn(List.of(this.player));

        this.entityParent = mock(Entity.class);
        when(this.entityParent.getUUID()).thenReturn(ENTITY_PARENT);
        when(this.entityParent.getX()).thenAnswer(invocation -> this.parentX);
        when(this.entityParent.getY()).thenReturn(64.0);
        when(this.entityParent.getZ()).thenReturn(0.0);
        when(this.entityParent.getYRot()).thenReturn(0.0F);
        when(this.entityParent.getXRot()).thenReturn(0.0F);
        this.currentParent = this.entityParent;

        this.packets = new RecordingPacketSink();
        this.store = new RecordingEffectStore();
        this.runtime = TestEffectRuntime.install(
            this.level, this.store, this.packets, new TestBudgetSource()
        );
    }

    @AfterEach
    void tearDown() {
        TestEffectRuntime.uninstall(this.level);
        CompatTestReset.reset();
    }

    @Test
    void attachmentStartsOnceAndFollowsAParentWithoutTransformPackets() {
        EffectAttachment attachment = EffectAttachment.toEntity(
            ENTITY_PARENT, new EffectPoint(0.0, 1.0, 0.0), 0.0F, 0.0F, 0.0F
        );
        VisualEffectStartResult result = this.start(this.spec(attachment, EffectAudience.nearby(500.0)));

        assertTrue(result.active());
        StartVisualEffectPayload start = this.soloStart();
        assertEquals(attachment, start.attachment());
        assertEquals(65.0, start.y());

        this.packets.clear();
        this.parentX = 100.0;
        this.gameTime++;
        this.runtime.visuals().tick();
        this.runtime.tick();

        assertTrue(this.packets.sent().isEmpty(),
            "moving an attached parent must not send transform updates");
        assertTrue(this.packets.sent().stream().noneMatch(packet ->
            packet.payload() instanceof EffectTransformBatchPayload));

        StartVisualEffectPayload resumed = this.runtime.visuals()
            .resumePayloads(this.player).getFirst();
        assertEquals(100.0, resumed.x(), 1.0E-6);
        assertEquals(65.0, resumed.y(), 1.0E-6);
    }

    @Test
    void missingParentRejectsANewAttachedStart() {
        this.currentParent = null;
        VisualEffectStartResult result = this.start(this.spec(
            EffectAttachment.toEntity(
                ENTITY_PARENT, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
            ),
            EffectAudience.dimensionWide()
        ));

        assertEquals(EffectStartStatus.REJECTED_PARENT, result.status());
        assertFalse(result.active());
        assertEquals(0, this.runtime.visuals().activeCount());
        assertTrue(this.packets.sent().isEmpty());
    }

    @Test
    void effectParentCyclesAreRejected() {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000031");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000032");
        this.runtime.visuals().startWithId(
            this.spec(null, EffectAudience.dimensionWide()),
            firstId,
            100L,
            mafisher.quasar.api.persistence.EffectPersistenceOptions.DEFAULT,
            null
        );
        VisualEffectStartResult second = this.runtime.visuals().startWithId(
            this.spec(EffectAttachment.toEffect(
                firstId, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
            ), EffectAudience.dimensionWide()),
            secondId,
            100L,
            mafisher.quasar.api.persistence.EffectPersistenceOptions.DEFAULT,
            null
        );
        assertTrue(second.active());

        VisualEffectStartResult cycle = this.runtime.visuals().startWithId(
            this.spec(EffectAttachment.toEffect(
                secondId, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
            ), EffectAudience.dimensionWide()),
            firstId,
            100L,
            mafisher.quasar.api.persistence.EffectPersistenceOptions.DEFAULT,
            null
        );

        assertEquals(EffectStartStatus.REJECTED_CYCLE, cycle.status());
        assertEquals(2, this.runtime.visuals().activeCount());
    }

    @Test
    void resumeUsesTheLatestStandaloneTransform() {
        VisualEffectStartResult result = this.start(this.spec(null, EffectAudience.dimensionWide()));
        this.runtime.visuals().update(
            result.handle(),
            new EffectTransform(new EffectPoint(42.0, 70.0, -3.0), 1.0F, 2.0F, 3.0F)
        );

        StartVisualEffectPayload resumed = this.runtime.visuals()
            .resumePayloads(this.player).getFirst();

        assertEquals(42.0, resumed.x(), 1.0E-6);
        assertEquals(70.0, resumed.y(), 1.0E-6);
        assertEquals(-3.0, resumed.z(), 1.0E-6);
    }

    @Test
    void attachedVisualsRejectWorldTransformUpdates() {
        VisualEffectStartResult result = this.start(this.spec(
            EffectAttachment.toEntity(
                ENTITY_PARENT, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
            ),
            EffectAudience.dimensionWide()
        ));

        assertThrows(IllegalStateException.class, () -> this.runtime.visuals().update(
            result.handle(),
            new EffectTransform(new EffectPoint(1.0, 2.0, 3.0), 0.0F, 0.0F, 0.0F)
        ));
    }

    @Test
    void stopWithParentStopsTheChild() {
        UUID parentId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        UUID childId = UUID.fromString("00000000-0000-0000-0000-000000000042");
        this.startWithId(parentId, null);
        this.startWithId(childId, EffectAttachment.toEffect(
            parentId, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
        ));

        this.runtime.visuals().stop(new VisualEffectHandle(parentId));

        assertEquals(0, this.runtime.visuals().activeCount());
        assertTrue(this.store.stops().contains(childId));
    }

    @Test
    void freezeKeepsTheChildAndPersistsItsLastFrame() {
        UUID parentId = UUID.fromString("00000000-0000-0000-0000-000000000051");
        UUID childId = UUID.fromString("00000000-0000-0000-0000-000000000052");
        this.startWithId(parentId, null);
        this.startWithId(childId, EffectAttachment.toEffect(
            parentId, new EffectPoint(0.0, 2.0, 0.0), 0.0F, 0.0F, 0.0F
        ).withParentLossPolicy(EffectAttachment.ParentLossPolicy.FREEZE_AT_LAST));

        this.runtime.visuals().tick();
        this.runtime.visuals().stop(new VisualEffectHandle(parentId));

        assertEquals(1, this.runtime.visuals().activeCount());
        EffectDescriptor lastUpdate = this.store.updates().stream()
            .filter(descriptor -> descriptor.instanceId().equals(childId))
            .reduce((first, second) -> second)
            .orElseThrow();
        assertEquals(66.0, lastUpdate.transform().position().y(), 1.0E-6);
    }

    @Test
    void missingEntityAppliesTheLossPolicyAfterTheGracePeriod() {
        VisualEffectStartResult result = this.start(this.spec(
            EffectAttachment.toEntity(
                ENTITY_PARENT, EffectPoint.ZERO, 0.0F, 0.0F, 0.0F
            ),
            EffectAudience.nearby(500.0)
        ));
        assertTrue(result.active());

        this.currentParent = null;
        this.gameTime += 19L;
        this.runtime.visuals().tick();
        assertEquals(1, this.runtime.visuals().activeCount());

        this.gameTime += 20L;
        this.runtime.visuals().tick();
        assertEquals(0, this.runtime.visuals().activeCount());
    }

    @Test
    void absentSubLevelProviderFallsBackToTheAuthoredTransform() {
        this.parentX = 3.0;
        VisualEffectStartResult result = this.start(this.spec(
            EffectAttachment.toSubLevel(
                UUID.fromString("00000000-0000-0000-0000-000000000061"),
                new EffectPoint(3.0, 4.0, 5.0),
                0.0F,
                0.0F,
                0.0F
            ),
            EffectAudience.dimensionWide()
        ));

        assertTrue(result.active());
        StartVisualEffectPayload payload = this.runtime.visuals()
            .resumePayloads(this.player).getFirst();
        assertEquals(3.0, payload.x(), 1.0E-6);
        assertEquals(64.0, payload.y(), 1.0E-6);
        assertEquals(0.0, payload.z(), 1.0E-6);
    }

    @Test
    void availableSubLevelProviderResolvesTheAttachmentPose() {
        UUID subLevelId = UUID.fromString("00000000-0000-0000-0000-000000000062");
        CompatTestReset.reset();
        CompatRegistry.registerSubLevelProvider(
            new SubLevelProvider() {
                @Override
                public ResourceLocation id() {
                    return ResourceLocation.fromNamespaceAndPath("test", "sub_level");
                }

                @Override
                public boolean available() {
                    return true;
                }

                @Override
                public Optional<EffectTransform> pose(UUID ignored) {
                    return Optional.of(new EffectTransform(
                        new EffectPoint(10.0, 20.0, 30.0), 0.0F, 0.0F, 0.0F
                    ));
                }
            }
        );

        VisualEffectStartResult result = this.start(this.spec(
            EffectAttachment.toSubLevel(
                subLevelId,
                new EffectPoint(1.0, 2.0, 3.0),
                0.0F,
                0.0F,
                0.0F
            ),
            EffectAudience.dimensionWide()
        ));

        assertTrue(result.active());
        StartVisualEffectPayload payload = this.runtime.visuals()
            .resumePayloads(this.player).getFirst();
        assertEquals(11.0, payload.x(), 1.0E-6);
        assertEquals(22.0, payload.y(), 1.0E-6);
        assertEquals(33.0, payload.z(), 1.0E-6);
    }

    private VisualEffectStartResult start(VisualEffectSpec spec) {
        return this.runtime.visuals().start(
            spec, null, mafisher.quasar.api.persistence.EffectPersistenceOptions.DEFAULT
        );
    }

    private void startWithId(UUID instanceId, EffectAttachment attachment) {
        this.runtime.visuals().startWithId(
            this.spec(attachment, EffectAudience.dimensionWide()),
            instanceId,
            100L,
            mafisher.quasar.api.persistence.EffectPersistenceOptions.DEFAULT,
            null
        );
    }

    private VisualEffectSpec spec(EffectAttachment attachment, EffectAudience audience) {
        return VisualEffectSpec.builder(
            EFFECT_ID,
            new Vec3(this.parentX, 64.0, 0.0),
            200,
            7L,
            audience
        ).attachment(attachment).build();
    }

    private StartVisualEffectPayload soloStart() {
        List<RecordingPacketSink.SentPacket> sent = this.packets.sent();
        assertEquals(1, sent.size());
        return assertInstanceOf(StartVisualEffectPayload.class, sent.getFirst().payload());
    }
}
