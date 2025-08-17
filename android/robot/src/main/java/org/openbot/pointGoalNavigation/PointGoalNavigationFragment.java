package org.openbot.pointGoalNavigation;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import org.openbot.R;
import org.openbot.common.ControlsFragment;
import org.openbot.databinding.FragmentPointGoalNavigationBinding;
import org.openbot.main.MainViewModel;
import org.openbot.tflite.Model;
import org.openbot.tflite.Model.CLASS;
import org.openbot.tflite.Model.PATH_TYPE;
import org.openbot.tflite.Model.TYPE;
import org.openbot.tflite.Navigation;
import org.openbot.tflite.Network.Device;
import org.openbot.vehicle.Control;
import org.openbot.vehicle.Vehicle;
import org.openbot.vision.VisualOdometry;

import java.io.IOException;

import timber.log.Timber;

public class PointGoalNavigationFragment extends ControlsFragment {

    private MainViewModel mainViewModel;
    private Vehicle vehicle;
    private Handler handlerMain;
    private FragmentPointGoalNavigationBinding binding;
    private boolean isRunning = false;
    private Navigation navigationPolicy;

    private VisualOdometry vo; // Our custom Visual Odometry
    private float goalX, goalY; // target coordinates in VO world

    public PointGoalNavigationFragment() {
        // Required empty public constructor
    }

    public static PointGoalNavigationFragment newInstance() {
        PointGoalNavigationFragment fragment = new PointGoalNavigationFragment();
        return fragment;
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPointGoalNavigationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        vehicle = mainViewModel.getVehicle().getValue();

        handlerMain = new Handler(Looper.getMainLooper());
        vo = new VisualOdometry();

        showStartDialog();
    }

    @Override
    protected void processControllerKeyData(String command) {}

    @Override
    protected void processUSBData(String data) {}

    private void showStartDialog() {
        // simplified: just set goal directly for demo
        goalX = 1.0f; // e.g., 1m forward
        goalY = 0.0f; // e.g., same lateral position
        startDriving(goalX, goalY);
    }

    private void startDriving(float goalX, float goalY) {
        this.goalX = goalX;
        this.goalY = goalY;

        // Load navigation policy
        Model model = new Model(
                0,
                CLASS.NAVIGATION,
                TYPE.GOALNAV,
                "navigation.tflite",
                PATH_TYPE.ASSET,
                "networks/navigation.tflite",
                "160x90");

        try {
            navigationPolicy = new Navigation(requireActivity(), model, Device.CPU, 1);
        } catch (IOException e) {
            e.printStackTrace();
            showInfoDialog("Navigation policy could not be initialized.");
            return;
        }

        isRunning = true;

        // Start a loop to simulate frame updates
        handlerMain.post(frameUpdateRunnable);
    }

    private final Runnable frameUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;

            // Capture frame from camera (stub)
            Bitmap frame = captureCameraFrame(); // implement this with your camera preview
            if (frame != null) {
                vo.processFrame(frame);

                float dx = (float) (goalX - vo.getX());
                float dy = (float) (goalY - vo.getY());
                float distance = (float) Math.sqrt(dx * dx + dy * dy);

                if (distance < 0.15f) {
                    stop();
                    audioPlayer.playFromStringID(R.string.goal_reached);
                    showInfoDialog(getString(R.string.goal_reached));
                    return;
                }

                float deltaYaw = (float) Math.atan2(dy, dx) - (float) vo.getHeading();
                Control control = navigationPolicy.recognizeImage(
                        frame,
                        distance,
                        (float) Math.sin(deltaYaw),
                        (float) Math.cos(deltaYaw));

                Timber.d("control: (" + control.getLeft() + ", " + control.getRight() + ")");
                vehicle.setControl(control);
            }

            // Repeat loop
            handlerMain.postDelayed(this, 100); // 10 Hz update
        }
    };

    private void stop() {
        isRunning = false;
        vehicle.stopBot();
    }

    private Bitmap captureCameraFrame() {
        // TODO: replace with actual camera preview frame
        return Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888);
    }

    private void showInfoDialog(String message) {
        // Simplified info dialog
        Timber.i("INFO: " + message);
    }

    @Override
    public void onStart() {
        super.onStart();
        // no ARCore, so nothing to resume
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        stop();
    }

    @Override
    public void onStop() {
        super.onStop();
        stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stop();
    }
}
