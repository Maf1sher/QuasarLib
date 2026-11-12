package mafisher.quasar.api.scale;

import net.minecraft.world.level.levelgen.Heightmap;

/** Terrain heightmap used to conform segmented grid columns to the surface at coordinator start. */
public enum SegmentHeightSource {
    /** Keep the coordinator base Y; terrain sampling is disabled. */
    NONE(null),
    WORLD_SURFACE(Heightmap.Types.WORLD_SURFACE),
    OCEAN_FLOOR(Heightmap.Types.OCEAN_FLOOR),
    MOTION_BLOCKING(Heightmap.Types.MOTION_BLOCKING);

    private final Heightmap.Types heightmap;

    SegmentHeightSource(Heightmap.Types heightmap) {
        this.heightmap = heightmap;
    }

    /** Vanilla heightmap type, or {@code null} for {@link #NONE}. */
    public Heightmap.Types heightmap() {
        return this.heightmap;
    }

    /** Resolves a persisted source name, falling back to {@code fallback} for unknown values. */
    public static SegmentHeightSource byName(String name, SegmentHeightSource fallback) {
        if (name != null && !name.isEmpty()) {
            for (SegmentHeightSource source : values()) {
                if (source.name().equals(name)) {
                    return source;
                }
            }
        }
        return fallback;
    }
}
