package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectSpec;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.api.persistence.EffectDescriptor;
import mafisher.quasar.api.persistence.EffectKind;
import mafisher.quasar.api.persistence.EffectPersistence;
import mafisher.quasar.api.persistence.EffectPersistenceOptions;
import mafisher.quasar.effect.runtime.RecordingEffectStore;
import mafisher.quasar.effect.runtime.RecordingPacketSink;
import mafisher.quasar.effect.runtime.TestBudgetSource;
import mafisher.quasar.effect.runtime.TestEffectRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VisualEffectStartRestoreTest {
    private static final ResourceLocation EFFECT_ID =
        ResourceLocation.fromNamespaceAndPath("quasar_lib", "start_restore_test");
    private static final UUID INSTANCE_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000034");
    private static final long START_TIME = 40L;

    private ServerLevel level;
    private long gameTime;
    private RecordingEffectStore store;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
        this.gameTime = 50L;
        this.store = new RecordingEffectStore();
        TestEffectRuntime.install(
            this.level, this.store, new RecordingPacketSink(), new TestBudgetSource()
        );
    }

    @AfterEach
    void tearDown() {
        VisualEffectService.clear(this.level);
        TestEffectRuntime.uninstall(this.level);
    }

    @Test
    void startAndRestoreBuildTheSameStartPayload() {
        StartVisualEffectPayload started = payloadAfterStart();

        VisualEffectService.clear(this.level);
        when(this.level.players()).thenReturn(List.of());
        VisualEffectService.restore(this.level, spec(), INSTANCE_ID, START_TIME, descriptor());

        assertEquals(started, payloadForPlayer());
    }

    @Test
    void restoreDoesNotRecordANewStartDescriptor() {
        VisualEffectService.restore(this.level, spec(), INSTANCE_ID, START_TIME, descriptor());

        assertTrue(this.store.starts().isEmpty());
    }

    private StartVisualEffectPayload payloadAfterStart() {
        VisualEffectService.startWithId(
            this.level, spec(), INSTANCE_ID, START_TIME, EffectPersistenceOptions.DEFAULT
        );
        return payloadForPlayer();
    }

    private StartVisualEffectPayload payloadForPlayer() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(
            UUID.fromString("00000000-0000-0000-0000-000000000002")
        );
        when(player.level()).thenReturn(this.level);
        when(player.position()).thenReturn(new Vec3(1.0, 2.0, 3.0));
        when(this.level.players()).thenReturn(List.of(player));
        List<StartVisualEffectPayload> payloads =
            VisualEffectService.resumePayloads(this.level, player);
        assertEquals(1, payloads.size());
        return payloads.getFirst();
    }

    private static VisualEffectSpec spec() {
        return VisualEffectSpec.builder(EFFECT_ID, new Vec3(1.0, 2.0, 3.0), 200, 7L, EffectAudience.nearby(32.0))
            .rotation(10.0F, 20.0F, 30.0F)
            .scale(2.0F)
            .build();
    }

    private static EffectDescriptor descriptor() {
        return EffectDescriptor.builder(INSTANCE_ID, EffectKind.VISUAL, EFFECT_ID, Level.OVERWORLD.location())
            .placement(spec().transform(), spec().scale())
            .timing(spec().seed(), START_TIME, spec().durationTicks())
            .audience(spec().audience())
            .capabilities(EffectCapabilities.NONE)
            .persistence(EffectPersistence.RESUMABLE)
            .state(new CompoundTag())
            .build();
    }
}
