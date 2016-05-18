precision mediump float;

uniform samplerCube uSkybox;
varying vec3 fTexCoords;

void main() {
    gl_FragColor = textureCube(uSkybox, fTexCoords);
}
