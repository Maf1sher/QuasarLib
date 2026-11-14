package mafisher.quasar.client.effect.mesh;

import java.io.IOException;
import java.util.Objects;
import mafisher.quasar.api.client.render.EffectMesh;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mutable load state behind the public static-mesh facade. A failed reload leaves the
 * previous data untouched so active instances keep the last valid asset.
 */
@ApiStatus.Internal
public final class StaticMeshHandle {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticMeshHandle.class);

    private final ResourceLocation location;
    private final StaticMeshFormat format;
    private @Nullable StaticMeshData data;

    public StaticMeshHandle(ResourceLocation location) {
        this.location = Objects.requireNonNull(location, "location");
        this.format = StaticMeshFormat.from(location);
    }

    public ResourceLocation location() {
        return this.location;
    }

    public boolean loaded() {
        return this.data != null;
    }

    public @Nullable EffectMesh mesh() {
        return this.data == null ? null : this.data.mesh();
    }

    public @Nullable AABB localBounds() {
        return this.data == null ? null : this.data.localBounds();
    }

    /** Re-reads the asset and keeps the previous data when parsing or I/O fails. */
    public boolean reload(ResourceManager resources) {
        Objects.requireNonNull(resources, "resources");
        try {
            StaticMeshData loaded = StaticMeshLoader.load(resources, this.location, this.format);
            this.data = loaded;
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.warn(
                "Could not load effect static mesh {}; {}",
                this.location,
                this.data == null ? "no mesh is available" : "keeping the last valid mesh",
                exception
            );
            return false;
        }
    }
}
