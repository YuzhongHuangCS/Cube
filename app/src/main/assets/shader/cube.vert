uniform mat4 uMVPMatrix;
uniform mat4 uModelMatrix;
uniform mat4 uModelMatrixIT;
attribute vec4 vPosition;
attribute vec4 vNormal;
varying vec3 fPosition;
varying vec3 fNormal;

void main() {
    gl_Position = uMVPMatrix * vPosition;
    fPosition = (uModelMatrix * vPosition).xyz;
    fNormal = (uModelMatrixIT * vNormal).xyz;
}