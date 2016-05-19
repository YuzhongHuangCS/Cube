uniform mat4 uMVPMatrix;
attribute vec3 vPosition;
varying vec3 fTexCoords;

void main() {
    gl_Position = uMVPMatrix * vec4(vPosition, 1.0);
    fTexCoords = vPosition;
}