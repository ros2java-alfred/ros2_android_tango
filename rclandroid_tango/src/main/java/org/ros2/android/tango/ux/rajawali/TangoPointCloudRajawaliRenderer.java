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
package org.ros2.android.tango.ux.rajawali;

import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.tango.support.TangoPointCloudManager;
import com.google.tango.support.TangoSupport;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;

import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
//import org.rajawali3d.renderer.Renderer; // 1.1
import org.rajawali3d.renderer.RajawaliRenderer; // 1.0
import org.rajawali3d.scene.ASceneFrameCallback;
//import org.rajawali3d.view.SurfaceView; // 1.1
import org.rajawali3d.surface.RajawaliSurfaceView; // 1.0

import org.ros2.android.tango.ux.TangoPointCloudRenderer;

/**
 * Renderer for Point Cloud data.
 */
public class TangoPointCloudRajawaliRenderer extends RajawaliRenderer implements TangoPointCloudRenderer {

    private static final float CAMERA_NEAR = 0.01f;
    private static final float CAMERA_FAR = 200f;
    private static final int MAX_NUMBER_OF_POINTS = 60000;

    private int displayRotation = 0;
    private TouchViewHandler mTouchViewHandler;
    private RajawaliSurfaceView surfaceView;
    private TangoPointCloudManager tangoPointCloudManager;

    // Objects rendered in the scene.
    private PointCloud mPointCloud;
    private FrustumAxes mFrustumAxes;
    private Grid mGrid;
    private boolean mIsConnected;

    public TangoPointCloudRajawaliRenderer(Context context, RajawaliSurfaceView surfaceView) {
        super(context);
        this.tangoPointCloudManager = new TangoPointCloudManager();
        this.mTouchViewHandler = new TouchViewHandler(mContext, getCurrentCamera());
        this.surfaceView = surfaceView;
    }

    @Override
    protected void initScene() {
        mGrid = new Grid(100, 1, 1, 0xFFCCCCCC);
        mGrid.setPosition(0, -1.3f, 0);
        getCurrentScene().addChild(mGrid);

        mFrustumAxes = new FrustumAxes(3);
        getCurrentScene().addChild(mFrustumAxes);

        // Indicate four floats per point since the point cloud data comes
        // in XYZC format.
        mPointCloud = new PointCloud(MAX_NUMBER_OF_POINTS, 4);
        getCurrentScene().addChild(mPointCloud);
        getCurrentScene().setBackgroundColor(Color.WHITE);
        getCurrentCamera().setNearPlane(CAMERA_NEAR);
        getCurrentCamera().setFarPlane(CAMERA_FAR);
        getCurrentCamera().setFieldOfView(37.5);
    }

    /**
     * Sets Rajawali surface view and its renderer. This is ideally called only once in onCreate.
     */
    public void setupRenderer() {
        this.surfaceView.setEGLContextClientVersion(2);
        this.getCurrentScene().registerFrameCallback(new ASceneFrameCallback() {

            @Override
            public void onPreFrame(long sceneTime, double deltaTime) {
                // NOTE: This will be executed on each cycle before rendering; called from the
                // OpenGL rendering thread.

                // Prevent concurrent access from a service disconnect through the onPause event.
                synchronized (TangoPointCloudRajawaliRenderer.this) {
                    // Don't execute any Tango API actions if we're not connected to the service.
                    if (!TangoPointCloudRajawaliRenderer.this.mIsConnected) {
                        return;
                    }

                    updatePointCloud();
                    updateCamera(displayRotation);
                }
            }

            @Override
            public boolean callPreFrame() {
                return true;
            }

            @Override
            public void onPreDraw(long sceneTime, double deltaTime) {

            }

            @Override
            public void onPostFrame(long sceneTime, double deltaTime) {

            }
        });
        this.surfaceView.setSurfaceRenderer(this);
    }


    /**
     * Updates the rendered point cloud. For this, we need the point cloud data and the device pose
     * at the time the cloud data was acquired.
     * NOTE: This needs to be called from the OpenGL rendering thread.
     */
    private void updatePointCloud(TangoPointCloudData pointCloudData, float[] openGlTdepth) {
        mPointCloud.updateCloud(pointCloudData.numPoints, pointCloudData.points);
        Matrix4 openGlTdepthMatrix = new Matrix4(openGlTdepth);
        mPointCloud.setPosition(openGlTdepthMatrix.getTranslation());
        // Conjugating the Quaternion is needed because Rajawali uses left-handed convention.
        mPointCloud.setOrientation(new Quaternion().fromMatrix(openGlTdepthMatrix).conjugate());
    }

    /**
     * Updates our information about the current device pose.
     * NOTE: This needs to be called from the OpenGL rendering thread.
     */
    private void updateCameraPose(TangoPoseData cameraPose) {
        float[] rotation = cameraPose.getRotationAsFloats();
        float[] translation = cameraPose.getTranslationAsFloats();
        Quaternion quaternion = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);
        mFrustumAxes.setPosition(translation[0], translation[1], translation[2]);
        // Conjugating the Quaternion is needed because Rajawali uses left-handed convention for
        // quaternions.
        mFrustumAxes.setOrientation(quaternion.conjugate());
        this.mTouchViewHandler.updateCamera(new Vector3(translation[0], translation[1], translation[2]),
                quaternion);
    }

    @Override
    public void onOffsetsChanged(float v, float v1, float v2, float v3, int i, int i1) {
    }

    @Override
    public void onTouchEvent(MotionEvent motionEvent) {
        this.mTouchViewHandler.onTouchEvent(motionEvent);
    }

    public void setFirstPersonView() {
        this.mTouchViewHandler.setFirstPersonView();
    }

    public void setTopDownView() {
        this.mTouchViewHandler.setTopDownView();
    }

    public void setThirdPersonView() {
        this.mTouchViewHandler.setThirdPersonView();
    }

    private void updateCamera(final int displayRotation) {
        // Update current camera pose.
        try {
            // Calculate the device pose. This transform is used to display
            // frustum in third and top down view, and used to render camera pose in
            // first person view.
            TangoPoseData lastFramePose = TangoSupport.getPoseAtTime(0,
                    TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                    TangoPoseData.COORDINATE_FRAME_DEVICE,
                    TangoSupport.ENGINE_OPENGL,
                    TangoSupport.ENGINE_OPENGL,
                    displayRotation);

            if (lastFramePose.statusCode == TangoPoseData.POSE_VALID) {
                this.updateCameraPose(lastFramePose);
            }
        } catch (TangoErrorException e) {
            //Log.e(TAG, "Could not get valid transform");
        }
    }

    private void updatePointCloud() {
        // Update point cloud data.
        TangoPointCloudData pointCloud = this.tangoPointCloudManager.getLatestPointCloud();

        if (pointCloud != null) {
            // Calculate the depth camera pose at the last point cloud update.
            TangoSupport.MatrixTransformData transform =
                    TangoSupport.getMatrixTransformAtTime(pointCloud.timestamp,
                            TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                            TangoPoseData.COORDINATE_FRAME_CAMERA_DEPTH,
                            TangoSupport.ENGINE_OPENGL,
                            TangoSupport.ENGINE_TANGO,
                            TangoSupport.ROTATION_IGNORED);

            if (transform.statusCode == TangoPoseData.POSE_VALID) {
                this.updatePointCloud(pointCloud, transform.matrix);
            }
        }
    }

    @Override
    public TangoPointCloudManager getPointCloudManager() {
        return this.tangoPointCloudManager;
    }

    public void setDisplayRotation(int displayRotation) {
        this.displayRotation = displayRotation;
    }

    @Override
    public void setConnected(boolean value) {
        this.mIsConnected = value;
    }
}