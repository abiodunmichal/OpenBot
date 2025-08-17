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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import org.openbot.utils.Constants;
import org.openbot.utils.PermissionUtils;
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
    private boolean isPermissionRequested = false;

    private Navigation navigationPolicy;
    private VisualOdometry vo;

    private float goalX;
    private float goalZ;

    private Bitmap currentCameraFrame; // Assign this from your camera preview

    public PointGoalNavigationFragment() {
        // Required empty public constructor
    }

    public static PointGoalNavigationFragment newInstance() {
        PointGoalNavigationFragment fragment = new PointGoalNavigationFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

    // Call this in your camera frame callback
    public void processFrame(Bitmap frame) {
        currentCameraFrame = frame;
        vo.processFrame(frame);

        if (!isRunning) return;

        float dx = goalX - (float) vo.getX();
        float dz = goalZ - (float) vo.getY();
        float distance = (float) Math.sqrt(dx * dx + dz * dz);

        if (distance < 0.15f) {
            stop();
            audioPlayer.playFromStringID(R.string.goal_reached);
            showInfoDialog(getString(R.string.goal_reached));
        } else {
            float deltaYaw = (float) Math.atan2(dz, dx) - (float) vo.getHeading();

            Control control = navigationPolicy.recognizeImage(
                    currentCameraFrame,
                    distance,
                    (float) Math.sin(deltaYaw),
                    (float) Math.cos(deltaYaw));

            vehicle.setControl(control);
            Timber.d("control: (" + control.getLeft() + ", " + control.getRight() + ")");
        }
    }

    private void stop() {
        vehicle.stopBot();
        isRunning = false;
    }

    private void startDriving(float goalX, float goalZ) {
        this.goalX = goalX;
        this.goalZ = goalZ;

        Model model =
                new Model(
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
    }

    private void showStartDialog() {
        if (getChildFragmentManager().findFragmentByTag(SetGoalDialogFragment.TAG) == null) {
            SetGoalDialogFragment dialog = SetGoalDialogFragment.newInstance();
            dialog.setCancelable(false);
            dialog.show(getChildFragmentManager(), SetGoalDialogFragment.TAG);
        }

        getChildFragmentManager()
                .setFragmentResultListener(
                        SetGoalDialogFragment.TAG,
                        getViewLifecycleOwner(),
                        (requestKey, result) -> {
                            Boolean start = result.getBoolean("start");

                            if (start) {
                                Float forward = result.getFloat("forward");
                                Float left = result.getFloat("left");

                                startDriving(-left, -forward);
                            } else {
                                requireActivity().onBackPressed();
                            }
                        });
    }

    private void showInfoDialog(String message) {
        if (getChildFragmentManager().findFragmentByTag(InfoDialogFragment.TAG) == null) {
            InfoDialogFragment dialog = InfoDialogFragment.newInstance(message);
            dialog.setCancelable(false);
            dialog.show(getChildFragmentManager(), InfoDialogFragment.TAG);
        }

        getChildFragmentManager()
                .setFragmentResultListener(
                        InfoDialogFragment.TAG,
                        getViewLifecycleOwner(),
                        (requestKey, result) -> {
                            Boolean restart = result.getBoolean("restart");

                            if (restart) {
                                showStartDialog();
                            } else {
                                requireActivity().onBackPressed();
                            }
                        });
    }

    @Override
    protected void processControllerKeyData(String command) {}

    @Override
    protected void processUSBData(String data) {}
}
