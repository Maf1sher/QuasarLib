#version 150

out vec4 fragColor;

void main() {
    // The probe framebuffer has no color attachment; the occlusion query only
    // observes whether a sample passes the depth test.
    fragColor = vec4(0.0);
}
