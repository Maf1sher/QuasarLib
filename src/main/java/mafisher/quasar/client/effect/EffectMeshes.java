package mafisher.quasar.client.effect;

/** Shared immutable local-space meshes used by built-in effect renderers. */
final class EffectMeshes {
    private static final int SPHERE_LATITUDE_SEGMENTS = 32;
    private static final int SPHERE_LONGITUDE_SEGMENTS = 64;
    private static final EffectMesh HORIZONTAL_QUAD = createHorizontalQuad();
    private static final EffectMesh VERTICAL_PLANE = createVerticalPlane();
    private static final EffectMesh UNIT_SPHERE = createUnitSphere();

    private EffectMeshes() {
    }

    static EffectMesh horizontalQuad() {
        return HORIZONTAL_QUAD;
    }

    static EffectMesh verticalPlane() {
        return VERTICAL_PLANE;
    }

    static EffectMesh unitSphere() {
        return UNIT_SPHERE;
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
        int vertexCount = SPHERE_LATITUDE_SEGMENTS * SPHERE_LONGITUDE_SEGMENTS * 4;
        EffectMesh.Builder mesh = EffectMesh.builder(vertexCount);
        for (int latitude = 0; latitude < SPHERE_LATITUDE_SEGMENTS; latitude++) {
            float top = (float)latitude / SPHERE_LATITUDE_SEGMENTS;
            float bottom = (float)(latitude + 1) / SPHERE_LATITUDE_SEGMENTS;
            for (int longitude = 0; longitude < SPHERE_LONGITUDE_SEGMENTS; longitude++) {
                float left = (float)longitude / SPHERE_LONGITUDE_SEGMENTS;
                float right = (float)(longitude + 1) / SPHERE_LONGITUDE_SEGMENTS;
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
