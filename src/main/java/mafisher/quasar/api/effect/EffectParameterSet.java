package mafisher.quasar.api.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import mafisher.quasar.api.audio.EffectPhaseTiming;
import org.jspecify.annotations.Nullable;

/**
 * Immutable definition of named, typed effect parameters.
 *
 * <p>A parameter is either sampled from an {@link EffectCurve} or kept at a constant default
 * until an {@link EffectParameterState} overrides it. Curves may place keyframes at named
 * {@link EffectPhaseAnchor} positions, which makes them duration-independent. Sampling is
 * allocation-free; the allocation-bearing {@link #sampleValue(String, float)} exists for tests
 * and authoring code.</p>
 */
public final class EffectParameterSet {
    public static final int MAX_PARAMETERS = 64;
    public static final int MAX_TOTAL_KEYFRAMES = 4096;

    private final List<Parameter> parameters;
    private final Map<String, Integer> indexByName;
    private final List<EffectPhaseAnchor> anchors;
    private final Map<String, Float> anchorByName;

    private EffectParameterSet(
        List<Parameter> parameters,
        Map<String, Integer> indexByName,
        List<EffectPhaseAnchor> anchors,
        Map<String, Float> anchorByName
    ) {
        this.parameters = parameters;
        this.indexByName = indexByName;
        this.anchors = anchors;
        this.anchorByName = anchorByName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Parameter> parameters() {
        return this.parameters;
    }

    public int size() {
        return this.parameters.size();
    }

    public boolean contains(String name) {
        return this.indexByName.containsKey(Objects.requireNonNull(name, "name"));
    }

    public Parameter parameter(String name) {
        Integer index = this.indexByName.get(Objects.requireNonNull(name, "name"));
        if (index == null) {
            throw new IllegalArgumentException("Unknown effect parameter: " + name);
        }
        return this.parameters.get(index);
    }

    public List<EffectPhaseAnchor> anchors() {
        return this.anchors;
    }

    /** Resolves a named anchor, failing when it is not declared. */
    public float anchor(String name) {
        Float progress = this.anchorByName.get(Objects.requireNonNull(name, "name"));
        if (progress == null) {
            throw new IllegalArgumentException("Unknown effect phase anchor: " + name);
        }
        return progress;
    }

    @Nullable
    public EffectPhaseAnchor anchorOrNull(String name) {
        Objects.requireNonNull(name, "name");
        for (EffectPhaseAnchor anchor : this.anchors) {
            if (anchor.name().equals(name)) {
                return anchor;
            }
        }
        return null;
    }

    /** Samples one scalar parameter without an override state. */
    public float sampleFloat(String name, float progress) {
        return sampleFloat(parameter(name), progress, null);
    }

    /** Samples one scalar parameter, preferring a matching typed override from {@code state}. */
    public float sampleFloat(String name, float progress, @Nullable EffectParameterState state) {
        return sampleFloat(parameter(name), progress, state);
    }

    /** Samples one parameter into {@code out} without an override state. */
    public void sample(String name, float progress, float[] out) {
        sample(parameter(name), progress, null, out);
    }

    /** Samples one parameter into {@code out}, preferring a matching typed override. */
    public void sample(
        String name,
        float progress,
        @Nullable EffectParameterState state,
        float[] out
    ) {
        sample(parameter(name), progress, state, out);
    }

    /** Allocation-bearing sample for tests and authoring; not for a render hot path. */
    public EffectCurve.Value sampleValue(String name, float progress) {
        return sampleValue(parameter(name), progress, null);
    }

    /** Allocation-bearing sample with an optional override state. */
    public EffectCurve.Value sampleValue(
        String name,
        float progress,
        @Nullable EffectParameterState state
    ) {
        return sampleValue(parameter(name), progress, state);
    }

    private float sampleFloat(Parameter parameter, float progress, @Nullable EffectParameterState state) {
        if (parameter.type() != EffectCurve.ValueType.FLOAT) {
            throw new IllegalStateException("Effect parameter " + parameter.name() + " is not a float");
        }
        EffectCurve.Value override = override(parameter, state);
        if (override != null) {
            return override.x();
        }
        return parameter.curve() == null
            ? parameter.defaultValue().x()
            : parameter.curve().sampleFloat(progress);
    }

    private void sample(
        Parameter parameter,
        float progress,
        @Nullable EffectParameterState state,
        float[] out
    ) {
        Objects.requireNonNull(out, "out");
        if (out.length < parameter.type().components()) {
            throw new IllegalArgumentException(
                "Effect parameter sampling buffer must hold at least "
                    + parameter.type().components() + " values"
            );
        }
        EffectCurve.Value override = override(parameter, state);
        if (override != null) {
            copy(override, out);
            return;
        }
        if (parameter.curve() == null) {
            copy(parameter.defaultValue(), out);
        } else {
            parameter.curve().sample(progress, out);
        }
    }

    private EffectCurve.Value sampleValue(
        Parameter parameter,
        float progress,
        @Nullable EffectParameterState state
    ) {
        EffectCurve.Value override = override(parameter, state);
        if (override != null) {
            return override;
        }
        return parameter.curve() == null
            ? parameter.defaultValue()
            : parameter.curve().sample(progress);
    }

    private static EffectCurve.Value override(
        Parameter parameter,
        @Nullable EffectParameterState state
    ) {
        if (state == null || state.isEmpty()) {
            return null;
        }
        EffectCurve.Value value = state.value(parameter.name());
        return value != null && value.type() == parameter.type() ? value : null;
    }

    private static void copy(EffectCurve.Value value, float[] out) {
        for (int component = 0; component < value.componentCount(); component++) {
            out[component] = value.component(component);
        }
    }

    /** One named parameter definition. A {@code null} curve means the default is used until overridden. */
    public record Parameter(
        String name,
        EffectCurve.ValueType type,
        @Nullable EffectCurve curve,
        EffectCurve.Value defaultValue
    ) {
        public Parameter {
            EffectPhaseAnchor.requireName(name);
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(defaultValue, "defaultValue");
            if (defaultValue.type() != type) {
                throw new IllegalArgumentException("Effect parameter default value must match its type");
            }
            if (curve != null && curve.type() != type) {
                throw new IllegalArgumentException("Effect parameter curve must match its type");
            }
        }
    }

    /** Builder that resolves anchor-relative curves when the set is built. */
    public static final class Builder {
        private final List<EffectPhaseAnchor> anchors = new ArrayList<>();
        private final Map<String, EffectPhaseAnchor> anchorsByName = new LinkedHashMap<>();
        private final List<PendingParameter> parameters = new ArrayList<>();
        private final Set<String> parameterNames = new LinkedHashSet<>();

        private Builder() {
        }

        public Builder anchor(String name, float progress) {
            return this.anchor(new EffectPhaseAnchor(name, progress));
        }

        public Builder anchor(EffectPhaseAnchor anchor) {
            Objects.requireNonNull(anchor, "anchor");
            if (this.anchorsByName.putIfAbsent(anchor.name(), anchor) != null) {
                throw new IllegalArgumentException("Duplicate effect phase anchor: " + anchor.name());
            }
            this.anchors.add(anchor);
            return this;
        }

        /** Declares one anchor per phase, using each phase start divided by total duration. */
        public Builder anchors(EffectPhaseTiming phases) {
            Objects.requireNonNull(phases, "phases");
            int total = phases.totalDurationTicks();
            if (total <= 0) {
                throw new IllegalArgumentException("Effect phase timing must have a positive duration");
            }
            for (int index = 0; index < phases.phaseCount(); index++) {
                this.anchor(phases.phaseName(index), phases.phaseStartTick(index) / (float)total);
            }
            return this;
        }

        public Builder scalar(String name, float defaultValue) {
            return this.parameter(name, EffectCurve.Value.scalar(defaultValue));
        }

        public Builder vec2(String name, float x, float y) {
            return this.parameter(name, EffectCurve.Value.vec2(x, y));
        }

        public Builder vec3(String name, float x, float y, float z) {
            return this.parameter(name, EffectCurve.Value.vec3(x, y, z));
        }

        public Builder color(String name, float red, float green, float blue, float alpha) {
            return this.parameter(name, EffectCurve.Value.color(red, green, blue, alpha));
        }

        public Builder parameter(String name, EffectCurve.Value defaultValue) {
            return this.add(new PendingParameter(name, null, null, defaultValue));
        }

        public Builder curve(String name, EffectCurve curve) {
            return this.add(new PendingParameter(name, null, Objects.requireNonNull(curve, "curve"), null));
        }

        public Builder curve(String name, EffectCurve.Builder curve) {
            return this.add(new PendingParameter(name, Objects.requireNonNull(curve, "curve"), null, null));
        }

        public EffectParameterSet build() {
            Map<String, Float> resolvedAnchors = new LinkedHashMap<>();
            for (EffectPhaseAnchor anchor : this.anchors) {
                resolvedAnchors.put(anchor.name(), anchor.progress());
            }

            List<Parameter> resolved = new ArrayList<>(this.parameters.size());
            Map<String, Integer> indexByName = new LinkedHashMap<>();
            int totalKeyframes = 0;
            for (PendingParameter pending : this.parameters) {
                EffectCurve curve = pending.builder() == null
                    ? pending.curve()
                    : pending.builder().build(resolvedAnchors);
                EffectCurve.Value defaultValue = pending.defaultValue() == null
                    ? curve.sample(0.0F)
                    : pending.defaultValue();
                EffectCurve.ValueType type = curve == null ? defaultValue.type() : curve.type();
                Parameter parameter = new Parameter(pending.name(), type, curve, defaultValue);
                resolved.add(parameter);
                indexByName.put(parameter.name(), resolved.size() - 1);
                if (curve != null) {
                    totalKeyframes += curve.keyframes().size();
                }
            }
            if (resolved.isEmpty()) {
                throw new IllegalStateException("An effect parameter set needs at least one parameter");
            }
            if (resolved.size() > MAX_PARAMETERS) {
                throw new IllegalStateException(
                    "An effect parameter set supports at most " + MAX_PARAMETERS + " parameters"
                );
            }
            if (totalKeyframes > MAX_TOTAL_KEYFRAMES) {
                throw new IllegalStateException(
                    "An effect parameter set supports at most "
                        + MAX_TOTAL_KEYFRAMES + " total keyframes"
                );
            }
            return new EffectParameterSet(
                List.copyOf(resolved),
                Collections.unmodifiableMap(indexByName),
                List.copyOf(this.anchors),
                Collections.unmodifiableMap(resolvedAnchors)
            );
        }

        private Builder add(PendingParameter parameter) {
            Objects.requireNonNull(parameter, "parameter");
            String name = EffectPhaseAnchor.requireName(parameter.name());
            if (!this.parameterNames.add(name)) {
                throw new IllegalArgumentException("Duplicate effect parameter: " + name);
            }
            if (this.parameters.size() >= MAX_PARAMETERS) {
                throw new IllegalStateException(
                    "An effect parameter set supports at most " + MAX_PARAMETERS + " parameters"
                );
            }
            this.parameters.add(parameter);
            return this;
        }
    }

    private record PendingParameter(
        String name,
        EffectCurve.@Nullable Builder builder,
        @Nullable EffectCurve curve,
        EffectCurve.@Nullable Value defaultValue
    ) {
    }
}
