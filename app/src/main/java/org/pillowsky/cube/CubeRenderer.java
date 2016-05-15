package org.pillowsky.cube;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
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

    private final float[] MVPMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
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
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        Matrix.setLookAtM(viewMatrix, 0, 0, 0, 10, 0, 0, 0, 0, 1, 0);
        Matrix.multiplyMM(MVPMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
        Matrix.rotateM(MVPMatrix, 0, (float) Math.toDegrees(orientation[0]), 0, 0, -1);
        Matrix.rotateM(MVPMatrix, 0, (float) Math.toDegrees(orientation[1]), -1, 0, 0);
        Matrix.rotateM(MVPMatrix, 0, (float) Math.toDegrees(orientation[2]), 0, 1, 0);
        cube.draw(MVPMatrix);
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;
        Matrix.perspectiveM(projectionMatrix, 0, 45, ratio, 0.1f, 100);
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

    public int loadShader(int type, String filename){
        try {
            InputStream stream = assetManager.open(filename);
            byte bytes[] = new byte[stream.available()];
            stream.read(bytes);
            String code = new String(bytes, "UTF-8");

            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, code);
            GLES20.glCompileShader(shader);

            return shader;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void checkGlError(String glOperation) {
        int error;
        if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, glOperation + ": glError " + error);
            throw new RuntimeException(glOperation + ": glError " + error);
        }
    }

    class Cube {
        private final FloatBuffer vertexBuffer;
        private final FloatBuffer colorBuffer;
        private final ByteBuffer indexBuffer;
        private final int program;
        private int positionHandle;
        private int colorHandle;
        private int MVPMatrixHandle;

        public Cube() {
            final float vertices[] = {
                    -1, -1, -1,		 1, -1, -1,
                    1,  1, -1,	    -1,  1, -1,
                    -1, -1,  1,      1, -1,  1,
                    1,  1,  1,     -1,  1,  1,
            };
            final float colors[] = {
                    0,  0,  0,  1,  0,  0,
                    1,  1,  0,  0,  1,  0,
                    0,  0,  1,  1,  0,  1,
                    1,  1,  1,  0,  1,  1,
            };
            final byte indices[] = {
                    0, 4, 5,    0, 5, 1,
                    1, 5, 6,    1, 6, 2,
                    2, 6, 7,    2, 7, 3,
                    3, 7, 4,    3, 4, 0,
                    4, 7, 6,    4, 6, 5,
                    3, 0, 1,    3, 1, 2
            };

            ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(vertices);
            vertexBuffer.position(0);

            bb = ByteBuffer.allocateDirect(colors.length * 4);
            bb.order(ByteOrder.nativeOrder());
            colorBuffer = bb.asFloatBuffer();
            colorBuffer.put(colors);
            colorBuffer.position(0);

            indexBuffer = ByteBuffer.allocateDirect(indices.length);
            indexBuffer.put(indices);
            indexBuffer.position(0);

            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, "simple.vert");
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, "simple.frag");

            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);

            positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
            colorHandle = GLES20.glGetAttribLocation(program, "vColor");
            MVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
            checkGlError("glGetUniformLocation");
        }

        public void draw(float[] mvpMatrix) {
            GLES20.glUseProgram(program);

            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, vertexBuffer);

            GLES20.glEnableVertexAttribArray(colorHandle);
            GLES20.glVertexAttribPointer(colorHandle, 3, GLES20.GL_FLOAT, false, 3 * 4, colorBuffer);

            GLES20.glUniformMatrix4fv(MVPMatrixHandle, 1, false, mvpMatrix, 0);
            checkGlError("glUniformMatrix4fv");

            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_BYTE, indexBuffer);

            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(colorHandle);
            GLES20.glUseProgram(0);
        }

    }
}