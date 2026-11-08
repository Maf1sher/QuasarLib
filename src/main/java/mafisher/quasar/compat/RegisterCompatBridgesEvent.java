package mafisher.quasar.compat;

import java.util.Objects;
import net.neoforged.bus.api.Event;

/**
 * Fired once during common setup so consumers can register optional physics and
 * protection bridges. Registration closes after the event; duplicate bridges
 * throw.
 *
 * <p>Subscribe on the game bus, for example with
 * {@code @EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)}.</p>
 */
public final class RegisterCompatBridgesEvent extends Event {
    /** Registers the optional physics bridge. */
    public void registerPhysicsBridge(PhysicsBridge bridge) {
        CompatRegistry.registerPhysicsBridge(Objects.requireNonNull(bridge, "bridge"));
    }

    /** Registers the optional claim-mod protection bridge. */
    public void registerProtectionBridge(ProtectionBridge bridge) {
        CompatRegistry.registerProtectionBridge(Objects.requireNonNull(bridge, "bridge"));
    }
}
