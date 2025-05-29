package org.openbot.main;

import static org.openbot.utils.Constants.DEVICE_ACTION_DATA_RECEIVED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.Locale;
import java.util.Objects;
import org.openbot.OpenBotApplication;
import org.openbot.R;
import org.openbot.utils.Constants;
import org.openbot.vehicle.UsbConnection;
import org.openbot.vehicle.Vehicle;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

  private MainViewModel viewModel;
  private BroadcastReceiver localBroadcastReceiver;
  private Vehicle vehicle;
  private LocalBroadcastManager localBroadcastManager;
  private BottomNavigationView bottomNavigationView;
  private NavController navController;

  // TextToSpeech field
  private TextToSpeech tts;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    viewModel = new ViewModelProvider(this).get(MainViewModel.class);
    vehicle = OpenBotApplication.vehicle;
    bottomNavigationView = findViewById(R.id.bottomNavigationView);
    bottomNavigationView.setSelectedItemId(R.id.home);
    viewModel.setVehicle(vehicle);

    localBroadcastReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action != null) {
              switch (action) {
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                  if (!vehicle.isUsbConnected()) {
                    vehicle.connectUsb();
                    viewModel.setUsbStatus(vehicle.isUsbConnected());
                  }
                  Timber.i("USB device attached");
                  break;

                case UsbConnection.ACTION_USB_PERMISSION:
                  synchronized (this) {
                    UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                      if (usbDevice != null) {
                        if (!vehicle.isUsbConnected()) {
                          vehicle.connectUsb();
                        }
                        viewModel.setUsbStatus(vehicle.isUsbConnected());
                        Timber.i("USB device attached");
                      }
                    }
                  }
                  break;

                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                  vehicle.disconnectUsb();
                  viewModel.setUsbStatus(vehicle.isUsbConnected());
                  Timber.i("USB device detached");
                  break;

                case DEVICE_ACTION_DATA_RECEIVED:
                  viewModel.setDeviceData(intent.getStringExtra("data"));
                  break;
              }
            }
          }
        };

    IntentFilter localIntentFilter = new IntentFilter();
    localIntentFilter.addAction(DEVICE_ACTION_DATA_RECEIVED);
    localIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
    localIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
    localIntentFilter.addAction(UsbConnection.ACTION_USB_PERMISSION);

    localBroadcastManager = LocalBroadcastManager.getInstance(this);
    localBroadcastManager.registerReceiver(localBroadcastReceiver, localIntentFilter);

    registerReceiver(localBroadcastReceiver, localIntentFilter);

    NavHostFragment navHostFragment =
        (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
    navController = navHostFragment.getNavController();
    AppBarConfiguration appBarConfiguration =
        new AppBarConfiguration.Builder(navController.getGraph()).build();
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    bottomNavigationView.setOnItemReselectedListener(item -> {});

    NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);
    NavigationUI.setupWithNavController(bottomNavigationView, navController);

    navController.addOnDestinationChangedListener(
        (controller, destination, arguments) -> {
          if (destination.getId() == R.id.mainFragment
              || destination.getId() == R.id.settingsFragment
              || destination.getId() == R.id.usbFragment
              || destination.getId() == R.id.projectsFragment
              || destination.getId() == R.id.profileFragment) {
            toolbar.setVisibility(View.VISIBLE);
            bottomNavigationView.setVisibility(View.VISIBLE);
          } else {
            toolbar.setVisibility(View.GONE);
            bottomNavigationView.setVisibility(View.GONE);
          }

          Menu menu = toolbar.getMenu();
          if (destination.getId() == R.id.projectsFragment) {
            menu.findItem(R.id.settingsFragment).setVisible(false);
            menu.findItem(R.id.barCodeScannerFragment).setVisible(true);
          } else {
            menu.findItem(R.id.barCodeScannerFragment).setVisible(false);
            if (vehicle.getConnectionType().equals("Bluetooth")) {
              menu.findItem(R.id.bluetoothFragment).setVisible(true);
            }
            menu.findItem(R.id.settingsFragment).setVisible(true);
          }
        });

    // Initialize TTS
    tts = new TextToSpeech(this, status -> {
      if (status == TextToSpeech.SUCCESS) {
        int result = tts.setLanguage(Locale.US);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
          Timber.e("TTS: Language not supported.");
        } else {
          speak("Hello, I am OpenBot and I am ready.");
        }
      } else {
        Timber.e("TTS: Initialization failed.");
      }
    });
  }

  private void speak(String text) {
    if (tts != null) {
      tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_items, menu);
    int currentDestinationId =
        Objects.requireNonNull(navController.getCurrentDestination()).getId();
    if (currentDestinationId == R.id.projectsFragment) {
      menu.findItem(R.id.barCodeScannerFragment).setVisible(true);
      menu.findItem(R.id.settingsFragment).setVisible(false);
    } else {
      menu.findItem(R.id.barCodeScannerFragment).setVisible(false);
    }
    if (vehicle.getConnectionType().equals("Bluetooth")) {
      menu.findItem(R.id.usbFragment).setVisible(false);
      menu.findItem(R.id.bluetoothFragment).setVisible(true);
    } else if (vehicle.getConnectionType().equals("USB")) {
      menu.findItem(R.id.usbFragment).setVisible(true);
      menu.findItem(R.id.bluetoothFragment).setVisible(false);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
    if (item.getItemId() == R.id.barCodeScannerFragment) {
      navController.navigate(R.id.barCodeScannerFragment);
      return true;
    }
    return NavigationUI.onNavDestinationSelected(item, navController)
        || super.onOptionsItemSelected(item);
  }

  @Override
  public boolean dispatchGenericMotionEvent(MotionEvent event) {
    if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        && event.getAction() == MotionEvent.ACTION_MOVE) {
      Bundle bundle = new Bundle();
      bundle.putParcelable(Constants.DATA, event);
      getSupportFragmentManager().setFragmentResult(Constants.GENERIC_MOTION_EVENT, bundle);
      return true;
    }
    return super.dispatchGenericMotionEvent(event);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    Bundle bundle = new Bundle();
    bundle.putParcelable(Constants.DATA_CONTINUOUS, event);
    getSupportFragmentManager().setFragmentResult(Constants.KEY_EVENT_CONTINUOUS, bundle);
    if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
      bundle.putParcelable(Constants.DATA, event);
      getSupportFragmentManager().setFragmentResult(Constants.KEY_EVENT, bundle);
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

  @Override
  public synchronized void onDestroy() {
    if (localBroadcastManager != null) {
      localBroadcastManager.unregisterReceiver(localBroadcastReceiver);
      localBroadcastManager = null;
    }
    unregisterReceiver(localBroadcastReceiver);
    if (localBroadcastReceiver != null) localBroadcastReceiver = null;
    if (!isChangingConfigurations()) vehicle.disconnectUsb();

    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }

    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
  }
}
