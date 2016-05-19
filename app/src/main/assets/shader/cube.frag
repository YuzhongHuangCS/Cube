precision mediump float;

uniform samplerCube uSkybox;
varying vec3 fPosition;
varying vec3 fNormal;

void main() {
    vec3 I = normalize(fPosition);
    vec3 R = reflect(I, normalize(fNormal));
    gl_FragColor = textureCube(uSkybox, R);
}
