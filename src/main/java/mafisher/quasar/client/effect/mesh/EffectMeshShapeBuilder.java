package mafisher.quasar.client.effect.mesh;

import java.util.Arrays;
import java.util.Comparator;
import mafisher.quasar.api.client.render.EffectMesh;
import org.jetbrains.annotations.ApiStatus;

/**
 * Procedural unit-shape builders used by {@code EffectMeshes}. Every shape is generated as complete quads so it can
 * travel through the shared {@code BLOCK}/{@code QUADS} path. Spherical surfaces use a subdivided cube projected onto
 * a unit sphere; that topology has no pole singularity and no collapsed polar quads.
 */
@ApiStatus.Internal
public final class EffectMeshShapeBuilder {
    /** Maximum segments or subdivisions accepted by any shape factory. */
    public static final int MAX_SEGMENTS = 128;
    /** Maximum emitted vertices for one procedural shape. */
    public static final int MAX_VERTICES = 65_536;

    private static final double TWO_PI = Math.PI * 2.0;
    private static final float TORUS_MAJOR_RADIUS = 0.75F;
    private static final float TORUS_TUBE_RADIUS = 0.25F;
    private static final float CONE_NORMAL_SCALE = (float)(1.0 / Math.sqrt(1.25));
    private static final Axis[] SIDE_FACES = {
        new Axis(0.0F, 0.0F, 1.0F),
        new Axis(1.0F, 0.0F, 0.0F),
        new Axis(0.0F, 0.0F, -1.0F),
        new Axis(-1.0F, 0.0F, 0.0F)
    };
    private static final Face[] CUBE_FACES = {
        new Face(new Axis(1.0F, 0.0F, 0.0F), new Axis(0.0F, 0.0F, -1.0F), new Axis(0.0F, 1.0F, 0.0F)),
        new Face(new Axis(-1.0F, 0.0F, 0.0F), new Axis(0.0F, 0.0F, 1.0F), new Axis(0.0F, 1.0F, 0.0F)),
        new Face(new Axis(0.0F, 1.0F, 0.0F), new Axis(1.0F, 0.0F, 0.0F), new Axis(0.0F, 0.0F, -1.0F)),
        new Face(new Axis(0.0F, -1.0F, 0.0F), new Axis(1.0F, 0.0F, 0.0F), new Axis(0.0F, 0.0F, 1.0F)),
        new Face(new Axis(0.0F, 0.0F, 1.0F), new Axis(1.0F, 0.0F, 0.0F), new Axis(0.0F, 1.0F, 0.0F)),
        new Face(new Axis(0.0F, 0.0F, -1.0F), new Axis(-1.0F, 0.0F, 0.0F), new Axis(0.0F, 1.0F, 0.0F))
    };

    private EffectMeshShapeBuilder() {
    }

    /** Builds a Y-up cylinder with radius 1, height 2, and both caps. */
    public static EffectMesh cylinder(int radialSegments, int heightSegments) {
        validateSegments("Cylinder radial", radialSegments, 3);
        validateSegments("Cylinder height", heightSegments, 1);
        int quads = radialSegments * (heightSegments + 2);
        EffectMesh.Builder mesh = builder(quads);
        for (int height = 0; height < heightSegments; height++) {
            float y0 = -1.0F + 2.0F * height / heightSegments;
            float y1 = -1.0F + 2.0F * (height + 1) / heightSegments;
            float v0 = (float)height / heightSegments;
            float v1 = (float)(height + 1) / heightSegments;
            for (int segment = 0; segment < radialSegments; segment++) {
                float angle0 = (float)(TWO_PI * segment / radialSegments);
                float angle1 = (float)(TWO_PI * (segment + 1) / radialSegments);
                float cos0 = (float)Math.cos(angle0);
                float sin0 = (float)Math.sin(angle0);
                float cos1 = (float)Math.cos(angle1);
                float sin1 = (float)Math.sin(angle1);
                float u0 = (float)segment / radialSegments;
                float u1 = (float)(segment + 1) / radialSegments;
                mesh.vertex(cos0, y0, sin0, u0, v0, cos0, 0.0F, sin0);
                mesh.vertex(cos0, y1, sin0, u0, v1, cos0, 0.0F, sin0);
                mesh.vertex(cos1, y1, sin1, u1, v1, cos1, 0.0F, sin1);
                mesh.vertex(cos1, y0, sin1, u1, v0, cos1, 0.0F, sin1);
            }
        }
        float[][] ring = circleRing(radialSegments);
        addCap(mesh, ring, 1.0F, true);
        addCap(mesh, ring, -1.0F, false);
        return mesh.build();
    }

    /** Builds a Y-up cone with base radius 1 at Y=-1, an apex at Y=1, and a base cap. */
    public static EffectMesh cone(int radialSegments) {
        validateSegments("Cone radial", radialSegments, 3);
        EffectMesh.Builder mesh = builder(radialSegments * 2);
        for (int segment = 0; segment < radialSegments; segment++) {
            float angle0 = (float)(TWO_PI * segment / radialSegments);
            float angle1 = (float)(TWO_PI * (segment + 1) / radialSegments);
            float cos0 = (float)Math.cos(angle0);
            float sin0 = (float)Math.sin(angle0);
            float cos1 = (float)Math.cos(angle1);
            float sin1 = (float)Math.sin(angle1);
            float u0 = (float)segment / radialSegments;
            float u1 = (float)(segment + 1) / radialSegments;
            mesh.vertex(cos0, -1.0F, sin0, u0, 0.0F,
                cos0 * CONE_NORMAL_SCALE, 0.5F * CONE_NORMAL_SCALE, sin0 * CONE_NORMAL_SCALE);
            mesh.vertex(0.0F, 1.0F, 0.0F, u0, 1.0F,
                cos0 * CONE_NORMAL_SCALE, 0.5F * CONE_NORMAL_SCALE, sin0 * CONE_NORMAL_SCALE);
            mesh.vertex(0.0F, 1.0F, 0.0F, u1, 1.0F,
                cos1 * CONE_NORMAL_SCALE, 0.5F * CONE_NORMAL_SCALE, sin1 * CONE_NORMAL_SCALE);
            mesh.vertex(cos1, -1.0F, sin1, u1, 0.0F,
                cos1 * CONE_NORMAL_SCALE, 0.5F * CONE_NORMAL_SCALE, sin1 * CONE_NORMAL_SCALE);
        }
        addCap(mesh, circleRing(radialSegments), -1.0F, false);
        return mesh.build();
    }

    /** Builds a Y-up torus whose outer radius is 1 and whose tube radius is 0.25. */
    public static EffectMesh torus(int majorSegments, int minorSegments) {
        validateSegments("Torus major", majorSegments, 3);
        validateSegments("Torus minor", minorSegments, 3);
        EffectMesh.Builder mesh = builder(majorSegments * minorSegments);
        for (int major = 0; major < majorSegments; major++) {
            float u0 = (float)major / majorSegments;
            float u1 = (float)(major + 1) / majorSegments;
            float angle0 = (float)(TWO_PI * major / majorSegments);
            float angle1 = (float)(TWO_PI * (major + 1) / majorSegments);
            for (int minor = 0; minor < minorSegments; minor++) {
                float v0 = (float)minor / minorSegments;
                float v1 = (float)(minor + 1) / minorSegments;
                float tube0 = (float)(TWO_PI * minor / minorSegments);
                float tube1 = (float)(TWO_PI * (minor + 1) / minorSegments);
                addTorusVertex(mesh, angle0, tube0, u0, v0);
                addTorusVertex(mesh, angle0, tube1, u0, v1);
                addTorusVertex(mesh, angle1, tube1, u1, v1);
                addTorusVertex(mesh, angle1, tube0, u1, v0);
            }
        }
        return mesh.build();
    }

    /** Builds a filled, Y-up-facing disk in the XZ plane at Y=0 with radius 1. */
    public static EffectMesh disk(int radialSegments, int angularSegments) {
        validateSegments("Disk radial", radialSegments, 1);
        validateSegments("Disk angular", angularSegments, 3);
        EffectMesh.Builder mesh = builder(radialSegments * angularSegments);
        for (int ring = 0; ring < radialSegments; ring++) {
            float innerRadius = (float)ring / radialSegments;
            float outerRadius = (float)(ring + 1) / radialSegments;
            for (int segment = 0; segment < angularSegments; segment++) {
                float angle0 = (float)(TWO_PI * segment / angularSegments);
                float angle1 = (float)(TWO_PI * (segment + 1) / angularSegments);
                float cos0 = (float)Math.cos(angle0);
                float sin0 = (float)Math.sin(angle0);
                float cos1 = (float)Math.cos(angle1);
                float sin1 = (float)Math.sin(angle1);
                if (ring == 0) {
                    mesh.vertex(0.0F, 0.0F, 0.0F, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F);
                    mesh.vertex(outerRadius * cos1, 0.0F, outerRadius * sin1,
                        capU(outerRadius * cos1), capV(outerRadius * sin1), 0.0F, 1.0F, 0.0F);
                    mesh.vertex(outerRadius * cos0, 0.0F, outerRadius * sin0,
                        capU(outerRadius * cos0), capV(outerRadius * sin0), 0.0F, 1.0F, 0.0F);
                    mesh.vertex(0.0F, 0.0F, 0.0F, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F);
                } else {
                    mesh.vertex(innerRadius * cos0, 0.0F, innerRadius * sin0,
                        capU(innerRadius * cos0), capV(innerRadius * sin0), 0.0F, 1.0F, 0.0F);
                    mesh.vertex(innerRadius * cos1, 0.0F, innerRadius * sin1,
                        capU(innerRadius * cos1), capV(innerRadius * sin1), 0.0F, 1.0F, 0.0F);
                    mesh.vertex(outerRadius * cos1, 0.0F, outerRadius * sin1,
                        capU(outerRadius * cos1), capV(outerRadius * sin1), 0.0F, 1.0F, 0.0F);
                    mesh.vertex(outerRadius * cos0, 0.0F, outerRadius * sin0,
                        capU(outerRadius * cos0), capV(outerRadius * sin0), 0.0F, 1.0F, 0.0F);
                }
            }
        }
        return mesh.build();
    }

    /** Builds a pole-free unit sphere from a subdivided cube projected onto the sphere. */
    public static EffectMesh icosphere(int subdivisions) {
        validateSegments("Icosphere subdivisions", subdivisions, 1);
        EffectMesh.Builder mesh = builder(6 * subdivisions * subdivisions);
        for (Face face : CUBE_FACES) {
            addFace(mesh, face, subdivisions, 0, subdivisions);
        }
        return mesh.build();
    }

    /** Builds a closed upper hemisphere with radius 1, a pole-free dome, and a flat base at Y=0. */
    public static EffectMesh hemisphere(int subdivisions) {
        validateSegments("Hemisphere subdivisions", subdivisions, 2);
        if ((subdivisions & 1) != 0) {
            throw new IllegalArgumentException("Hemisphere subdivisions must be even");
        }
        int half = subdivisions / 2;
        EffectMesh.Builder mesh = builder(3 * subdivisions * subdivisions + 4 * subdivisions);
        addFace(mesh, CUBE_FACES[2], subdivisions, 0, subdivisions);
        for (int face = 0; face < SIDE_FACES.length; face++) {
            addFace(mesh, CUBE_FACES[facingFace(face)], subdivisions, half, subdivisions);
        }
        addCap(mesh, equatorRing(subdivisions), 0.0F, false);
        return mesh.build();
    }

    private static int facingFace(int side) {
        return switch (side) {
            case 0 -> 4;
            case 1 -> 0;
            case 2 -> 5;
            default -> 1;
        };
    }

    private static void addFace(EffectMesh.Builder mesh, Face face, int subdivisions, int startRow, int endRow) {
        for (int column = 0; column < subdivisions; column++) {
            float u0 = -1.0F + 2.0F * column / subdivisions;
            float u1 = -1.0F + 2.0F * (column + 1) / subdivisions;
            for (int row = startRow; row < endRow; row++) {
                float v0 = -1.0F + 2.0F * row / subdivisions;
                float v1 = -1.0F + 2.0F * (row + 1) / subdivisions;
                addSphereVertex(mesh, face, u0, v0);
                addSphereVertex(mesh, face, u1, v0);
                addSphereVertex(mesh, face, u1, v1);
                addSphereVertex(mesh, face, u0, v1);
            }
        }
    }

    private static void addSphereVertex(EffectMesh.Builder mesh, Face face, float u, float v) {
        float x = face.origin().x() + face.u().x() * u + face.v().x() * v;
        float y = face.origin().y() + face.u().y() * u + face.v().y() * v;
        float z = face.origin().z() + face.u().z() * u + face.v().z() * v;
        float length = (float)Math.sqrt(x * x + y * y + z * z);
        x /= length;
        y /= length;
        z /= length;
        float textureU = (float)(Math.atan2(z, x) / TWO_PI + 0.5);
        float textureV = (float)(Math.acos(Math.max(-1.0, Math.min(1.0, y))) / Math.PI);
        mesh.vertex(x, y, z, textureU, textureV, x, y, z);
    }

    private static float[][] equatorRing(int subdivisions) {
        float[][] ring = new float[4 * subdivisions][];
        int index = 0;
        for (Face face : new Face[] {CUBE_FACES[4], CUBE_FACES[0], CUBE_FACES[5], CUBE_FACES[1]}) {
            for (int segment = 0; segment < subdivisions; segment++) {
                float u = -1.0F + 2.0F * segment / subdivisions;
                float x = face.origin().x() + face.u().x() * u;
                float z = face.origin().z() + face.u().z() * u;
                float length = (float)Math.sqrt(x * x + z * z);
                ring[index++] = new float[] {x / length, z / length};
            }
        }
        Arrays.sort(ring, Comparator.comparingDouble(point -> Math.atan2(point[1], point[0])));
        return ring;
    }

    private static void addTorusVertex(EffectMesh.Builder mesh, float majorAngle, float minorAngle,
        float u, float v) {
        float cosMajor = (float)Math.cos(majorAngle);
        float sinMajor = (float)Math.sin(majorAngle);
        float cosMinor = (float)Math.cos(minorAngle);
        float sinMinor = (float)Math.sin(minorAngle);
        float radial = TORUS_MAJOR_RADIUS + TORUS_TUBE_RADIUS * cosMinor;
        mesh.vertex(radial * cosMajor, TORUS_TUBE_RADIUS * sinMinor, radial * sinMajor, u, v,
            cosMinor * cosMajor, sinMinor, cosMinor * sinMajor);
    }

    private static void addCap(EffectMesh.Builder mesh, float[][] ring, float y, boolean up) {
        float normalY = up ? 1.0F : -1.0F;
        for (int segment = 0; segment < ring.length; segment++) {
            float[] first = ring[segment];
            float[] second = ring[(segment + 1) % ring.length];
            mesh.vertex(0.0F, y, 0.0F, 0.5F, 0.5F, 0.0F, normalY, 0.0F);
            if (up) {
                mesh.vertex(second[0], y, second[1], capU(second[0]), capV(second[1]), 0.0F, normalY, 0.0F);
                mesh.vertex(first[0], y, first[1], capU(first[0]), capV(first[1]), 0.0F, normalY, 0.0F);
            } else {
                mesh.vertex(first[0], y, first[1], capU(first[0]), capV(first[1]), 0.0F, normalY, 0.0F);
                mesh.vertex(second[0], y, second[1], capU(second[0]), capV(second[1]), 0.0F, normalY, 0.0F);
            }
            mesh.vertex(0.0F, y, 0.0F, 0.5F, 0.5F, 0.0F, normalY, 0.0F);
        }
    }

    private static float[][] circleRing(int segments) {
        float[][] ring = new float[segments][];
        for (int segment = 0; segment < segments; segment++) {
            float angle = (float)(TWO_PI * segment / segments);
            ring[segment] = new float[] {(float)Math.cos(angle), (float)Math.sin(angle)};
        }
        return ring;
    }

    private static float capU(float x) {
        return 0.5F + x * 0.5F;
    }

    private static float capV(float z) {
        return 0.5F + z * 0.5F;
    }

    private static EffectMesh.Builder builder(int quads) {
        if (quads <= 0) {
            throw new IllegalArgumentException("Mesh quads must be positive");
        }
        long vertices = (long)quads * 4L;
        if (vertices > MAX_VERTICES) {
            throw new IllegalArgumentException("Mesh exceeds " + MAX_VERTICES + " vertices");
        }
        return EffectMesh.builder((int)vertices);
    }

    private static void validateSegments(String name, int value, int minimum) {
        if (value < minimum || value > MAX_SEGMENTS) {
            throw new IllegalArgumentException(
                name + " segments must be between " + minimum + " and " + MAX_SEGMENTS
            );
        }
    }

    private record Axis(float x, float y, float z) {
    }

    private record Face(Axis origin, Axis u, Axis v) {
    }
}
