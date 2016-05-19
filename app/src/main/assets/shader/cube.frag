precision mediump float;

uniform samplerCube uSkybox;
varying vec3 fPosition;
varying vec3 fNormal;

void main() {
    vec3 I = normalize(fPosition);
    vec3 N = normalize(fNormal);
	float IN = dot(I, N);
	float n_vaccum = 1.0;
	float n_glass = 1.5;
    float eta = n_vaccum/n_glass;
	float cos2t;
	if ((cos2t = 1.0 - eta*eta * (1.0 - IN*IN)) < 0.0) {
	    vec3 RF = reflect(I, normalize(fNormal));
    	gl_FragColor = textureCube(uSkybox, RF);
    } else {
    	vec3 RF = reflect(I, normalize(fNormal));
        vec3 RE = refract(I, normalize(fNormal), eta);
        float a = n_glass - n_vaccum;
    	float b = n_glass + n_vaccum;
    	float R0 = a * a / (b * b);
    	float c = 1.0 + IN;
    	float Re = R0 + (1.0 - R0) * c*c*c*c*c;
    	float Tr = 1.0 - Re;
    	gl_FragColor = textureCube(uSkybox, RF) * Re + textureCube(uSkybox, RE) * Tr;
    }
}
