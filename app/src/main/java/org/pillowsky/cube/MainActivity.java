package org.pillowsky.cube;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        GLSurfaceView glSurfaceView = (GLSurfaceView)findViewById(R.id.glSurfaceView);
        MyGLRenderer mRenderer = new MyGLRenderer(this);

        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.setRenderer(mRenderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }
}
