package mafisher.quasar.client.effect.mesh;

import org.jetbrains.annotations.ApiStatus;

/** Centripetal Catmull-Rom sampling and ring writes used by {@link EffectRibbonGeometry}. */
@ApiStatus.Internal
@SuppressWarnings("checkstyle:ParameterNumber")
final class EffectRibbonSpline {
    private EffectRibbonSpline() {
    }

    static int copyControls(float[] controls, int count, float[] rings, int stride) {
        for (int index = 0; index < count; index++) {
            writeRing(rings, stride, index, controls[index * 3], controls[index * 3 + 1],
                controls[index * 3 + 2]);
        }
        return count;
    }

    static int sampleControls(float[] controls, int count, int subdivisions, float[] rings,
        int stride) {
        int out = 0;
        for (int span = 0; span < count - 1; span++) {
            int first = Math.max(0, span - 1);
            int second = span;
            int third = span + 1;
            int fourth = Math.min(count - 1, span + 2);
            float p0x = controls[first * 3];
            float p0y = controls[first * 3 + 1];
            float p0z = controls[first * 3 + 2];
            float p1x = controls[second * 3];
            float p1y = controls[second * 3 + 1];
            float p1z = controls[second * 3 + 2];
            float p2x = controls[third * 3];
            float p2y = controls[third * 3 + 1];
            float p2z = controls[third * 3 + 2];
            float p3x = controls[fourth * 3];
            float p3y = controls[fourth * 3 + 1];
            float p3z = controls[fourth * 3 + 2];
            if (first == second) {
                p0x = 2.0F * p1x - p2x;
                p0y = 2.0F * p1y - p2y;
                p0z = 2.0F * p1z - p2z;
            }
            if (fourth == third) {
                p3x = 2.0F * p2x - p1x;
                p3y = 2.0F * p2y - p1y;
                p3z = 2.0F * p2z - p1z;
            }
            float t0 = 0.0F;
            float t1 = t0 + knot(p0x, p0y, p0z, p1x, p1y, p1z);
            float t2 = t1 + knot(p1x, p1y, p1z, p2x, p2y, p2z);
            float t3 = t2 + knot(p2x, p2y, p2z, p3x, p3y, p3z);
            for (int step = 0; step < subdivisions; step++) {
                float t = t1 + (t2 - t1) * step / subdivisions;
                writeRing(rings, stride, out++, catmullRom(p0x, p1x, p2x, p3x, t0, t1, t2, t3, t),
                    catmullRom(p0y, p1y, p2y, p3y, t0, t1, t2, t3, t),
                    catmullRom(p0z, p1z, p2z, p3z, t0, t1, t2, t3, t));
            }
        }
        int last = count - 1;
        writeRing(rings, stride, out++, controls[last * 3], controls[last * 3 + 1],
            controls[last * 3 + 2]);
        return out;
    }

    static void writeRing(float[] rings, int stride, int index, float x, float y, float z) {
        int offset = index * stride;
        rings[offset] = x;
        rings[offset + 1] = y;
        rings[offset + 2] = z;
        rings[offset + 3] = 0.0F;
        rings[offset + 4] = 0.0F;
    }

    static float knot(float fromX, float fromY, float fromZ, float toX, float toY, float toZ) {
        float distance = length(toX - fromX, toY - fromY, toZ - fromZ);
        return (float)Math.sqrt(Math.max(distance, 1.0E-4F));
    }

    static float catmullRom(float p0, float p1, float p2, float p3,
        float t0, float t1, float t2, float t3, float t) {
        float a1 = (t1 - t) / (t1 - t0) * p0 + (t - t0) / (t1 - t0) * p1;
        float a2 = (t2 - t) / (t2 - t1) * p1 + (t - t1) / (t2 - t1) * p2;
        float a3 = (t3 - t) / (t3 - t2) * p2 + (t - t2) / (t3 - t2) * p3;
        float b1 = (t2 - t) / (t2 - t0) * a1 + (t - t0) / (t2 - t0) * a2;
        float b2 = (t3 - t) / (t3 - t1) * a2 + (t - t1) / (t3 - t1) * a3;
        return (t2 - t) / (t2 - t1) * b1 + (t - t1) / (t2 - t1) * b2;
    }

    static float length(float x, float y, float z) {
        return (float)Math.sqrt(x * x + y * y + z * z);
    }
}
