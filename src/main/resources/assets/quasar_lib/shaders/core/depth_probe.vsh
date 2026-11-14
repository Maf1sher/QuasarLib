#version 150

in vec3 Position;

uniform mat4 ProbeMVP;

void main() {
    gl_Position = ProbeMVP * vec4(Position, 1.0);
}
