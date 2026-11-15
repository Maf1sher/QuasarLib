package mafisher.quasar.client.effect.inspect;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.ApiStatus;

/** Shared colors and the depth-tested line render type used by inspector gizmos. */
@ApiStatus.Internal
public final class InspectStyle {
    public static final Color BOUNDS = new Color(0.25F, 0.95F, 0.40F, 0.85F);
    public static final Color RADIUS = new Color(0.20F, 0.80F, 1.00F, 0.75F);
    public static final Color AUDIO = new Color(1.00F, 0.85F, 0.20F, 0.85F);
    public static final Color AUDIO_PAST = new Color(1.00F, 0.85F, 0.20F, 0.30F);
    public static final Color SCREEN = new Color(1.00F, 0.35F, 0.90F, 0.90F);
    public static final Color HITBOX_DAMAGE = new Color(1.00F, 0.25F, 0.25F, 0.90F);
    public static final Color HITBOX_REMOVE = new Color(1.00F, 0.55F, 0.10F, 0.90F);
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFFA0A0A0;
    public static final int TEXT_WARNING = 0xFFFF6060;
    public static final int PANEL = 0x90000000;

    private static final RenderType LINES = RenderType.create(
        "quasar_lib_inspect_lines",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES,
        1_536,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.0)))
            .setLayeringState(RenderStateShard.NO_LAYERING)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setCullState(RenderStateShard.NO_CULL)
            .setOutputState(RenderStateShard.MAIN_TARGET)
            .createCompositeState(false)
    );

    private InspectStyle() {
    }

    /** Returns the inspector's line render type. It tests depth but never writes it. */
    public static RenderType lines() {
        return LINES;
    }

    /** One validated RGBA color. */
    public record Color(float red, float green, float blue, float alpha) {
        public Color {
            requireUnit(red, "red");
            requireUnit(green, "green");
            requireUnit(blue, "blue");
            requireUnit(alpha, "alpha");
        }

        /** Returns this color as packed ARGB for GUI text. */
        public int argb() {
            return (Math.round(this.alpha * 255.0F) << 24)
                | (Math.round(this.red * 255.0F) << 16)
                | (Math.round(this.green * 255.0F) << 8)
                | Math.round(this.blue * 255.0F);
        }

        private static void requireUnit(float value, String name) {
            if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
                throw new IllegalArgumentException("Inspect color " + name + " must be in [0, 1]");
            }
        }
    }
}
