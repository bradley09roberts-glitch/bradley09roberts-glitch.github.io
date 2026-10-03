#version 330 compatibility
// The pack draws its own sky, sun, moon, stars and clouds in composite; the vanilla ones are hidden.
void main() {
    gl_Position = ftransform();
}
