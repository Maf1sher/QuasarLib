#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;
uniform mat4 Instances[128];
uniform vec4 InstanceColors[128];
uniform vec4 InstanceData[128];

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
flat out vec2 uvOffset;
flat out vec2 uvScale;
out float faceShade;

void main() {
    vec3 local = Position + ChunkOffset;
    vec3 world = (Instances[gl_InstanceID] * vec4(local, 1.0)).xyz;
    vec3 viewPos = (ModelViewMat * vec4(world, 1.0)).xyz;
    gl_Position = ProjMat * vec4(viewPos, 1.0);

    vertexDistance = fog_distance(viewPos, FogShape);
    vertexColor = InstanceColors[gl_InstanceID] * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    uvOffset = InstanceData[gl_InstanceID].xy;
    uvScale = InstanceData[gl_InstanceID].zw;
    faceShade = 0.65 + 0.35 * abs(Normal.y);
}
