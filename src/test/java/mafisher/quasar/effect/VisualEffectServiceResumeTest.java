package mafisher.quasar.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.StartVisualEffectPayload;
import mafisher.quasar.api.effect.VisualEffectHandle;
import mafisher.quasar.api.effect.VisualEffectService;
import mafisher.quasar.api.effect.VisualEffectSpec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import mafisher.quasar.testing.GlobalStateTest;

@GlobalStateTest
class VisualEffectServiceResumeTest {
    private static final ResourceLocation EFFECT_ID = ResourceLocation.fromNamespaceAndPath("quasar_lib", "resume_test");

    private ServerLevel level;
    private long gameTime;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.players()).thenReturn(List.of());
        when(this.level.getGameTime()).thenAnswer(ignored -> this.gameTime);
    }

    @AfterEach
    void tearDown() {
        VisualEffectService.clear(this.level);
    }

    @Test
    void resumeMarksThePlayerAsTrackingSoTheNextTickDoesNotResend() {
        VisualEffectHandle handle = VisualEffectService.start(this.level, spec(EffectAudience.dimensionWide())).handle();
        ServerPlayer player = player(new Vec3(0.0, 64.0, 0.0));
        when(this.level.players()).thenReturn(List.of(player));
        this.gameTime = 50L;

        List<StartVisualEffectPayload> payloads = VisualEffectService.resumePayloads(this.level, player);

        assertEquals(1, payloads.size());
        assertEquals(50L, payloads.getFirst().serverGameTime());
        assertEquals(List.of(player.getUUID()), VisualEffectService.trackingPlayers(this.level, handle));
    }

    @Test
    void resumeUntracksPlayersOutsideTheAudience() {
        VisualEffectHandle handle = VisualEffectService.start(
            this.level,
            spec(EffectAudience.nearby(16.0))
        ).handle();
        ServerPlayer player = player(new Vec3(4.0, 64.0, 0.0));
        when(this.level.players()).thenReturn(List.of(player));

        assertEquals(1, VisualEffectService.resumePayloads(this.level, player).size());
        assertEquals(List.of(player.getUUID()), VisualEffectService.trackingPlayers(this.level, handle));

        when(player.position()).thenReturn(new Vec3(100.0, 64.0, 0.0));
        when(player.getX()).thenReturn(100.0);
        when(player.getY()).thenReturn(64.0);
        when(player.getZ()).thenReturn(0.0);

        assertTrue(VisualEffectService.resumePayloads(this.level, player).isEmpty());
        assertTrue(VisualEffectService.trackingPlayers(this.level, handle).isEmpty());
    }

    private static VisualEffectSpec spec(EffectAudience audience) {
        return VisualEffectSpec.builder(EFFECT_ID, new Vec3(0.0, 64.0, 0.0), 200, 3L, audience)
            .rotation(0.0F, 0.0F, 0.0F)
            .scale(1.0F)
            .build();
    }

    private ServerPlayer player(Vec3 position) {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getUUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        when(player.level()).thenReturn(this.level);
        when(player.position()).thenReturn(position);
        when(player.getX()).thenReturn(position.x);
        when(player.getY()).thenReturn(position.y);
        when(player.getZ()).thenReturn(position.z);
        return player;
    }
}
