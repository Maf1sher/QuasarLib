package mafisher.quasar.content;

import java.io.IOException;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Client-only registration of content-owned shaders. */
public final class ContentClientShaders {
    private ContentClientShaders() {
    }

    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        ContentShaders.RUNE_CIRCLE.register(event);
        ContentShaders.RUNE_MASK.register(event);
        ContentShaders.RUNE_GLOW.register(event);
        ContentShaders.BLACK_HOLE.register(event);
        ContentShaders.BLACK_HOLE_MASK.register(event);
        ContentShaders.BLACK_HOLE_MESH.register(event);
        ContentShaders.BLACK_HOLE_MESH_MASK.register(event);
    }
}
