#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
flat in vec2 uvOffset;
flat in vec2 uvScale;
in float faceShade;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0 * uvScale + uvOffset);
    if (texel.a < 0.1) {
        discard;
    }
    vec4 color = texel * vertexColor * ColorModulator;
    color.rgb *= faceShade;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
