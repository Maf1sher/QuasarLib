package mafisher.quasar.api.client.render;

import com.mojang.blaze3d.shaders.Uniform;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mafisher.quasar.api.client.screen.ScreenEffectOperationContext;
import mafisher.quasar.api.effect.EffectCurve;
import mafisher.quasar.api.effect.EffectParameterSet;
import mafisher.quasar.api.effect.EffectParameterState;
import net.minecraft.client.renderer.ShaderInstance;
import org.jspecify.annotations.Nullable;

/**
 * Type-checked binding from named effect parameters to shader uniforms.
 *
 * <p>The binding resolves parameter types once, rejects missing uniforms at bind time, and caches
 * the resolved {@link Uniform} handles for one shader instance. Missing a uniform is a shader
 * contract error, not a silent zero. The binding is client-only and not thread-safe.</p>
 */
public final class EffectParameterBinding {
    private final EffectParameterSet parameters;
    private final Spec[] specs;
    private final float[] scratch = new float[4];
    private ShaderInstance cachedShader;
    private Uniform[] cachedUniforms;

    private EffectParameterBinding(EffectParameterSet parameters, Spec[] specs) {
        this.parameters = parameters;
        this.specs = specs;
    }

    public static Builder builder(EffectParameterSet parameters) {
        return new Builder(parameters);
    }

    public EffectParameterSet parameters() {
        return this.parameters;
    }

    /** Binds every mapped parameter without an override state. */
    public void bind(ShaderInstance shader, float progress) {
        this.bind(shader, progress, null);
    }

    /** Binds every mapped parameter, preferring typed overrides from {@code state}. */
    public void bind(ShaderInstance shader, float progress, @Nullable EffectParameterState state) {
        Uniform[] uniforms = this.uniforms(shader);
        for (int index = 0; index < this.specs.length; index++) {
            bindUniform(uniforms[index], this.specs[index], progress, state);
        }
    }

    /** Binds through a screen-operation context, which provides its own validated uniform cache. */
    public void bind(
        ScreenEffectOperationContext context,
        ShaderInstance shader,
        float progress,
        @Nullable EffectParameterState state
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(shader, "shader");
        for (Spec spec : this.specs) {
            if (spec.type() == EffectCurve.ValueType.FLOAT) {
                float value = this.parameters.sampleFloat(spec.parameter(), progress, state);
                context.setUniform(shader, spec.uniform(), value * spec.scale() + spec.offset());
                continue;
            }
            this.parameters.sample(spec.parameter(), progress, state, this.scratch);
            switch (spec.type()) {
                case VEC2 -> context.setUniform(
                    shader, spec.uniform(), this.scratch[0], this.scratch[1]
                );
                case VEC3 -> context.setUniform(
                    shader, spec.uniform(), this.scratch[0], this.scratch[1], this.scratch[2]
                );
                case COLOR -> context.setUniform(
                    shader, spec.uniform(),
                    this.scratch[0], this.scratch[1], this.scratch[2], this.scratch[3]
                );
                case FLOAT -> throw new IllegalStateException("Float bindings are handled above");
            }
        }
    }

    private void bindUniform(
        Uniform uniform,
        Spec spec,
        float progress,
        @Nullable EffectParameterState state
    ) {
        if (spec.type() == EffectCurve.ValueType.FLOAT) {
            float value = this.parameters.sampleFloat(spec.parameter(), progress, state);
            uniform.set(value * spec.scale() + spec.offset());
            return;
        }
        this.parameters.sample(spec.parameter(), progress, state, this.scratch);
        switch (spec.type()) {
            case VEC2 -> uniform.set(this.scratch[0], this.scratch[1]);
            case VEC3 -> uniform.set(this.scratch[0], this.scratch[1], this.scratch[2]);
            case COLOR -> uniform.set(this.scratch[0], this.scratch[1], this.scratch[2], this.scratch[3]);
            case FLOAT -> throw new IllegalStateException("Float bindings are handled above");
        }
    }

    private Uniform[] uniforms(ShaderInstance shader) {
        Objects.requireNonNull(shader, "shader");
        if (shader == this.cachedShader) {
            return this.cachedUniforms;
        }
        Uniform[] uniforms = new Uniform[this.specs.length];
        for (int index = 0; index < this.specs.length; index++) {
            Uniform uniform = shader.getUniform(this.specs[index].uniform());
            if (uniform == null) {
                throw new IllegalStateException(
                    "Shader " + shader.getName() + " is missing effect parameter uniform '"
                        + this.specs[index].uniform() + "'"
                );
            }
            uniforms[index] = uniform;
        }
        this.cachedShader = shader;
        this.cachedUniforms = uniforms;
        return uniforms;
    }

    /** Builder that validates names and types against the parameter set. */
    public static final class Builder {
        private final EffectParameterSet parameters;
        private final List<Spec> specs = new ArrayList<>();
        private final Set<String> boundParameters = new LinkedHashSet<>();
        private final Set<String> boundUniforms = new LinkedHashSet<>();

        private Builder(EffectParameterSet parameters) {
            this.parameters = Objects.requireNonNull(parameters, "parameters");
        }

        /** Maps a parameter to the uniform with the same name. */
        public Builder uniform(String parameter) {
            return this.uniform(parameter, parameter);
        }

        public Builder uniform(String parameter, String uniform) {
            return this.uniform(parameter, uniform, 1.0F, 0.0F);
        }

        /**
         * Maps a float parameter to a uniform with a scale and offset.
         * Vector and colour parameters must be bound without a scale.
         */
        public Builder uniform(String parameter, String uniform, float scale, float offset) {
            String checkedParameter = Objects.requireNonNull(parameter, "parameter");
            String checkedUniform = Objects.requireNonNull(uniform, "uniform");
            EffectCurve.ValueType type = this.parameters.parameter(checkedParameter).type();
            if (checkedUniform.isBlank()) {
                throw new IllegalArgumentException("Effect uniform names cannot be blank");
            }
            if (!Float.isFinite(scale) || !Float.isFinite(offset)) {
                throw new IllegalArgumentException("Effect uniform scale and offset must be finite");
            }
            if (type != EffectCurve.ValueType.FLOAT && (scale != 1.0F || offset != 0.0F)) {
                throw new IllegalArgumentException(
                    "Scale and offset are supported only for float effect parameters"
                );
            }
            if (!this.boundParameters.add(checkedParameter)) {
                throw new IllegalArgumentException(
                    "Effect parameter is already bound: " + checkedParameter
                );
            }
            if (!this.boundUniforms.add(checkedUniform)) {
                throw new IllegalArgumentException(
                    "Shader uniform is bound more than once: " + checkedUniform
                );
            }
            this.specs.add(new Spec(checkedParameter, checkedUniform, type, scale, offset));
            return this;
        }

        public EffectParameterBinding build() {
            if (this.specs.isEmpty()) {
                throw new IllegalStateException("An effect parameter binding needs at least one uniform");
            }
            return new EffectParameterBinding(this.parameters, this.specs.toArray(Spec[]::new));
        }
    }

    private record Spec(
        String parameter,
        String uniform,
        EffectCurve.ValueType type,
        float scale,
        float offset
    ) {
    }
}
