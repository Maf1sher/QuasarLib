package mafisher.quasar.effect;

import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Guards effect runtime state against access from outside the server thread. */
public final class EffectThreading {
    private EffectThreading() {
    }

    /**
     * Rejects access from a thread other than the level's server thread. A missing server or
     * running thread is ignored so unit tests can mock a level without starting a server.
     */
    public static void requireServerThread(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        Thread runningThread = server.getRunningThread();
        if (runningThread != null && runningThread != Thread.currentThread()) {
            throw new IllegalStateException(
                "Effect state for " + level.dimension().location() + " was accessed from "
                    + Thread.currentThread().getName() + " instead of the server thread"
            );
        }
    }
}
