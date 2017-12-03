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
package org.ros2.android.tango;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.google.atap.tangoservice.Tango;
import com.google.atap.tangoservice.TangoConfig;
import com.google.atap.tangoservice.TangoCoordinateFramePair;
import com.google.atap.tangoservice.TangoErrorException;
import com.google.atap.tangoservice.TangoEvent;
import com.google.atap.tangoservice.TangoInvalidException;
import com.google.atap.tangoservice.TangoOutOfDateException;
import com.google.atap.tangoservice.TangoPointCloudData;
import com.google.atap.tangoservice.TangoPoseData;
import com.google.tango.support.TangoPointCloudManager;
import com.google.tango.support.TangoSupport;
import com.google.tango.ux.TangoUx;
import com.google.tango.ux.UxExceptionEvent;
import com.google.tango.ux.UxExceptionEventListener;

import org.ros2.android.core.RosConfig;
import org.ros2.android.core.RosManager;
import org.ros2.android.core.node.AndroidNativeNode;
import org.ros2.android.tango.ux.TangoPointCloudRenderer;
import org.ros2.rcljava.node.topic.Publisher;
import org.ros2.rcljava.qos.QoSProfile;
import org.ros2.rcljava.time.WallTimer;
import org.ros2.rcljava.time.WallTimerCallback;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import geometry_msgs.msg.Point32;
import sensor_msgs.msg.Imu;
import sensor_msgs.msg.PointCloud;

public class TangoNode extends AndroidNativeNode implements WallTimerCallback {
    private static final String TAG = "TangoNode";

    private static final String UX_EXCEPTION_EVENT_DETECTED = "Exception Detected: ";
    private static final String UX_EXCEPTION_EVENT_RESOLVED = "Exception Resolved: ";

    private static final int SECS_TO_MILLISECS = 1000;
    private static final double UPDATE_INTERVAL_MS = 100.0;

    private Tango tango;
    private TangoConfig tangoConfig;
    private TangoUx tangoUx = null;

    private TangoPointCloudRenderer renderer;
    private TangoPointCloudManager pointCloudManager;

    private double mPointCloudTimeToNextUpdate = UPDATE_INTERVAL_MS;
    private double mPointCloudPreviousTimeStamp;

    private RosConfig rosConfig;
    private RosManager rosManager;
    private WallTimer timer;
    private Publisher<PointCloud> pcPublisher;
    private Publisher<Imu> imuPublisher;

    public TangoNode (Context context, String name) {
        this(context, name,null);
    }

    public TangoNode (Context context, String name, TangoPointCloudRenderer renderer) {
        super(name, context);
        if (renderer != null) {
            this.renderer = renderer;
            this.tangoUx = this.setupTangoUxAndLayout(context);
            this.pointCloudManager = this.renderer.getPointCloudManager();
        }

        this.pcPublisher = this.createPublisher(PointCloud.class, "/cloud", QoSProfile.SENSOR_DATA);
        this.imuPublisher = this.createPublisher(Imu.class, "/imu", QoSProfile.SENSOR_DATA);

        this.timer = this.createWallTimer(500, TimeUnit.MILLISECONDS, this);
    }

    private void publishPointCloud() {
        PointCloud pc = new PointCloud();
        Collection<Point32> points = new ArrayList<>();

        TangoPointCloudData pointsBuffer = this.pointCloudManager.getLatestPointCloud();
        for (int i = 0; i < pointsBuffer.points.capacity() - 3; i = i + 3) {
            Point32 point = new Point32();
            point.setX(pointsBuffer.points.get(i));
            point.setY(pointsBuffer.points.get(i+1));
            point.setZ(pointsBuffer.points.get(i+2));
            points.add(point);
        }
        pc.setPoints(points);

        this.pcPublisher.publish(pc);
    }

    private void publishImu() {
        Imu imu = new Imu();

        // TODO

        this.imuPublisher.publish(imu);

    }

    public void onResume(final Activity activity) {

        // Initialize the Tango Service as a normal Android Service.
        // Since we call mTango.disconnect() in onPause, this will unbind the
        // Tango Service, so every time onResume is called, we should
        // create a new Tango object.
        this.tango = new Tango(activity, new Runnable(){
            // Pass in a Runnable to be called from UI thread when Tango
            // is ready. This Runnable will be running on a new thread.
            // When Tango is ready, we can call Tango functions
            // safely here only when there are no UI thread changes involved.
            @Override
            public void run() {
                synchronized (activity) {
                    try {
                        tangoConfig = setupTangoConfig(tango);
                        tango.connect(tangoConfig);
                        startupTango(activity);
                        TangoSupport.initialize(tango);
                        if (renderer != null) {
                            renderer.setConnected(true);
//                        renderer.setDisplayRotation();
                        }
                    } catch (TangoOutOfDateException e) {
                        Log.e(TAG, activity.getString(R.string.exception_out_of_date), e);
                    } catch (TangoErrorException e) {
                        Log.e(TAG, activity.getString(R.string.exception_tango_error), e);
                    } catch (TangoInvalidException e) {
                        Log.e(TAG, activity.getString(R.string.exception_tango_invalid), e);
                    }
                }
            }
        });

        this.rosManager = new RosManager(activity, new Runnable() {
            @Override
            public void run() {
                synchronized (activity) {
                    try {
                        rosConfig = setupRosConfig(rosManager);
                        rosManager.connect(rosConfig);
                        startupRos(activity);
                    } catch (Exception e) {
                        Log.e(TAG, activity.getString(R.string.exception_ros_error), e);
                    }
                }
            }
        });
    }

    public void onPause(final Activity activity) {
        synchronized (this) {
            try {
                // Unbind the Tango Service. If you don't, you'll get a
                // service leak exception.
                this.tangoUx.stop();
                this.tango.disconnect();
                if (renderer != null) {
                    this.renderer.setConnected(false);
                }
            } catch (TangoErrorException e) {
                Log.e(TAG, activity.getString(R.string.exception_tango_error), e);
            }
        }
    }

    private void startupRos(final Activity activity) {
        this.rosManager.addNode(this);
    }

    /**
     * Set up the callback listeners for the Tango Service and obtain other parameters required
     * after Tango connection.
     * Listen to updates from the Point Cloud and Tango Events and Pose.
     */
    private void startupTango(final Activity activity) {
        ArrayList<TangoCoordinateFramePair> framePairs = new ArrayList<TangoCoordinateFramePair>();

        framePairs.add(new TangoCoordinateFramePair(TangoPoseData.COORDINATE_FRAME_START_OF_SERVICE,
                TangoPoseData.COORDINATE_FRAME_DEVICE));

        this.tango.connectListener(framePairs, new Tango.TangoUpdateCallback() {

            @Override
            public void onPoseAvailable(TangoPoseData pose) {
                // Passing in the pose data to UX library produce exceptions.
                if (tangoUx != null) {
                    tangoUx.updatePoseStatus(pose.statusCode);
                }
            }

            @Override
            public void onPointCloudAvailable(TangoPointCloudData pointCloud) {
                if (tangoUx != null) {
                    tangoUx.updatePointCloud(pointCloud);
                }
                pointCloudManager.updatePointCloud(pointCloud);

                final double currentTimeStamp = pointCloud.timestamp;
                final double pointCloudFrameDelta =
                        (currentTimeStamp - mPointCloudPreviousTimeStamp) * SECS_TO_MILLISECS;
                mPointCloudPreviousTimeStamp = currentTimeStamp;

                mPointCloudTimeToNextUpdate -= pointCloudFrameDelta;

                if (mPointCloudTimeToNextUpdate < 0.0) {
                    mPointCloudTimeToNextUpdate = UPDATE_INTERVAL_MS;
                    final String pointCountString = Integer.toString(pointCloud.numPoints);
                    final double averageDepth = getAveragedDepth(pointCloud.points,
                            pointCloud.numPoints);

//                    activity.runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            mPointCountTextView.setText(pointCountString);
//                            mAverageZTextView.setText(FORMAT_THREE_DECIMAL.format(averageDepth));
//                        }
//                    });
                }
            }

            @Override
            public void onTangoEvent(TangoEvent event) {
                if (tangoUx != null) {
                    tangoUx.updateTangoEvent(event);
                }
            }
        });
    }

    /**
     * Sets up the Tango configuration object. Make sure mTango object is initialized before
     * making this call.
     */
    private TangoConfig setupTangoConfig(Tango tango) {
        // Use the default configuration plus add depth sensing.
        TangoConfig config = tango.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);
        config.putBoolean(TangoConfig.KEY_BOOLEAN_AUTORECOVERY, true);

        config.putBoolean(TangoConfig.KEY_BOOLEAN_DEPTH, true);
        config.putInt(TangoConfig.KEY_INT_DEPTH_MODE, TangoConfig.TANGO_DEPTH_MODE_POINT_CLOUD);
        return config;
    }

    private RosConfig setupRosConfig(RosManager rosManager) {
        RosConfig config = rosManager.getConfig(TangoConfig.CONFIG_TYPE_DEFAULT);

        return config;
    }

    /**
     * Sets up TangoUX and sets its listener.
     */
    private TangoUx setupTangoUxAndLayout(Context context) {
        TangoUx tangoUx = new TangoUx(context);
        tangoUx.setUxExceptionEventListener(this.uxExceptionListener);
        return tangoUx;
    }

    /**
     * Calculates the average depth from a point cloud buffer.
     *
     * @param pointCloudBuffer
     * @param numPoints
     * @return Average depth.
     */
    private float getAveragedDepth(FloatBuffer pointCloudBuffer, int numPoints) {
        float totalZ = 0;
        float averageZ = 0;
        if (numPoints != 0) {
            int numFloats = 4 * numPoints;
            for (int i = 2; i < numFloats; i = i + 4) {
                totalZ = totalZ + pointCloudBuffer.get(i);
            }
            averageZ = totalZ / numPoints;
        }
        return averageZ;
    }

    /*
    * Set a UxExceptionEventListener to be notified of any UX exceptions.
    * In this example we are just logging all the exceptions to logcat, but in a real app,
    * developers should use these exceptions to contextually notify the user and help direct the
    * user in using the device in a way Tango Service expects it.
    * <p>
    * A UxExceptionEvent can have two statuses: DETECTED and RESOLVED.
    * An event is considered DETECTED when the exception conditions are observed, and RESOLVED when
    * the root causes have been addressed.
    * Both statuses will trigger a separate event.
    */
    private UxExceptionEventListener uxExceptionListener = new UxExceptionEventListener() {
        @Override
        public void onUxExceptionEvent(UxExceptionEvent uxExceptionEvent) {
            String status = uxExceptionEvent.getStatus() == UxExceptionEvent.STATUS_DETECTED ?
                    UX_EXCEPTION_EVENT_DETECTED : UX_EXCEPTION_EVENT_RESOLVED;

            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_LYING_ON_SURFACE) {
                Log.i(TAG, status + "Device lying on surface");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FEW_DEPTH_POINTS) {
                Log.i(TAG, status + "Too few depth points");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FEW_FEATURES) {
                Log.i(TAG, status + "Too few features");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_MOTION_TRACK_INVALID) {
                Log.i(TAG, status + "Invalid poses in MotionTracking");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_MOVING_TOO_FAST) {
                Log.i(TAG, status + "Moving too fast");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FISHEYE_CAMERA_OVER_EXPOSED) {
                Log.i(TAG, status + "Fisheye Camera Over Exposed");
            }
            if (uxExceptionEvent.getType() == UxExceptionEvent.TYPE_FISHEYE_CAMERA_UNDER_EXPOSED) {
                Log.i(TAG, status + "Fisheye Camera Under Exposed");
            }
        }
    };

    @Override
    public void tick() {
        //this.publishImu();
        this.publishPointCloud();
    }

    @Override
    public void dispose() {
        this.timer.dispose();
        this.pcPublisher.dispose();
        this.imuPublisher.dispose();
        super.dispose();
    }
}
