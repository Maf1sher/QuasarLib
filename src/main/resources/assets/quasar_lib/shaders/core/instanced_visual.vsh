#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>
#moj_import <quasar_lib:instance_payload.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform samplerBuffer InstanceBuffer;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
flat out vec2 effectData;

void main() {
    vec3 local = Position + ChunkOffset;
    vec3 world = (quasarInstanceMatrix(InstanceBuffer, gl_InstanceID) * vec4(local, 1.0)).xyz;
    vec3 viewPos = (ModelViewMat * vec4(world, 1.0)).xyz;
    gl_Position = ProjMat * vec4(viewPos, 1.0);

    vertexDistance = fog_distance(world, FogShape);
    vertexColor = quasarInstanceColor(InstanceBuffer, gl_InstanceID)
        * Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    effectData = quasarInstanceData(InstanceBuffer, gl_InstanceID).xy;
}
