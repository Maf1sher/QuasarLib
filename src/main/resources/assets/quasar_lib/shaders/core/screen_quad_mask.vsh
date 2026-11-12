#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    // Screen-space coverage: the effect is a post-process, so the mask spans the
    // whole viewport. Ignoring the world pose keeps a hard edge from cutting it.
    gl_Position = vec4(UV0.xy * 2.0 - 1.0, 0.0, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
}
