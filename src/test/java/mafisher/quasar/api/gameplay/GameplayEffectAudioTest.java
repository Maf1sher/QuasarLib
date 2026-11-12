package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.audio.EffectAudioAttenuation;
import mafisher.quasar.api.audio.EffectAudioCue;
import mafisher.quasar.api.audio.EffectAudioPlan;
import mafisher.quasar.api.audio.EffectAudioService;
import mafisher.quasar.api.audio.EffectAudioSpec;
import mafisher.quasar.api.audio.EffectAudioTrigger;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.EffectPoint;
import mafisher.quasar.api.effect.EffectTransform;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.GameplayEffectController;
import mafisher.quasar.api.gameplay.GameplayEffectSpec;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.effect.gameplay.GameplayEffectNbt;
import mafisher.quasar.effect.network.EffectAudioTriggerPayload;
import mafisher.quasar.effect.runtime.RecordingEffectStore;
import mafisher.quasar.effect.runtime.RecordingPacketSink;
import mafisher.quasar.effect.runtime.TestBudgetSource;
import mafisher.quasar.effect.runtime.TestEffectRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import mafisher.quasar.testing.GlobalStateTest;

@GlobalStateTest
class GameplayEffectAudioTest {
    private static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "audio_gameplay");
    private static final ResourceLocation SOUND = ResourceLocation.withDefaultNamespace("test");

    private ServerLevel level;
    private RecordingPacketSink packets;
    private long gameTime;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        this.packets = new RecordingPacketSink();
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
        TestEffectRuntime.install(
            this.level, new RecordingEffectStore(), this.packets, new TestBudgetSource()
        );
    }

    @AfterEach
    void tearDown() {
        GameplayEffectService.stopAll(this.level, GameplayEffectEndReason.CANCELLED);
        TestEffectRuntime.uninstall(this.level);
    }

    @Test
    void phaseCuesStartAtTheSessionStart() {
        GameplayEffectSpec spec = spec();
        GameplayEffectHandle handle = GameplayEffectService.start(this.level, spec, new TestController());

        assertEquals(1, EffectAudioService.activeCount(this.level));

        ServerPlayer player = player();
        when(this.level.players()).thenReturn(List.of(player));
        List<EffectAudioPlan> plans = EffectAudioService.resumePlans(this.level, player);

        assertEquals(1, plans.size());
        EffectAudioPlan plan = plans.getFirst();
        assertEquals(0L, plan.startGameTime());
        assertEquals(50, plan.durationTicks());
        assertEquals(20, plan.cues().getFirst().tick());

        GameplayEffectService.stop(this.level, handle, GameplayEffectEndReason.CANCELLED);
        assertEquals(0, EffectAudioService.activeCount(this.level));
    }

    @Test
    void triggersFiredDuringOnStartReachRecipients() {
        ServerPlayer player = player();
        when(this.level.players()).thenReturn(List.of(player));
        MinecraftServer server = mock(MinecraftServer.class);
        PlayerList playerList = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(playerList);
        when(playerList.getPlayer(player.getUUID())).thenReturn(player);
        when(this.level.getServer()).thenReturn(server);

        GameplayEffectHandle handle = GameplayEffectService.start(
            this.level, triggeredSpec(), new TriggerController()
        );

        assertTrue(this.packets.sent().stream()
            .anyMatch(sent -> sent.payload() instanceof EffectAudioTriggerPayload));
        GameplayEffectService.stop(this.level, handle, GameplayEffectEndReason.CANCELLED);
    }

    @Test
    void restoresAudioAtTheOriginalStartTime() {
        UUID sessionId = UUID.randomUUID();
        GameplayEffectSpec spec = spec();
        EffectDescriptor descriptor = EffectDescriptor.builder(sessionId, EffectKind.GAMEPLAY, TYPE_ID, ResourceLocation.withDefaultNamespace("overworld"))
            .placement(new EffectTransform(new EffectPoint(0.0, 0.0, 0.0), 0.0F, 0.0F, 0.0F), 1.0F)
            .timing(spec.seed(), 10L, 50)
            .audience(EffectAudience.dimensionWide())
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(GameplayEffectNbt.saveDescriptorState(spec, new CompoundTag()))
            .build();
        this.gameTime = 20L;

        GameplayEffectService.restore(this.level, spec, sessionId, 10L, descriptor, new TestController());

        assertEquals(1, EffectAudioService.activeCount(this.level));
        ServerPlayer player = player();
        when(this.level.players()).thenReturn(List.of(player));
        EffectAudioPlan plan = EffectAudioService.resumePlans(this.level, player).getFirst();
        assertEquals(10L, plan.startGameTime());
        assertEquals(20, plan.cues().getFirst().tick());
    }

    private ServerPlayer player() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.level()).thenReturn(this.level);
        when(player.position()).thenReturn(Vec3.ZERO);
        return player;
    }

    private static GameplayEffectSpec spec() {
        EffectPhasePlan phases = EffectPhasePlan.of(
            new EffectPhase("charge", 20),
            new EffectPhase("impact", 30)
        );
        EffectAudioSpec audio = EffectAudioSpec.builder(phases)
            .phaseCue("impact", 0, EffectAudioCue.at(0, Vec3.ZERO,
                SOUND,
                SoundSource.MASTER,
                1.0F,
                1.0F,
                EffectAudioAttenuation.distance(64.0F)))
            .build();
        return new GameplayEffectSpec(
            TYPE_ID,
            null,
            Vec3.ZERO,
            phases,
            7L,
            new EffectTickBudget(0, 0, 0),
            OwnerPolicy.CONTINUE,
            audio
        );
    }

    private static GameplayEffectSpec triggeredSpec() {
        EffectPhasePlan phases = EffectPhasePlan.of(
            new EffectPhase("charge", 20),
            new EffectPhase("impact", 30)
        );
        EffectAudioSpec audio = EffectAudioSpec.builder(phases)
            .trigger(EffectAudioTrigger.at("impact", Vec3.ZERO, SOUND, SoundSource.PLAYERS, 1.0F, 1.0F))
            .build();
        return new GameplayEffectSpec(
            TYPE_ID,
            null,
            Vec3.ZERO,
            phases,
            7L,
            new EffectTickBudget(0, 0, 0),
            OwnerPolicy.CONTINUE,
            audio
        );
    }

    private static class TestController implements GameplayEffectController {
        @Override
        public void onStart(GameplayEffectContext context) {
        }

        @Override
        public void onResume(GameplayEffectContext context) {
        }

        @Override
        public void onTick(GameplayEffectContext context) {
        }
    }

    private static final class TriggerController extends TestController {
        @Override
        public void onStart(GameplayEffectContext context) {
            context.triggerAudio("impact");
        }
    }
}
