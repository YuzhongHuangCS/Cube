package org.pillowsky.cube;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class CubeRenderer implements GLSurfaceView.Renderer, SensorEventListener {

    private static final String TAG = "CubeRenderer";
    private static AssetManager assetManager;
    private final SensorManager sensorManager;
    private final Sensor rotationVectorSensor;
    private Cube cube;
    private Skybox skybox;

    private final float[] mvpMatrix = new float[16];
    private final float[] vpMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] rotationMatrix = new float[16];
    private final float[] orientation = new float[3];

    public CubeRenderer(Context context) {
        assetManager = context.getAssets();
        sensorManager = (SensorManager)context.getSystemService(context.SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LESS);
        cube = new Cube();
        skybox = new Skybox();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.setLookAtM(viewMatrix, 0, 0, 0, 0, 0, 0, -10, 0, 1, 0);
        Matrix.translateM(modelMatrix, 0, 0, 0, -5);
        Matrix.rotateM(modelMatrix, 0, SystemClock.uptimeMillis() * 0.01f, 0, 1, 0);
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix.clone(), 0, modelMatrix, 0);
        Matrix.rotateM(viewMatrix, 0, (float) Math.toDegrees(orientation[0]), 0, 0, -1);
        Matrix.rotateM(viewMatrix, 0, (float) Math.toDegrees(orientation[1]), 1, 0, 0);
        Matrix.rotateM(viewMatrix, 0, (float) Math.toDegrees(orientation[2]), 0, 1, 0);
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0);
        cube.draw(mvpMatrix, modelMatrix);
        skybox.draw(vpMatrix);
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;
        Matrix.perspectiveM(projectionMatrix, 0, 90, ratio, 0.1f, 100);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, String.valueOf(accuracy));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
        }
    }

    public void onResume() {
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void onPause() {
        sensorManager.unregisterListener(this);
    }

    public Bitmap loadBitmap(String path) {
        try (InputStream stream = assetManager.open(path)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            return BitmapFactory.decodeStream(stream, null, options);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int buildShader(String path, int type){
        try (InputStream stream = assetManager.open(path)) {
            byte bytes[] = new byte[stream.available()];
            stream.read(bytes);
            String code = new String(bytes, "UTF-8");

            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, code);
            GLES20.glCompileShader(shader);

            int[] result = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, result, 0);
            if (result[0] != GLES20.GL_TRUE) {
                throw new RuntimeException("Build shader failed: " + GLES20.glGetShaderInfoLog(shader));
            }

            return shader;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int buildProgram(String[] pathArray, int[] typeArray) {
        int program = GLES20.glCreateProgram();
        for (int i = 0; i < Math.min(pathArray.length, typeArray.length); i++) {
            GLES20.glAttachShader(program, buildShader(pathArray[i], typeArray[i]));
        }
        GLES20.glLinkProgram(program);

        int[] result = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, result, 0);
        if (result[0] != GLES20.GL_TRUE) {
            throw new RuntimeException("Build program failed: " + GLES20.glGetProgramInfoLog(program));
        }

        return program;
    }

    public void checkGlError(String glOperation) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(glOperation + ": glError " + error + " " + GLUtils.getEGLErrorString(error));
        }
    }

    class Skybox {
        private final FloatBuffer vertexBuffer;
        private final ByteBuffer indexBuffer;
        private final int program;
        private int positionHandle;
        private int mvpMatrixHandle;
        private int skyboxHandle;
        private final int[] skyboxTexture = new int[1];

        public Skybox() {
            final float vertices[] = {
                    -10, -10, -10,  10, -10, -10,
                    10,  10, -10,	-10,  10, -10,
                    -10, -10,  10,  10, -10,  10,
                    10,  10,  10,   -10,  10, 10,
            };
            final byte indices[] = {
                    0, 4, 5,    0, 5, 1,
                    1, 5, 6,    1, 6, 2,
                    2, 6, 7,    2, 7, 3,
                    3, 7, 4,    3, 4, 0,
                    4, 7, 6,    4, 6, 5,
                    3, 0, 1,    3, 1, 2
            };

            final String faces[] = {
                    "skybox/right.jpg",
                    "skybox/left.jpg",
                    "skybox/top.jpg",
                    "skybox/bottom.jpg",
                    "skybox/back.jpg",
                    "skybox/front.jpg"
            };

            ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(vertices);
            vertexBuffer.position(0);

            indexBuffer = ByteBuffer.allocateDirect(indices.length);
            indexBuffer.put(indices);
            indexBuffer.position(0);

            GLES20.glGenTextures(1, skyboxTexture, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, skyboxTexture[0]);

            for (int i = 0; i < 6; i++) {
                Bitmap bitmap = loadBitmap(faces[i]);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, bitmap, 0);
                bitmap.recycle();
            }

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, 0);

            program = buildProgram(new String[] {"shader/skybox.vert", "shader/skybox.frag"}, new int[] {GLES20.GL_VERTEX_SHADER, GLES20.GL_FRAGMENT_SHADER});
            checkGlError("program");

            positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
            mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
            skyboxHandle = GLES20.glGetUniformLocation(program, "uSkybox");
            checkGlError("glGetUniformLocation");
        }

        public void draw(float[] mvpMatrix) {
            GLES20.glUseProgram(program);

            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, vertexBuffer);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, skyboxTexture[0]);
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
            GLES20.glUniform1i(skyboxHandle, 0);
            checkGlError("glUniform");

            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_BYTE, indexBuffer);

            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, 0);
            GLES20.glUseProgram(0);
        }

    }

    class Cube {
        private final FloatBuffer vertexBuffer;
        private final FloatBuffer normalBuffer;
        private final int program;
        private int positionHandle;
        private int normalHandle;
        private int mvpMatrixHandle;
        private int modelMatrixHandle;
        private int modelMatrixITHandle;
        private int skyboxHandle;
        private final int[] skyboxTexture = new int[1];
        private final float[] modelMatrixI = new float[16];
        private final float[] modelMatrixIT = new float[16];

        public Cube() {
            final float vertices[] = {
                    -1, -1, -1,
                    -1, -1, 1,
                    1, -1, 1,

                    -1, -1, -1,
                    1, -1, 1,
                    1, -1, -1,

                    1, -1, -1,
                    1, -1, 1,
                    1,  1,  1,

                    1, -1, -1,
                    1,  1,  1,
                    1,  1, -1,

                    1,  1, -1,
                    1,  1,  1,
                    -1,  1, 1,

                    1,  1, -1,
                    -1,  1, 1,
                    -1,  1, -1,

                    -1,  1, -1,
                    -1,  1, 1,
                    -1, -1, 1,

                    -1,  1, -1,
                    -1, -1, 1,
                    -1, -1, -1,

                    -1, -1, 1,
                    -1,  1, 1,
                    1,  1,  1,

                    -1, -1, 1,
                    1,  1,  1,
                    1, -1, 1,

                    -1,  1, -1,
                    -1, -1, -1,
                    1, -1, -1,

                    -1,  1, -1,
                    1, -1, -1,
                    1,  1, -1,
            };
            final float normals[] = {
                    0, -1, 0,
                    0, -1, 0,
                    0, -1, 0,

                    0, -1, 0,
                    0, -1, 0,
                    0, -1, 0,

                    1, 0, 0,
                    1, 0, 0,
                    1, 0, 0,

                    1, 0, 0,
                    1, 0, 0,
                    1, 0, 0,

                    0, 1, 0,
                    0, 1, 0,
                    0, 1, 0,

                    0, 1, 0,
                    0, 1, 0,
                    0, 1, 0,

                    -1, 0, 0,
                    -1, 0, 0,
                    -1, 0, 0,

                    -1, 0, 0,
                    -1, 0, 0,
                    -1, 0, 0,

                    0, 0, 1,
                    0, 0, 1,
                    0, 0, 1,

                    0, 0, 1,
                    0, 0, 1,
                    0, 0, 1,

                    0, 0, -1,
                    0, 0, -1,
                    0, 0, -1,

                    0, 0, -1,
                    0, 0, -1,
                    0, 0, -1,
            };

            final String faces[] = {
                    "skybox/right.jpg",
                    "skybox/left.jpg",
                    "skybox/top.jpg",
                    "skybox/bottom.jpg",
                    "skybox/back.jpg",
                    "skybox/front.jpg"
            };

            ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(vertices);
            vertexBuffer.position(0);

            bb = ByteBuffer.allocateDirect(normals.length * 4);
            bb.order(ByteOrder.nativeOrder());
            normalBuffer = bb.asFloatBuffer();
            normalBuffer.put(normals);
            normalBuffer.position(0);

            GLES20.glGenTextures(1, skyboxTexture, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, skyboxTexture[0]);

            for (int i = 0; i < 6; i++) {
                Bitmap bitmap = loadBitmap(faces[i]);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, bitmap, 0);
                bitmap.recycle();
            }

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_CUBE_MAP, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, 0);

            program = buildProgram(new String[] {"shader/cube.vert", "shader/cube.frag"}, new int[] {GLES20.GL_VERTEX_SHADER, GLES20.GL_FRAGMENT_SHADER});
            checkGlError("program");

            positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
            normalHandle = GLES20.glGetAttribLocation(program, "vNormal");
            mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
            modelMatrixHandle = GLES20.glGetUniformLocation(program, "uModelMatrix");
            modelMatrixITHandle = GLES20.glGetUniformLocation(program, "uModelMatrixIT");
            skyboxHandle = GLES20.glGetUniformLocation(program, "uSkybox");
            checkGlError("glGetUniformLocation");
        }

        public void draw(float[] mvpMatrix, float[] modelMatrix) {
            Matrix.invertM(modelMatrixI, 0, modelMatrix, 0);
            Matrix.transposeM(modelMatrixIT, 0, modelMatrixI, 0);
            GLES20.glUseProgram(program);

            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, vertexBuffer);

            GLES20.glEnableVertexAttribArray(normalHandle);
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, normalBuffer);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, skyboxTexture[0]);
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
            GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0);
            GLES20.glUniformMatrix4fv(modelMatrixITHandle, 1, false, modelMatrixIT, 0);
            GLES20.glUniform1i(skyboxHandle, 0);
            checkGlError("glUniform");

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);

            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(normalHandle);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_CUBE_MAP, 0);
            GLES20.glUseProgram(0);
        }
    }
}