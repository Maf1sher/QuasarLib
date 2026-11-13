package mafisher.quasar.api.client.render;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mojang.blaze3d.shaders.Uniform;
import mafisher.quasar.api.client.screen.ScreenEffectOperationContext;
import mafisher.quasar.api.effect.EffectCurve;
import mafisher.quasar.api.effect.EffectEasing;
import mafisher.quasar.api.effect.EffectParameterSet;
import mafisher.quasar.api.effect.EffectParameterState;
import net.minecraft.client.renderer.ShaderInstance;
import org.junit.jupiter.api.Test;

class EffectParameterBindingTest {
    @Test
    void bindsScaledFloatsAndVectors() {
        ShaderInstance shader = shader();
        Uniform radius = uniform(shader, "Radius");
        Uniform axis = uniform(shader, "Axis");
        EffectParameterState state = EffectParameterState.builder()
            .putFloat("radius", 1.0F)
            .build();

        binding().bind(shader, 0.5F, state);

        verify(radius).set(3.0F);
        verify(axis).set(1.0F, 0.5F, 0.0F);
    }

    @Test
    void cachesUniformHandlesForOneShaderAndRefreshesAfterReload() {
        ShaderInstance first = shader();
        Uniform firstRadius = uniform(first, "Radius");
        uniform(first, "Axis");
        ShaderInstance second = shader();
        Uniform secondRadius = uniform(second, "Radius");
        uniform(second, "Axis");
        EffectParameterBinding binding = binding();

        binding.bind(first, 0.0F);
        binding.bind(first, 0.5F);
        binding.bind(second, 0.5F);

        verify(first, times(1)).getUniform("Radius");
        verify(second, times(1)).getUniform("Radius");
        verify(firstRadius, times(2)).set(org.mockito.ArgumentMatchers.anyFloat());
        verify(secondRadius).set(org.mockito.ArgumentMatchers.anyFloat());
    }

    @Test
    void bindsThroughAScreenOperationContext() {
        ScreenEffectOperationContext context = mock(ScreenEffectOperationContext.class);
        ShaderInstance shader = shader();

        binding().bind(context, shader, 0.5F, null);

        verify(context).setUniform(shader, "Radius", 3.0F);
        verify(context).setUniform(shader, "Axis", 1.0F, 0.5F, 0.0F);
    }

    @Test
    void bindsColourValuesAsFourComponents() {
        EffectParameterSet set = EffectParameterSet.builder()
            .color("tint", 0.0F, 0.0F, 0.0F, 1.0F)
            .build();
        EffectParameterBinding binding = EffectParameterBinding.builder(set)
            .uniform("tint", "Tint")
            .build();
        ScreenEffectOperationContext context = mock(ScreenEffectOperationContext.class);
        ShaderInstance shader = shader();

        binding.bind(context, shader, 0.0F, null);

        verify(context).setUniform(shader, "Tint", 0.0F, 0.0F, 0.0F, 1.0F);
    }

    @Test
    void failsWhenAUniformIsMissing() {
        ShaderInstance shader = mock(ShaderInstance.class);
        when(shader.getName()).thenReturn("test:shader");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> binding().bind(shader, 0.5F));

        assertTrue(failure.getMessage().contains("Radius"));
        assertTrue(failure.getMessage().contains("test:shader"));
    }

    @Test
    void rejectsDuplicateBindingsAndInvalidScaleTargets() {
        EffectParameterSet set = set();

        assertThrows(IllegalArgumentException.class,
            () -> EffectParameterBinding.builder(set)
                .uniform("radius", "Radius")
                .uniform("radius", "Other"));
        assertThrows(IllegalArgumentException.class,
            () -> EffectParameterBinding.builder(set)
                .uniform("radius", "Radius")
                .uniform("axis", "Radius"));
        assertThrows(IllegalArgumentException.class,
            () -> EffectParameterBinding.builder(set)
                .uniform("axis", "Axis", 2.0F, 0.0F));
        assertThrows(IllegalStateException.class,
            () -> EffectParameterBinding.builder(set).build());
    }

    private static EffectParameterBinding binding() {
        return EffectParameterBinding.builder(set())
            .uniform("radius", "Radius", 2.0F, 1.0F)
            .uniform("axis", "Axis")
            .build();
    }

    private static EffectParameterSet set() {
        return EffectParameterSet.builder()
            .scalar("radius", 1.0F)
            .curve("axis", EffectCurve.vec3()
                .keyframe(0.0F, EffectCurve.Value.vec3(1.0F, 0.0F, 0.0F), EffectEasing.LINEAR)
                .keyframe(1.0F, EffectCurve.Value.vec3(1.0F, 1.0F, 0.0F), EffectEasing.LINEAR))
            .build();
    }

    private static ShaderInstance shader() {
        ShaderInstance shader = mock(ShaderInstance.class);
        when(shader.getName()).thenReturn("test:shader");
        return shader;
    }

    private static Uniform uniform(ShaderInstance shader, String name) {
        Uniform uniform = mock(Uniform.class);
        when(shader.getUniform(name)).thenReturn(uniform);
        return uniform;
    }
}
