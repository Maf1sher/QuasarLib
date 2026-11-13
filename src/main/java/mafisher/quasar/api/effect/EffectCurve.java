package mafisher.quasar.api.effect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable, duration-independent typed keyframe curve.
 *
 * <p>Keyframe positions are normalized progress values in {@code [0, 1]}. Sampling below the
 * first keyframe or above the last keyframe clamps to that endpoint. The easing on a keyframe
 * applies to the segment that starts at it; the last keyframe's easing is unused. Values of
 * vector and colour curves interpolate component by component.</p>
 *
 * <p>Curves are authored once and sampled without allocation through
 * {@link #sampleFloat(float)} and {@link #sample(float, float[])}. {@link #sample(float)} is the
 * convenience form for tests and authoring code.</p>
 */
public final class EffectCurve {
    public static final int MAX_KEYFRAMES = 256;

    private final ValueType type;
    private final List<Keyframe> keyframes;

    private EffectCurve(ValueType type, List<Keyframe> keyframes) {
        this.type = Objects.requireNonNull(type, "type");
        if (keyframes.isEmpty() || keyframes.size() > MAX_KEYFRAMES) {
            throw new IllegalArgumentException(
                "An effect curve needs 1 to " + MAX_KEYFRAMES + " keyframes"
            );
        }
        List<Keyframe> sorted = new ArrayList<>(keyframes);
        sorted.sort(Comparator.comparingDouble(Keyframe::progress));
        for (Keyframe keyframe : sorted) {
            Objects.requireNonNull(keyframe, "keyframe");
            if (keyframe.value().type() != type) {
                throw new IllegalArgumentException("Effect curve keyframes must match the curve type");
            }
        }
        for (int index = 1; index < sorted.size(); index++) {
            if (Float.compare(sorted.get(index - 1).progress(), sorted.get(index).progress()) == 0) {
                throw new IllegalArgumentException("Effect curve keyframe positions must be unique");
            }
        }
        this.keyframes = List.copyOf(sorted);
    }

    public static Builder scalar() {
        return new Builder(ValueType.FLOAT);
    }

    public static Builder vec2() {
        return new Builder(ValueType.VEC2);
    }

    public static Builder vec3() {
        return new Builder(ValueType.VEC3);
    }

    public static Builder color() {
        return new Builder(ValueType.COLOR);
    }

    public ValueType type() {
        return this.type;
    }

    public List<Keyframe> keyframes() {
        return this.keyframes;
    }

    public float firstProgress() {
        return this.keyframes.getFirst().progress();
    }

    public float lastProgress() {
        return this.keyframes.getLast().progress();
    }

    /** Samples a scalar curve; throws when this curve has a different type. */
    public float sampleFloat(float progress) {
        requireType(ValueType.FLOAT);
        float value = clampProgress(progress);
        if (value <= firstProgress()) {
            return this.keyframes.getFirst().value().x();
        }
        if (value >= lastProgress()) {
            return this.keyframes.getLast().value().x();
        }
        int index = segmentIndex(value);
        Keyframe from = this.keyframes.get(index);
        Keyframe to = this.keyframes.get(index + 1);
        float eased = from.easing().apply(segmentProgress(from, to, value));
        return from.value().x() + (to.value().x() - from.value().x()) * eased;
    }

    /** Samples every component into {@code out}; the array must hold at least the curve width. */
    public void sample(float progress, float[] out) {
        Objects.requireNonNull(out, "out");
        if (out.length < this.type.components()) {
            throw new IllegalArgumentException(
                "Effect curve sampling buffer must hold at least " + this.type.components() + " values"
            );
        }
        float value = clampProgress(progress);
        if (value <= firstProgress()) {
            copyValue(this.keyframes.getFirst().value(), out);
            return;
        }
        if (value >= lastProgress()) {
            copyValue(this.keyframes.getLast().value(), out);
            return;
        }
        int index = segmentIndex(value);
        Keyframe from = this.keyframes.get(index);
        Keyframe to = this.keyframes.get(index + 1);
        float eased = from.easing().apply(segmentProgress(from, to, value));
        for (int component = 0; component < this.type.components(); component++) {
            float start = from.value().component(component);
            float end = to.value().component(component);
            out[component] = start + (end - start) * eased;
        }
    }

    /** Convenience allocation-bearing sample for tests and authoring; not for a render hot path. */
    public Value sample(float progress) {
        float[] components = new float[this.type.components()];
        sample(progress, components);
        return Value.of(this.type, components);
    }

    private int segmentIndex(float progress) {
        int low = 0;
        int high = this.keyframes.size() - 1;
        while (low + 1 < high) {
            int middle = (low + high) >>> 1;
            if (progress < this.keyframes.get(middle).progress()) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return low;
    }

    private static float segmentProgress(Keyframe from, Keyframe to, float progress) {
        float length = to.progress() - from.progress();
        return length <= 0.0F ? 1.0F : (progress - from.progress()) / length;
    }

    private static float clampProgress(float progress) {
        if (!Float.isFinite(progress)) {
            throw new IllegalArgumentException("Effect curve progress must be finite");
        }
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    private static void copyValue(Value value, float[] out) {
        for (int component = 0; component < value.type().components(); component++) {
            out[component] = value.component(component);
        }
    }

    private void requireType(ValueType expected) {
        if (this.type != expected) {
            throw new IllegalStateException(
                "Effect curve is " + this.type + " and cannot be sampled as " + expected
            );
        }
    }

    /** Width and interpolation semantics of one curve value. */
    public enum ValueType {
        FLOAT(1),
        VEC2(2),
        VEC3(3),
        COLOR(4);

        private final int components;

        ValueType(int components) {
            this.components = components;
        }

        public int components() {
            return this.components;
        }
    }

    /** Immutable typed value used by keyframes, parameter defaults, and delta state. */
    public record Value(ValueType type, float x, float y, float z, float w) {
        public Value {
            Objects.requireNonNull(type, "type");
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(w)) {
                throw new IllegalArgumentException("Effect curve values must be finite");
            }
            requireZero(y, type, 2);
            requireZero(z, type, 3);
            requireZero(w, type, 4);
        }

        public static Value scalar(float value) {
            return new Value(ValueType.FLOAT, value, 0.0F, 0.0F, 0.0F);
        }

        public static Value vec2(float x, float y) {
            return new Value(ValueType.VEC2, x, y, 0.0F, 0.0F);
        }

        public static Value vec3(float x, float y, float z) {
            return new Value(ValueType.VEC3, x, y, z, 0.0F);
        }

        public static Value color(float red, float green, float blue, float alpha) {
            return new Value(ValueType.COLOR, red, green, blue, alpha);
        }

        /** Builds a value from a typed component array; the array length must match the type. */
        public static Value of(ValueType type, float... components) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(components, "components");
            if (components.length != type.components()) {
                throw new IllegalArgumentException(
                    type + " values need " + type.components() + " components"
                );
            }
            return new Value(
                type,
                components[0],
                type.components() > 1 ? components[1] : 0.0F,
                type.components() > 2 ? components[2] : 0.0F,
                type.components() > 3 ? components[3] : 0.0F
            );
        }

        public int componentCount() {
            return this.type.components();
        }

        public float component(int index) {
            if (index < 0 || index >= this.type.components()) {
                throw new IllegalArgumentException("Effect value component index is out of range");
            }
            return switch (index) {
                case 0 -> this.x;
                case 1 -> this.y;
                case 2 -> this.z;
                default -> this.w;
            };
        }

        private static void requireZero(float value, ValueType type, int component) {
            if (type.components() < component && value != 0.0F) {
                throw new IllegalArgumentException(
                    "Unused components of a " + type + " value must be zero"
                );
            }
        }
    }

    /** One resolved keyframe. */
    public record Keyframe(float progress, Value value, EffectEasing easing) {
        public Keyframe {
            if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
                throw new IllegalArgumentException("Effect curve keyframe progress must be in [0, 1]");
            }
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(easing, "easing");
        }
    }

    /** Builder for one typed curve. Anchor-relative keyframes are resolved by {@link #build(Map)}. */
    public static final class Builder {
        private final ValueType type;
        private final List<PendingKeyframe> keyframes = new ArrayList<>();

        private Builder(ValueType type) {
            this.type = type;
        }

        public Builder keyframe(float progress, float value, EffectEasing easing) {
            return this.keyframe(progress, Value.scalar(value), easing);
        }

        public Builder keyframe(String anchor, float offset, float value, EffectEasing easing) {
            return this.keyframe(anchor, offset, Value.scalar(value), easing);
        }

        public Builder keyframe(float progress, Value value, EffectEasing easing) {
            if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
                throw new IllegalArgumentException("Effect curve keyframe progress must be in [0, 1]");
            }
            return this.add(new PendingKeyframe(progress, null, 0.0F, value, easing));
        }

        public Builder keyframe(String anchor, float offset, Value value, EffectEasing easing) {
            EffectPhaseAnchor.requireName(anchor);
            if (!Float.isFinite(offset)) {
                throw new IllegalArgumentException("Effect curve anchor offset must be finite");
            }
            return this.add(new PendingKeyframe(Float.NaN, anchor, offset, value, easing));
        }

        public boolean hasAnchors() {
            return this.keyframes.stream().anyMatch(keyframe -> keyframe.anchor() != null);
        }

        public EffectCurve build() {
            if (this.hasAnchors()) {
                throw new IllegalStateException("Effect curve has anchor-relative keyframes");
            }
            return this.build(Map.of());
        }

        /** Resolves anchor-relative keyframes and builds an immutable curve. */
        public EffectCurve build(Map<String, Float> anchors) {
            Objects.requireNonNull(anchors, "anchors");
            Map<String, Float> checkedAnchors = new LinkedHashMap<>();
            for (Map.Entry<String, Float> entry : anchors.entrySet()) {
                String name = EffectPhaseAnchor.requireName(entry.getKey());
                Float progress = Objects.requireNonNull(entry.getValue(), "anchor progress");
                if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
                    throw new IllegalArgumentException(
                        "Effect phase anchor progress must be finite and in [0, 1]"
                    );
                }
                checkedAnchors.put(name, progress);
            }

            List<Keyframe> resolved = new ArrayList<>(this.keyframes.size());
            for (PendingKeyframe pending : this.keyframes) {
                float progress = pending.anchor() == null
                    ? pending.progress()
                    : anchorProgress(checkedAnchors, pending.anchor()) + pending.offset();
                if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
                    throw new IllegalArgumentException(
                        "Effect curve keyframe resolved outside [0, 1]: " + progress
                    );
                }
                resolved.add(new Keyframe(progress, pending.value(), pending.easing()));
            }
            return new EffectCurve(this.type, resolved);
        }

        private Builder add(PendingKeyframe keyframe) {
            Objects.requireNonNull(keyframe.value(), "value");
            Objects.requireNonNull(keyframe.easing(), "easing");
            if (keyframe.value().type() != this.type) {
                throw new IllegalArgumentException(
                    "Effect curve is " + this.type + " but received a " + keyframe.value().type() + " value"
                );
            }
            if (this.keyframes.size() >= MAX_KEYFRAMES) {
                throw new IllegalArgumentException(
                    "An effect curve supports at most " + MAX_KEYFRAMES + " keyframes"
                );
            }
            this.keyframes.add(keyframe);
            return this;
        }

        private static float anchorProgress(Map<String, Float> anchors, String name) {
            Float progress = anchors.get(name);
            if (progress == null) {
                throw new IllegalArgumentException("Effect curve references an unknown anchor: " + name);
            }
            return progress;
        }
    }

    private record PendingKeyframe(
        float progress,
        @Nullable String anchor,
        float offset,
        Value value,
        EffectEasing easing
    ) {
    }
}
