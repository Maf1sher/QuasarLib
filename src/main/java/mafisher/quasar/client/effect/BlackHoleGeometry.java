package mafisher.quasar.client.effect;

/** Black-hole-specific local mesh templates built on the shared effect mesh representation. */
final class BlackHoleGeometry {
    private static final int DISK_ANGULAR_SEGMENTS = 64;
    private static final int DISK_RADIAL_SEGMENTS = 6;
    private static final float DISK_RADIUS = 1.25F;
    private static final float PRIMARY_DISK_INNER_RADIUS = 0.42F;
    private static final EffectMesh SPHERE = EffectMeshes.unitSphere();
    private static final EffectMesh PRIMARY_DISK = createDiskLayer(
        PRIMARY_DISK_INNER_RADIUS, DISK_RADIUS, DISK_RADIUS,
        DISK_RADIUS * 0.035F, DISK_RADIUS * 0.035F, 1.0F);
    private static final EffectMesh SECONDARY_DISK = createDiskLayer(
        DISK_RADIUS * 0.54F, DISK_RADIUS * 0.94F, DISK_RADIUS,
        DISK_RADIUS * 0.06F, DISK_RADIUS * 0.055F, 0.44F);

    private BlackHoleGeometry() {
    }

    static EffectMesh sphere() {
        return SPHERE;
    }

    static EffectMesh primaryDisk() {
        return PRIMARY_DISK;
    }

    static EffectMesh secondaryDisk() {
        return SECONDARY_DISK;
    }

    private static EffectMesh createDiskLayer(float innerRadius, float outerRadius, float normalizationScale,
        float halfThickness, float warpAmplitude, float layerAlphaMultiplier) {
        int vertexCount = (DISK_RADIAL_SEGMENTS * DISK_ANGULAR_SEGMENTS * 2
            + DISK_ANGULAR_SEGMENTS * 2) * 4;
        EffectMesh.Builder mesh = EffectMesh.builder(vertexCount);
        for (int radial = 0; radial < DISK_RADIAL_SEGMENTS; radial++) {
            float radialStart = (float)radial / DISK_RADIAL_SEGMENTS;
            float radialEnd = (float)(radial + 1) / DISK_RADIAL_SEGMENTS;
            float radius0 = innerRadius + (outerRadius - innerRadius) * radialStart;
            float radius1 = innerRadius + (outerRadius - innerRadius) * radialEnd;
            for (int segment = 0; segment < DISK_ANGULAR_SEGMENTS; segment++) {
                double angle0 = Math.PI * 2.0 * segment / DISK_ANGULAR_SEGMENTS;
                double angle1 = Math.PI * 2.0 * (segment + 1) / DISK_ANGULAR_SEGMENTS;
                float warp00 = diskWarp(radius0, angle0, normalizationScale, warpAmplitude);
                float warp10 = diskWarp(radius1, angle0, normalizationScale, warpAmplitude);
                float warp11 = diskWarp(radius1, angle1, normalizationScale, warpAmplitude);
                float warp01 = diskWarp(radius0, angle1, normalizationScale, warpAmplitude);
                float thickness0 = halfThickness * (0.35F + 0.65F * radius0 / normalizationScale);
                float thickness1 = halfThickness * (0.35F + 0.65F * radius1 / normalizationScale);

                addQuad(mesh,
                    radius0, angle0, warp00 + thickness0,
                    radius1, angle0, warp10 + thickness1,
                    radius1, angle1, warp11 + thickness1,
                    radius0, angle1, warp01 + thickness0,
                    layerAlphaMultiplier);
                addQuad(mesh,
                    radius0, angle1, warp01 - thickness0,
                    radius1, angle1, warp11 - thickness1,
                    radius1, angle0, warp10 - thickness1,
                    radius0, angle0, warp00 - thickness0,
                    layerAlphaMultiplier * 0.72F);
                if (radial == DISK_RADIAL_SEGMENTS - 1) {
                    addQuad(mesh,
                        radius1, angle0, warp10 + thickness1,
                        radius1, angle0, warp10 - thickness1,
                        radius1, angle1, warp11 - thickness1,
                        radius1, angle1, warp11 + thickness1,
                        layerAlphaMultiplier * 0.85F);
                }
                if (radial == 0) {
                    addQuad(mesh,
                        radius0, angle1, warp01 + thickness0,
                        radius0, angle1, warp01 - thickness0,
                        radius0, angle0, warp00 - thickness0,
                        radius0, angle0, warp00 + thickness0,
                        layerAlphaMultiplier * 0.72F);
                }
            }
        }
        return mesh.build();
    }

    private static void addQuad(EffectMesh.Builder mesh,
        float radius0, double angle0, float z0,
        float radius1, double angle1, float z1,
        float radius2, double angle2, float z2,
        float radius3, double angle3, float z3,
        float alphaMultiplier) {
        addDiskVertex(mesh, radius0, angle0, z0, alphaMultiplier);
        addDiskVertex(mesh, radius1, angle1, z1, alphaMultiplier);
        addDiskVertex(mesh, radius2, angle2, z2, alphaMultiplier);
        addDiskVertex(mesh, radius3, angle3, z3, alphaMultiplier);
    }

    private static void addDiskVertex(EffectMesh.Builder mesh, float radius, double angle, float z,
        float alphaMultiplier) {
        float x = radius * (float)Math.cos(angle);
        float y = radius * (float)Math.sin(angle);
        mesh.vertex(x, y, z, 0.5F + x / (2.0F * DISK_RADIUS), 0.5F + y / (2.0F * DISK_RADIUS),
            0.0F, 0.0F, 1.0F, alphaMultiplier);
    }

    private static float diskWarp(float radius, double angle, float scale, float amplitude) {
        float normalizedRadius = radius / scale;
        float angularWave = (float)Math.sin(angle * 2.0 + normalizedRadius * 3.5);
        float corrugation = (float)Math.sin(angle * 6.0 - normalizedRadius * 18.0);
        return amplitude * (angularWave * (0.3F + normalizedRadius * 0.7F) + corrugation * 0.22F);
    }
}
