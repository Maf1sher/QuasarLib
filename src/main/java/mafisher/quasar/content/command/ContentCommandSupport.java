package mafisher.quasar.content.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec3;

/** Shared command geometry for content demos. */
final class ContentCommandSupport {
    private ContentCommandSupport() {
    }

    static Vec3 effectPosition(CommandSourceStack source) {
        Vec3 position = source.getPosition();
        return new Vec3(position.x, position.y - 1.4, position.z);
    }
}
