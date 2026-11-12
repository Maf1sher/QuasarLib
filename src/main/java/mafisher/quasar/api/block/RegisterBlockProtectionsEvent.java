package mafisher.quasar.api.block;

import java.util.Objects;
import mafisher.quasar.effect.block.BlockProtectionService;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/**
 * Fired once during common setup so consumers can register per-change block protection callbacks.
 * Every registered callback must allow a change; duplicate IDs and late registration throw.
 *
 * <p>Subscribe on the game bus, for example with
 * {@code @EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)}.</p>
 */
public final class RegisterBlockProtectionsEvent extends Event {
    public void register(ResourceLocation id, BlockProtection protection) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(protection, "protection");
        BlockProtectionService.register(id, protection);
    }
}
