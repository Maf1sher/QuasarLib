package mafisher.quasar.api.gameplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import mafisher.quasar.api.effect.EffectAudience;
import mafisher.quasar.api.effect.debris.EffectDebrisSpec;
import mafisher.quasar.api.effect.EffectScope;
import mafisher.quasar.api.gameplay.GameplayEffectContext;
import mafisher.quasar.api.gameplay.tick.EffectTickBudget;
import mafisher.quasar.api.persistence.EffectCapabilities;
import mafisher.quasar.effect.persistence.BlockJournal;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameplayDebrisTest {
    private ServerLevel level;
    private PlayerList players;
    private GameplayEffectContext context;

    @BeforeEach
    void setUp() {
        this.level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        this.players = mock(PlayerList.class);
        when(this.level.isClientSide()).thenReturn(false);
        when(this.level.dimension()).thenReturn(Level.OVERWORLD);
        when(this.level.getGameTime()).thenReturn(100L);
        when(this.level.getServer()).thenReturn(server);
        when(server.getPlayerList()).thenReturn(this.players);
        this.context = context(10);
    }

    @AfterEach
    void tearDown() {
        this.context.close();
    }

    @Test
    void degradesBurstsToTheRemainingBudget() {
        int sent = this.context.spawnDebris(spec(16));

        assertEquals(10, sent);
        assertEquals(10, this.context.counters().debrisPieces());
        verify(this.players).broadcast(
            any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any(Packet.class)
        );
    }

    @Test
    void zeroBudgetSendsNothing() {
        this.context.close();
        this.context = context(0);

        assertEquals(0, this.context.spawnDebris(spec(16)));
        assertEquals(0, this.context.counters().debrisPieces());
    }

    private GameplayEffectContext context(int maxDebrisPieces) {
        return new GameplayEffectContext(
            this.level,
            new EffectScope(this.level),
            new GameplayEffectSpec(
                ResourceLocation.fromNamespaceAndPath("quasar_lib", "debris_test"),
                UUID.fromString("00000000-0000-0000-0000-000000000006"),
                Vec3.ZERO,
                EffectPhasePlan.of(new EffectPhase("run", 100)),
                7L,
                new EffectTickBudget(0, 0, 0, 0, 0, 0, maxDebrisPieces),
                OwnerPolicy.CONTINUE
            ),
            UUID.randomUUID(),
            EffectCapabilities.NONE,
            new BlockJournal()
        );
    }

    private static EffectDebrisSpec spec(int pieces) {
        return new EffectDebrisSpec(
            Vec3.ZERO,
            Blocks.STONE.defaultBlockState(),
            pieces,
            new Vec3(0.0, 1.0, 0.0),
            0.5,
            1.0,
            0.08,
            40,
            EffectAudience.nearby(64.0)
        );
    }
}
