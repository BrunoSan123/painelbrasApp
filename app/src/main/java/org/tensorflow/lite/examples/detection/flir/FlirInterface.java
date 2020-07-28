package org.tensorflow.lite.examples.detection.flir;

import android.content.Context;
import android.util.Log;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.log.ThermalLog;


import org.tensorflow.lite.examples.detection.MainActivityInterface;
import org.tensorflow.lite.examples.detection.utility.PermissionHandler;

import java.io.IOException;

public class FlirInterface {
    private static final String TAG = "FlirInterface";
    private PermissionHandler permissionHandler;
    private CameraHandler cameraHandler;
    private Identity connectedIdentity = null;
    private UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();
    private boolean _isDiscovering = false;
    public boolean isDiscovering() {
        return _isDiscovering;
    }
    private Context context;
    private MainActivityInterface mainActivityInterface;
    private boolean alreadyAskedUSBPermissions = false;

    public enum CameraType {
        USB,
        SimulatorOne,
        SimulatorTwo
    }

    private static FlirInterface instance;
    public static FlirInterface getInstance(Context context, MainActivityInterface activity) {
        if (instance == null) {
            instance = new FlirInterface(context, activity);
        }
        return instance;
    }

    private FlirInterface(Context context, MainActivityInterface activityInterface) {
        this.context = context;
        this.mainActivityInterface = activityInterface;

        // Enable log if debug version
        //ThermalLog.LogLevel enableLoggingInDebug = BuildConfig.DEBUG ? ThermalLog.LogLevel.DEBUG : ThermalLog.LogLevel.NONE;
        ThermalSdkAndroid.init(context, ThermalLog.LogLevel.NONE);  // No log
        permissionHandler = new PermissionHandler(activityInterface);
        cameraHandler = new CameraHandler();
    }

    public void updateContext(Context context, MainActivityInterface activityInterface) {
        this.context = context;
        this.mainActivityInterface = activityInterface;
    }

    public boolean isConnectionLost() {
        return connectedIdentity == null && !cameraHandler.isConnected();
    }

    public void fixConnection(CameraType cameraType) {
        if (isConnectionLost()) {
            connect(cameraType);
        }
    }

    public boolean isConnected() {
        return connectedIdentity != null && cameraHandler.isConnected() && cameraHandler.isGrabbing();
    }

    public void startDiscovery() {
        if (_isDiscovering) {
            return;
        }

        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
    }

    public void stopDiscovery() {
        if (!_isDiscovering) {
            return;
        }
        cameraHandler.stopDiscovery(discoveryStatusListener);
    }

    public void connect(CameraType cameraType) {
        if (isConnected()) {
            Log.d(TAG, "FlirInterface is already connected");
            return;
        }

        if (cameraType == CameraType.USB) {
            try {
                connectedIdentity = cameraHandler.getFlirOne();
                // USB Permissions
                if (UsbPermissionHandler.isFlirOne(connectedIdentity)) {
                    if (!UsbPermissionHandler.hasFlirOnePermission(connectedIdentity, mainActivityInterface.getApplicationContext())) {
                        usbPermissionHandler.requestFlirOnePermisson(
                            connectedIdentity,
                            mainActivityInterface.getApplicationContext(),
                            permissionListener
                        );
                    }
                }
            } catch(Exception ex) {
                Log.e(TAG, ex.getMessage());
            }
        } else if (cameraType == CameraType.SimulatorOne) {
            connectedIdentity = cameraHandler.getCppEmulator();
            executeConnection();
        } else if (cameraType == CameraType.SimulatorTwo) {
            connectedIdentity = cameraHandler.getFlirOneEmulator();
            executeConnection();
        } else {
            connectedIdentity = null;
            Log.e(TAG, "No compatible camera found for connection.");
        }
    }

    public void disconnect() {
        if (isConnected()) {
            connectedIdentity = null;
            cameraHandler.disconnect();
        }
    }

    private void executeConnection() {
        if (connectedIdentity == null) {
            Log.e(TAG, "connectedIdentity is null. No camera available.");
            return;
        }

        try {
            cameraHandler.connect(connectedIdentity, connectionStatusListener);
            cameraHandler.startStream(streamDataListener);
        }
        catch(IOException ioex) {
            Log.e(TAG, ioex.getMessage());
        }
    }

    // Listeners
    private UsbPermissionHandler.UsbPermissionListener permissionListener = new UsbPermissionHandler.UsbPermissionListener() {
        @Override
        public void permissionGranted(Identity identity) {
            alreadyAskedUSBPermissions = true;
            executeConnection();
        }
        @Override
        public void permissionDenied(Identity identity) {
            Log.i(TAG, "Permission was denied for identity " + identity);
        }
        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
            Log.i(TAG, "Error when asking for permission for FLIR ONE, error: " + errorType + " identity: " + identity);
        }
    };
    private DiscoveryEventListener cameraDiscoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(Identity identity) {
            Log.d(TAG, "onCameraFound identity: " + identity);
            cameraHandler.add(identity);
        }

        @Override
        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
            stopDiscovery();
            Log.d(TAG, "onDiscoveryError communicationInterface: " + communicationInterface + " errorCode:" + errorCode);
        }
    };
    private CameraHandler.DiscoveryStatus discoveryStatusListener = new CameraHandler.DiscoveryStatus() {
        @Override
        public void started() {
            _isDiscovering = true;
            Log.d(TAG, "discovery started");
        }

        @Override
        public void stopped() {
            Log.d(TAG, "discovery stopped");
            _isDiscovering = false;
        }
    };
    private ConnectionStatusListener connectionStatusListener = new ConnectionStatusListener() {
        @Override
        public void onDisconnected(@org.jetbrains.annotations.Nullable ErrorCode errorCode) {
            Log.d(TAG, "onDisconnected errorCode:" + errorCode);
            connectedIdentity = null;
        }
    };

    private final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {
        @Override
        public void streamTempData(double tempAtCenter) {
            mainActivityInterface.setTemperature(tempAtCenter);
        }
    };
}
