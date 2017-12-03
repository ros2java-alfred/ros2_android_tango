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

import android.os.Bundle;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

//import org.rajawali3d.view.SurfaceView; // 1.1
import org.rajawali3d.surface.RajawaliSurfaceView; // 1.0
import org.ros2.android.core.BaseRosActivity;
import org.ros2.android.tango.ux.rajawali.TangoPointCloudRajawaliRenderer;
import org.ros2.android.tango.ux.TangoPointCloudRenderer;

public class MainActivity extends BaseRosActivity {

    private TangoNode node;
    private TangoPointCloudRenderer renderer;

    private TextView pointCountTextView;
    private TextView averageZTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);

        this.pointCountTextView = this.findViewById(R.id.point_count_textview);
        this.averageZTextView   = this.findViewById(R.id.average_z_textview);

        // GLSurfaceView surfaceView;       // For native OpenGL engine
        RajawaliSurfaceView surfaceView = this.findViewById(R.id.gl_surface_view); // For Rajawali engine

        //this.renderer = new TangoPointCloudOpenGLRenderer(this, this.surfaceView);
        this.renderer = new TangoPointCloudRajawaliRenderer(this, surfaceView);
        this.renderer.setupRenderer();

        this.node = new TangoNode(this, "tango", this.renderer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.node.onResume(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public void onFirstPersonClicked(View view) {
        this.renderer.setFirstPersonView();
    }

    public void onTopDownClicked(View view) {
        this.renderer.setTopDownView();
    }

    public void onThirdPersonClicked(View view) {
        this.renderer.setThirdPersonView();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        this.renderer.onTouchEvent(event);
        return true;
    }

    /**
     * Query the display's rotation.
     */
    private void setDisplayRotation() {
        Display display = this.getWindowManager().getDefaultDisplay();
        this.renderer.setDisplayRotation(display.getRotation());
    }
}
