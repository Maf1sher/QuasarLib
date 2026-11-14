package mafisher.quasar.api.client.render;

import mafisher.quasar.client.effect.mesh.EffectMeshShapeBuilder;

/** Shared immutable local-space meshes used by built-in effect renderers. */
public final class EffectMeshes {
    private static final int SPHERE_LATITUDE_SEGMENTS = 32;
    private static final int SPHERE_LONGITUDE_SEGMENTS = 64;
    private static final int DEFAULT_RADIAL_SEGMENTS = 32;
    private static final int DEFAULT_HEIGHT_SEGMENTS = 1;
    private static final int DEFAULT_TORUS_MAJOR_SEGMENTS = 32;
    private static final int DEFAULT_TORUS_MINOR_SEGMENTS = 16;
    private static final int DEFAULT_SPHERE_SUBDIVISIONS = 16;
    private static final int DEFAULT_HEMISPHERE_SUBDIVISIONS = 16;
    private static final int DEFAULT_DISK_RADIAL_SEGMENTS = 4;
    private static final int DEFAULT_DISK_ANGULAR_SEGMENTS = 64;
    private static final EffectMesh HORIZONTAL_QUAD = createHorizontalQuad();
    private static final EffectMesh VERTICAL_PLANE = createVerticalPlane();
    private static final EffectMesh UNIT_SPHERE = createUnitSphere();
    private static final EffectMesh CYLINDER = EffectMeshShapeBuilder.cylinder(
        DEFAULT_RADIAL_SEGMENTS, DEFAULT_HEIGHT_SEGMENTS
    );
    private static final EffectMesh CONE = EffectMeshShapeBuilder.cone(DEFAULT_RADIAL_SEGMENTS);
    private static final EffectMesh TORUS = EffectMeshShapeBuilder.torus(
        DEFAULT_TORUS_MAJOR_SEGMENTS, DEFAULT_TORUS_MINOR_SEGMENTS
    );
    private static final EffectMesh HEMISPHERE = EffectMeshShapeBuilder.hemisphere(
        DEFAULT_HEMISPHERE_SUBDIVISIONS
    );
    private static final EffectMesh ICOSPHERE = EffectMeshShapeBuilder.icosphere(DEFAULT_SPHERE_SUBDIVISIONS);
    private static final EffectMesh DISK = EffectMeshShapeBuilder.disk(
        DEFAULT_DISK_RADIAL_SEGMENTS, DEFAULT_DISK_ANGULAR_SEGMENTS
    );

    private EffectMeshes() {
    }

    public static EffectMesh horizontalQuad() {
        return HORIZONTAL_QUAD;
    }

    public static EffectMesh verticalPlane() {
        return VERTICAL_PLANE;
    }

    /**
     * Returns the shared UV sphere. The lat-long topology has pole fans; use {@link #icosphere()} when a pole-free
     * surface is required.
     */
    public static EffectMesh unitSphere() {
        return UNIT_SPHERE;
    }

    /**
     * Builds a unit UV sphere with explicit tessellation. Callers should cache the result at construction time; vertex
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

    /** Returns the shared Y-up cylinder with radius 1, height 2, and both caps. */
    public static EffectMesh cylinder() {
        return CYLINDER;
    }

    /** Builds a Y-up cylinder with radius 1, height 2, and both caps. */
    public static EffectMesh cylinder(int radialSegments, int heightSegments) {
        return EffectMeshShapeBuilder.cylinder(radialSegments, heightSegments);
    }

    /** Returns the shared Y-up cone with base radius 1 at Y=-1, an apex at Y=1, and a base cap. */
    public static EffectMesh cone() {
        return CONE;
    }

    /** Builds a Y-up cone with base radius 1 at Y=-1, an apex at Y=1, and a base cap. */
    public static EffectMesh cone(int radialSegments) {
        return EffectMeshShapeBuilder.cone(radialSegments);
    }

    /** Returns the shared Y-up torus with outer radius 1 and tube radius 0.25. */
    public static EffectMesh torus() {
        return TORUS;
    }

    /** Builds a Y-up torus with outer radius 1 and tube radius 0.25. */
    public static EffectMesh torus(int majorSegments, int minorSegments) {
        return EffectMeshShapeBuilder.torus(majorSegments, minorSegments);
    }

    /** Returns the shared closed upper hemisphere with radius 1 and a flat base at Y=0. */
    public static EffectMesh hemisphere() {
        return HEMISPHERE;
    }

    /** Builds a closed upper hemisphere with radius 1 and a flat base at Y=0. */
    public static EffectMesh hemisphere(int subdivisions) {
        return EffectMeshShapeBuilder.hemisphere(subdivisions);
    }

    /** Returns the shared pole-free unit sphere. */
    public static EffectMesh icosphere() {
        return ICOSPHERE;
    }

    /**
     * Builds a pole-free unit sphere from a subdivided cube projected onto the sphere. Callers should cache the result
     * at construction time; vertex count is {@code 6 * subdivisions * subdivisions * 4}.
     */
    public static EffectMesh icosphere(int subdivisions) {
        return EffectMeshShapeBuilder.icosphere(subdivisions);
    }

    /** Returns the shared filled unit disk in the XZ plane at Y=0, facing +Y. */
    public static EffectMesh disk() {
        return DISK;
    }

    /** Builds a filled unit disk in the XZ plane at Y=0, facing +Y. */
    public static EffectMesh disk(int radialSegments, int angularSegments) {
        return EffectMeshShapeBuilder.disk(radialSegments, angularSegments);
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
