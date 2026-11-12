package mafisher.quasar.api.client;

/** Shared immutable local-space meshes used by built-in effect renderers. */
public final class EffectMeshes {
    private static final int SPHERE_LATITUDE_SEGMENTS = 32;
    private static final int SPHERE_LONGITUDE_SEGMENTS = 64;
    private static final EffectMesh HORIZONTAL_QUAD = createHorizontalQuad();
    private static final EffectMesh VERTICAL_PLANE = createVerticalPlane();
    private static final EffectMesh UNIT_SPHERE = createUnitSphere();

    private EffectMeshes() {
    }

    public static EffectMesh horizontalQuad() {
        return HORIZONTAL_QUAD;
    }

    public static EffectMesh verticalPlane() {
        return VERTICAL_PLANE;
    }

    public static EffectMesh unitSphere() {
        return UNIT_SPHERE;
    }

    /**
     * Builds a unit sphere with explicit tessellation. Callers should cache the result at construction time; vertex
     * count is {@code latitudeSegments * longitudeSegments * 4}.
     */
    public static EffectMesh unitSphere(int latitudeSegments, int longitudeSegments) {
        if (latitudeSegments < 1 || longitudeSegments < 1) {
            throw new IllegalArgumentException("Sphere segments must be positive");
        }
        if ((long)latitudeSegments * longitudeSegments * 4 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Sphere tessellation is too large");
        }
        return createUnitSphere(latitudeSegments, longitudeSegments);
    }

    private static EffectMesh createHorizontalQuad() {
        return EffectMesh.builder(4)
            .vertex(-1.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F)
            .vertex(-1.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F)
            .vertex(1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 1.0F, 0.0F)
            .vertex(1.0F, 0.0F, -1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F)
            .build();
    }

    private static EffectMesh createVerticalPlane() {
        return EffectMesh.builder(4)
            .vertex(-0.5F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F)
            .vertex(0.5F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F)
            .vertex(0.5F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F)
            .vertex(-0.5F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F)
            .build();
    }

    private static EffectMesh createUnitSphere() {
        return createUnitSphere(SPHERE_LATITUDE_SEGMENTS, SPHERE_LONGITUDE_SEGMENTS);
    }

    private static EffectMesh createUnitSphere(int latitudeSegments, int longitudeSegments) {
        int vertexCount = latitudeSegments * longitudeSegments * 4;
        EffectMesh.Builder mesh = EffectMesh.builder(vertexCount);
        for (int latitude = 0; latitude < latitudeSegments; latitude++) {
            float top = (float)latitude / latitudeSegments;
            float bottom = (float)(latitude + 1) / latitudeSegments;
            for (int longitude = 0; longitude < longitudeSegments; longitude++) {
                float left = (float)longitude / longitudeSegments;
                float right = (float)(longitude + 1) / longitudeSegments;
                addSphereVertex(mesh, left, top);
                addSphereVertex(mesh, left, bottom);
                addSphereVertex(mesh, right, bottom);
                addSphereVertex(mesh, right, top);
            }
        }
        return mesh.build();
    }

    private static void addSphereVertex(EffectMesh.Builder mesh, float longitude, float latitude) {
        double theta = Math.PI * latitude;
        double phi = Math.PI * 2.0 * longitude;
        float sinTheta = (float)Math.sin(theta);
        float x = sinTheta * (float)Math.cos(phi);
        float y = (float)Math.cos(theta);
        float z = sinTheta * (float)Math.sin(phi);
        mesh.vertex(x, y, z, longitude, latitude, x, y, z);
    }
}
