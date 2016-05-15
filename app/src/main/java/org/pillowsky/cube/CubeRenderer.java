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

public class CubeRenderer implements GLSurfaceView.Renderer, SensorEventListener {

    private static final String TAG = "CubeRenderer";
    private static AssetManager mAssetManager;
    private final SensorManager mSensorManager;
    private final Sensor mRotationVectorSensor;
    private Cube mCube;

    // mMVPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] mMVPMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mRotationMatrix = new float[16];
    private final float[] mOrientation = new float[3];

    public CubeRenderer(Context context) {
        mAssetManager = context.getAssets();
        mSensorManager = (SensorManager)context.getSystemService(context.SENSOR_SERVICE);
        mRotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LESS);
        mCube = new Cube();

    }

    @Override
    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 10, 0, 0, 0, 0, 1, 0);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);
        Matrix.rotateM(mMVPMatrix, 0, (float) Math.toDegrees(mOrientation[0]), 0, 0, -1);
        Matrix.rotateM(mMVPMatrix, 0, (float) Math.toDegrees(mOrientation[1]), -1, 0, 0);
        Matrix.rotateM(mMVPMatrix, 0, (float) Math.toDegrees(mOrientation[2]), 0, 1, 0);
        mCube.draw(mMVPMatrix);
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        // Adjust the viewport based on geometry changes,
        // such as screen rotation
        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / height;

        // this projection matrix is applied to object coordinates
        // in the onDrawFrame() method
        Matrix.perspectiveM(mProjectionMatrix, 0, 45, ratio, 0.1f, 100);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, String.valueOf(accuracy));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(mRotationMatrix , event.values);
            SensorManager.getOrientation(mRotationMatrix, mOrientation);
        }
    }

    public void onResume() {
        mSensorManager.registerListener(this, mRotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void onPause() {
        mSensorManager.unregisterListener(this);
    }

    public static int loadShader(int type, String filename){
        try {
            InputStream stream = mAssetManager.open(filename);
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

    public static void checkGlError(String glOperation) {
        int error;
        if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, glOperation + ": glError " + error);
            throw new RuntimeException(glOperation + ": glError " + error);
        }
    }

}