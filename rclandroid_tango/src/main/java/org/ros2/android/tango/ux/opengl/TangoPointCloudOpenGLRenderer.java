/* Copyright 2017 Mickael Gaillard <mick.gaillard@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ros2.android.tango.ux.opengl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;

import com.google.atap.tangoservice.TangoCameraIntrinsics;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.tango.support.TangoPointCloudManager;
import com.google.tango.support.TangoSupport;

import org.ros2.android.tango.ux.TangoPointCloudRenderer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class TangoPointCloudOpenGLRenderer implements GLSurfaceView.Renderer, TangoPointCloudRenderer {

    private int displayRotation = 0;
    private Context mContext;
    private GLSurfaceView surfaceView;
    private TangoPointCloudManager tangoPointCloudManager;

    private OpenGlCameraPreview mOpenGlCameraPreview;
    private OpenGlSphere mEarthSphere;
    private OpenGlSphere mMoonSphere;
    private boolean mProjectionMatrixConfigured;
    private RenderCallback mRenderCallback;

    public TangoPointCloudOpenGLRenderer(Context context, GLSurfaceView surfaceView) {
        this.mContext = context;
        this.surfaceView = surfaceView;
        this.tangoPointCloudManager = new TangoPointCloudManager();

        mOpenGlCameraPreview = new OpenGlCameraPreview();
        mEarthSphere = new OpenGlSphere(0.15f, 20, 20);
        mMoonSphere = new OpenGlSphere(0.05f, 10, 10);
    }

    @Override
    public void setFirstPersonView() {

    }

    @Override
    public void setTopDownView() {

    }

    @Override
    public void setThirdPersonView() {

    }

    @Override
    public void onTouchEvent(MotionEvent event) {

    }

    /**
     * Here is where you would set up your rendering logic. We're replacing it with a minimalistic,
     * dummy example, using a standard GLSurfaceView and a basic renderer, for illustration purposes
     * only.
     */
    public void setupRenderer() {
        surfaceView.setEGLContextClientVersion(2);
//        renderer = new OpenGlAugmentedRealityRenderer(this,
//                new OpenGlAugmentedRealityRenderer.RenderCallback() {
//                    private double lastRenderedTimeStamp;
//
//                    @Override
//                    public void preRender() {
//                        // This is the work that you would do on your main OpenGL render thread.
//
//                        try {
//                            // Synchronize against concurrently disconnecting the service triggered
//                            // from the UI thread.
//                            synchronized (OpenGlAugmentedRealityActivity.this) {
//                                // We need to be careful not to run any Tango-dependent code in the
//                                // OpenGL thread unless we know the Tango Service is properly
//                                // set up and connected.
//                                if (!mIsConnected) {
//                                    return;
//                                }
//
//                                // Set up scene camera projection to match RGB camera intrinsics.
//                                if (!mRenderer.isProjectionMatrixConfigured()) {
//                                    TangoCameraIntrinsics intrinsics =
//                                            TangoSupport.getCameraIntrinsicsBasedOnDisplayRotation(
//                                                    TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
//                                                    mDisplayRotation);
//                                    mRenderer.setProjectionMatrix(
//                                            projectionMatrixFromCameraIntrinsics(intrinsics));
//                                }
//                                // Connect the Tango SDK to the OpenGL texture ID where we are
//                                // going to render the camera.
//                                // NOTE: This must be done after both the texture is generated
//                                // and the Tango Service is connected.
//                                if (mConnectedTextureIdGlThread != mRenderer.getTextureId()) {
//                                    mTango.connectTextureId(
//                                            TangoCameraIntrinsics.TANGO_CAMERA_COLOR,
//                                            mRenderer.getTextureId());
//                                    mConnectedTextureIdGlThread = mRenderer.getTextureId();
//                                    Log.d(TAG, "connected to texture id: " +
//                                            mRenderer.getTextureId());
//                                }
//                                // If there is a new RGB camera frame available, update the texture
//                                // and scene camera pose.
//                                if (mIsFrameAvailableTangoThread.compareAndSet(true, false)) {
//                                    // {@code mRgbTimestampGlThread} contains the exact timestamp at
//                                    // which the rendered RGB frame was acquired.
//                                    mRgbTimestampGlThread =
//                                            mTango.updateTexture(TangoCameraIntrinsics.
//                                                    TANGO_CAMERA_COLOR);
//
//                                    // Get the transform from color camera to Start of Service
//                                    // at the timestamp of the RGB image in OpenGL coordinates.
//                                    TangoSupport.MatrixTransformData transform =
//                                            TangoSupport.getMatrixTransformAtTime(
//                                                    mRgbTimestampGlThread,
//                                                    TangoPoseData
//                                                            .COORDINATE_FRAME_START_OF_SERVICE,
//                                                    TangoPoseData.COORDINATE_FRAME_CAMERA_COLOR,
//                                                    TangoSupport.ENGINE_OPENGL,
//                                                    TangoSupport.ENGINE_OPENGL,
//                                                    mDisplayRotation);
//                                    if (transform.statusCode == TangoPoseData.POSE_VALID) {
//
//                                        mRenderer.updateViewMatrix(transform.matrix);
//                                        double deltaTime = mRgbTimestampGlThread
//                                                - lastRenderedTimeStamp;
//                                        lastRenderedTimeStamp = mRgbTimestampGlThread;
//
//                                        // Set the earth rotation around itself.
//                                        float[] openGlTEarth = new float[16];
//                                        Matrix.rotateM(mEarthMoonCenterTEarth, 0, (float)
//                                                deltaTime * 360 / 10, 0, 1, 0);
//                                        Matrix.multiplyMM(openGlTEarth, 0, mOpenGLTEarthMoonCenter,
//                                                0, mEarthMoonCenterTEarth, 0);
//
//                                        // Set moon rotation around the earth and moon center.
//                                        float[] openGlTMoon = new float[16];
//                                        Matrix.rotateM(mEarthMoonCenterTMoonRotation, 0, (float)
//                                                deltaTime * 360 / 50, 0, 1, 0);
//                                        float[] mEarthTMoon = new float[16];
//                                        Matrix.multiplyMM(mEarthTMoon, 0,
//                                                mEarthMoonCenterTMoonRotation, 0,
//                                                mEarthMoonCenterTTranslation, 0);
//                                        Matrix.multiplyMM(openGlTMoon, 0,
//                                                mOpenGLTEarthMoonCenter,
//                                                0, mEarthTMoon, 0);
//
//                                        mRenderer.setEarthTransform(openGlTEarth);
//                                        mRenderer.setMoonTransform(openGlTMoon);
//                                    } else {
//                                        // When the pose status is not valid, it indicates tracking
//                                        // has been lost. In this case, we simply stop rendering.
//                                        //
//                                        // This is also the place to display UI to suggest that the
//                                        // user walk to recover tracking.
//                                        Log.w(TAG, "Could not get a valid transform at time " +
//                                                mRgbTimestampGlThread);
//                                    }
//                                }
//                            }
//                            // Avoid crashing the application due to unhandled exceptions.
//                        } catch (TangoErrorException e) {
//                            Log.e(TAG, "Tango API call error within the OpenGL render thread", e);
//                        } catch (Throwable t) {
//                            Log.e(TAG, "Exception on the OpenGL thread", t);
//                        }
//                    }
//                });
//
//        // Set the starting position and orientation of the Earth and Moon with respect to the
//        // OpenGL frame.
//        Matrix.setIdentityM(mOpenGLTEarthMoonCenter, 0);
//        Matrix.translateM(mOpenGLTEarthMoonCenter, 0, 0, 0, -1f);
//        Matrix.setIdentityM(mEarthMoonCenterTEarth, 0);
//        Matrix.setIdentityM(mEarthMoonCenterTMoonRotation, 0);
//        Matrix.setIdentityM(mEarthMoonCenterTTranslation, 0);
//        Matrix.translateM(mEarthMoonCenterTTranslation, 0, 0.5f, 0, 0);

        surfaceView.setRenderer(this);
    }

    @Override
    public TangoPointCloudManager getPointCloudManager() {
        return this.tangoPointCloudManager;
    }

    @Override
    public void setDisplayRotation(int displayRotation) {
        this.displayRotation = displayRotation;
    }

    @Override
    public void setConnected(boolean b) {

    }


    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        // Enable depth test to discard fragments that are behind another fragment.
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        // Enable face culling to discard back-facing triangles.
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
        mOpenGlCameraPreview.setUpProgramAndBuffers();
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
//        Bitmap earthBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable
//                .earth, options);
//        mEarthSphere.setUpProgramAndBuffers(earthBitmap);
//        Bitmap moonBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable
//                .moon, options);
//        mMoonSphere.setUpProgramAndBuffers(moonBitmap);
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        mProjectionMatrixConfigured = false;
    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // Call application-specific code that needs to run on the OpenGL thread.
        mRenderCallback.preRender();
        // Don't write depth buffer because we want to draw the camera as background.
        GLES20.glDepthMask(false);
        mOpenGlCameraPreview.drawAsBackground();
        // Enable depth buffer again for AR.
        GLES20.glDepthMask(true);
        GLES20.glCullFace(GLES20.GL_BACK);
        mEarthSphere.drawSphere();
        mMoonSphere.drawSphere();
    }

    /**
     * A small callback to allow the caller to introduce application-specific code to be executed
     * in the OpenGL thread.
     */
    public interface RenderCallback {
        void preRender();
    }
}
