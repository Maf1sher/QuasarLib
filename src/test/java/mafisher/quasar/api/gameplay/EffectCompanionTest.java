package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.TestConfig;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.effect.EffectAttachment;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectLifetimePolicy;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectStartStatus;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.budget.EffectPriority;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.effect.network.EffectTransformBatchPayload;
import mafisher.quasar.effect.runtime.LevelEffectRuntime;
import mafisher.quasar.effect.runtime.RecordingPacketSink;
import mafisher.quasar.effect.runtime.SavedDataEffectStore;
import mafisher.quasar.effect.runtime.TestBudgetSource;
import mafisher.quasar.testing.GameplayEffectFixtures;
import mafisher.quasar.testing.GlobalStateTest;
import mafisher.quasar.testing.TestServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@GlobalStateTest
class EffectCompanionTest {
    private static final ResourceLocation EFFECT =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "companion_test");
    private static final ResourceLocation SOUND =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "companion_sound");
    private static final UUID PLAYER_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000020");

    private TestServerLevel world;
    private RecordingPacketSink packets;
    private TestBudgetSource budgets;
    private LevelEffectRuntime runtime;

    @BeforeEach
    void setUp() {
        TestConfig.loadDefaults();
        this.world = new TestServerLevel();
        this.packets = new RecordingPacketSink();
        this.budgets = new TestBudgetSource();
        this.runtime = this.world.installRuntime(
            new SavedDataEffectStore(this.world.level()), this.packets, this.budgets
        );
    }

    @AfterEach
    void tearDown() {
        GameplayEffectService.stopAll(
            this.world.level(), GameplayEffectEndReason.CANCELLED
        );
        this.world.close();
        TestConfig.unload();
    }

    @Test
    void startsOneChildWithParentTimingTransformAndScale() {
        GameplayEffectSpec parent = parentSpec(
            new Vec3(10.0, 64.0, -2.0), 99L, 2.0F, 40
        );
        GameplayEffectFixtures.CompanionController controller =
            new GameplayEffectFixtures.CompanionController(companionSpec());

        GameplayEffectHandle handle = GameplayEffectService.start(
            this.world.level(), parent, controller
        );

        assertEquals(EffectCompanion.Status.ACTIVE, controller.companion.status());
        assertEquals(1, VisualEffectService.activeCount(this.world.level()));
        EffectDescriptor child = this.world.store().find(controller.companion.childId());
        assertNotNull(child);
        assertEquals(EffectKind.VISUAL, child.kind());
        assertEquals(EFFECT, child.typeId());
        assertEquals(handle.sessionId(), child.ownerSessionId());
        assertEquals(40, child.durationTicks());
        assertEquals(99L, child.seed());
        assertEquals(3.0F, child.scale(), 1.0E-6);
        assertEquals(0L, child.startGameTime());
        assertEquals(11.0, child.transform().position().x(), 1.0E-6);
        assertEquals(66.0, child.transform().position().y(), 1.0E-6);
        assertEquals(1.0, child.transform().position().z(), 1.0E-6);
        assertEquals(10.0F, child.transform().yaw(), 1.0E-6);
        assertEquals(20.0F, child.transform().pitch(), 1.0E-6);
        assertEquals(30.0F, child.transform().roll(), 1.0E-6);
    }

    @Test
    void duplicateStartKeepsOneChild() {
        GameplayEffectFixtures.CompanionController controller =
            new GameplayEffectFixtures.CompanionController(companionSpec(), true);

        GameplayEffectService.start(
            this.world.level(), parentSpec(new Vec3(0.0, 64.0, 0.0), 7L, 1.0F, 40), controller
        );

        assertEquals(1, VisualEffectService.activeCount(this.world.level()));
        assertNotNull(controller.duplicate);
        assertEquals(controller.companion.childId(), controller.duplicate.childId());
        assertEquals(EffectCompanion.Status.ACTIVE, controller.duplicate.status());
    }

    @Test
    void budgetRejectionLeavesTheSessionAliveWithoutRetrying() {
        this.budgets.standaloneVisuals(0);
        GameplayEffectFixtures.CompanionController controller =
            new GameplayEffectFixtures.CompanionController(companionSpec());

        GameplayEffectHandle handle = GameplayEffectService.start(
            this.world.level(), parentSpec(new Vec3(0.0, 64.0, 0.0), 7L, 1.0F, 40), controller
        );

        assertEquals(EffectCompanion.Status.UNAVAILABLE, controller.companion.status());
        assertEquals(EffectStartStatus.CULLED_ON_START, controller.companion.rejection());
        assertTrue(GameplayEffectService.isActive(this.world.level(), handle));
        assertEquals(0, VisualEffectService.activeCount(this.world.level()));

        this.world.setGameTime(1L);
        GameplayEffectService.tick(this.world.level());

        assertEquals(EffectCompanion.Status.UNAVAILABLE, controller.companion.status());
        assertEquals(0, VisualEffectService.activeCount(this.world.level()));
    }

    @Test
    void childAudioStartsAndStopsWithTheChild() {
        EffectAudioSpec audio = EffectAudioSpec.of(EffectAudioCue.at(
            1,
            new Vec3(0.0, 64.0, 0.0),
            SOUND,
            SoundSource.MASTER,
            1.0F,
            1.0F
        ));
        CompanionSpec spec = CompanionSpec.builder(EFFECT, EffectAudience.dimensionWide())
            .audio(audio)
            .build();
        GameplayEffectFixtures.CompanionController controller =
            new GameplayEffectFixtures.CompanionController(spec);

        GameplayEffectService.start(
            this.world.level(), parentSpec(new Vec3(0.0, 64.0, 0.0), 7L, 1.0F, 40), controller
        );

        assertNotNull(EffectAudioService.planOrNull(
            this.world.level(), controller.companion.childId()
        ));
        controller.companion.stop();

        assertNull(EffectAudioService.planOrNull(
            this.world.level(), controller.companion.childId()
        ));
        assertFalse(VisualEffectService.isActive(
            this.world.level(),
            new mafisher.quasar.api.effect.VisualEffectHandle(controller.companion.childId())
        ));
        assertEquals(EffectCompanion.Status.STOPPED, controller.companion.status());
    }

    @Test
    void finishNaturallyChildDoesNotFollowScopeCancellation() {
        CompanionSpec spec = CompanionSpec.builder(EFFECT, EffectAudience.dimensionWide())
            .lifetime(EffectLifetimePolicy.FINISH_NATURALLY)
            .build();
        GameplayEffectFixtures.CompanionController controller =
            new GameplayEffectFixtures.CompanionController(spec);
        GameplayEffectService.start(
            this.world.level(), parentSpec(new Vec3(0.0, 64.0, 0.0), 7L, 1.0F, 40), controller
        );
        UUID childId = controller.companion.childId();

        GameplayEffectService.stopAll(
            this.world.level(), GameplayEffectEndReason.CANCELLED
        );

        assertTrue(VisualEffectService.isActive(
            this.world.level(), new mafisher.quasar.api.effect.VisualEffectHandle(childId)
        ));
        VisualEffectService.stop(
            this.world.level(), new mafisher.quasar.api.effect.VisualEffectHandle(childId)
        );
    }

    @Test
    void staticParentSendsNoTransformAndAMovedParentSendsOne() {
        ServerPlayer player = player();
        MinecraftServer server = server(player);
        when(this.world.level().players()).thenReturn(List.of(player));
        when(this.world.level().getServer()).thenReturn(server);
        CompanionSpec spec = CompanionSpec.builder(EFFECT, EffectAudience.dimensionWide())
            .offset(0.0, 0.0, 1.0)
            .rotationInheritance(EffectAttachment.RotationInheritance.YAW_ONLY)
            .build();
        GameplayEffectFixtures.CompanionController controller =
            new GameplayEffectFixtures.CompanionController(spec);
        GameplayEffectService.start(
            this.world.level(), parentSpec(new Vec3(0.0, 64.0, 0.0), 7L, 1.0F, 40), controller
        );
        this.packets.clear();

        for (int tick = 0; tick < 5; tick++) {
            controller.companion.tick(controller.context);
            this.runtime.tick();
        }

        assertTrue(this.packets.sent().stream().noneMatch(
            packet -> packet.payload() instanceof EffectTransformBatchPayload
        ));

        EffectTransform parent = new EffectTransform(
            new EffectPoint(5.0, 64.0, 0.0), 90.0F, 0.0F, 0.0F
        );
        controller.companion.tick(controller.context, parent);
        this.runtime.tick();

        assertEquals(1, this.packets.sent().stream().filter(
            packet -> packet.payload() instanceof EffectTransformBatchPayload
        ).count());
        EffectDescriptor child = this.world.store().find(controller.companion.childId());
        assertNotNull(child);
        assertEquals(
            EffectAttachment.resolve(parent, spec.localTransform(), spec.rotationInheritance()),
            child.transform()
        );
    }

    private ServerPlayer player() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.level()).thenReturn(this.world.level());
        when(player.getUUID()).thenReturn(PLAYER_ID);
        when(player.position()).thenReturn(Vec3.ZERO);
        return player;
    }

    private static MinecraftServer server(ServerPlayer player) {
        MinecraftServer server = mock(MinecraftServer.class);
        PlayerList players = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayer(PLAYER_ID)).thenReturn(player);
        return server;
    }

    private static CompanionSpec companionSpec() {
        return CompanionSpec.builder(EFFECT, EffectAudience.dimensionWide())
            .offset(1.0, 2.0, 3.0)
            .rotation(10.0F, 20.0F, 30.0F)
            .scale(1.5F)
            .priority(EffectPriority.CRITICAL)
            .build();
    }

    private static GameplayEffectSpec parentSpec(
        Vec3 anchor,
        long seed,
        float scale,
        int durationTicks
    ) {
        return new GameplayEffectSpec(
            GameplayEffectFixtures.TYPE,
            null,
            anchor,
            EffectPhasePlan.of(new EffectPhase("run", durationTicks)),
            seed,
            EffectTickBudget.unlimited(),
            OwnerPolicy.CONTINUE,
            EffectAudioSpec.NONE,
            EffectPriority.HIGH,
            scale
        );
    }
}
