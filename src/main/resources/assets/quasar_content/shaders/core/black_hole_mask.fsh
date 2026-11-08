#version 150

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float coverage = texture(Sampler0, texCoord0).a * vertexColor.a;
    fragColor = vec4(1.0, 1.0, 1.0, coverage);
}
