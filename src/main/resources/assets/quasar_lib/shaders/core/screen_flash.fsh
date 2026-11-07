#version 150

uniform vec3 FlashColor;
uniform float FlashStrength;

out vec4 fragColor;

void main() {
    fragColor = vec4(FlashColor, clamp(FlashStrength, 0.0, 1.0));
}
