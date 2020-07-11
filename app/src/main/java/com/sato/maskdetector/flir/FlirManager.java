package com.sato.maskdetector.flir;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.live.remote.OnCompletion;
import com.flir.thermalsdk.log.ThermalLog;
import com.sato.maskdetector.BuildConfig;
import com.sato.maskdetector.DetectorActivity;
import com.sato.maskdetector.utility.PermissionHandler;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

public class FlirManager {
    private static final String TAG = "FlirManager";
    private PermissionHandler permissionHandler;
    private CameraHandler cameraHandler;
    private Identity connectedIdentity = null;
    private LinkedBlockingQueue<FrameDataHolder> framesBuffer = new LinkedBlockingQueue(21);
    private UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();
    private DetectorActivity mainActivity;
    private ImageView msxImage;
    private ImageView photoImage;
    private double temperature;

    public FlirManager(Context context, DetectorActivity.ShowMessage showMessage, DetectorActivity mainActivity) {
        this.mainActivity = mainActivity;

        // Enable log if debug version
        ThermalLog.LogLevel enableLoggingInDebug = BuildConfig.DEBUG ? ThermalLog.LogLevel.DEBUG : ThermalLog.LogLevel.NONE;
        ThermalSdkAndroid.init(context, enableLoggingInDebug);
        permissionHandler = new PermissionHandler(showMessage, this.mainActivity);
        cameraHandler = new CameraHandler();
    }

    private void updateConnectionText(Identity identity, String status) {
        String deviceId = identity != null ? identity.deviceId : "";
        Log.d(TAG, "Connection Update: " + deviceId + " is " + status);
    }

    public void startDiscovery() {
        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
    }

    public void stopDiscovery() {
        cameraHandler.stopDiscovery(discoveryStatusListener);
    }

    public void connectFlirOne() {
        connect(cameraHandler.getFlirOne());
    }

    public void connectSimulatorOne() {
        connect(cameraHandler.getCppEmulator());
    }

    public void connectSimulatorTwo() {
        connect(cameraHandler.getFlirOneEmulator());
    }



    /**
     * Connect to a Camera
     */
    private void connect(Identity identity) {
        if (connectedIdentity != null) {
            cameraHandler.stopDiscovery(discoveryStatusListener);
            Log.d(TAG, "connect(), we only support one camera connection at the time");
            return;
        }

        if (identity == null) {
            Log.d(TAG, "connect(), can't connect, no camera available");
            return;
        }

        connectedIdentity = identity;

        //updateConnectionText(identity, "CONNECTING");
        //IF your using "USB_DEVICE_ATTACHED" and "usb-device vendor-id" in the Android Manifest
        // you don't need to request permission, see documentation for more information
        if (UsbPermissionHandler.isFlirOne(identity)) {
            try {
                usbPermissionHandler.requestFlirOnePermisson(identity, mainActivity, permissionListener);
            } catch(Exception ex) {
                Log.d("FlirManager", ex.getMessage());
            }
        } else {
            doConnect(identity);
        }

    }

    private void doConnect(Identity identity) {
        new Thread(() -> {
            try {
                cameraHandler.connect(identity, connectionStatusListener);
                mainActivity.runOnUiThread(() -> {
                    updateConnectionText(identity, "CONNECTED");
                    cameraHandler.startStream(streamDataListener);
                });
            } catch (IOException e) {
                mainActivity.runOnUiThread(() -> {
                    Log.d(TAG, "Could not connect: " + e);
                    updateConnectionText(identity, "DISCONNECTED");
                });
            }
        }).start();
    }

    /**
     * Disconnect to a camera
     */
    public void disconnect() {
        updateConnectionText(connectedIdentity, "DISCONNECTING");
        connectedIdentity = null;
        Log.d(TAG, "disconnect() called with: connectedIdentity = [" + connectedIdentity + "]");
        new Thread(() -> {
            cameraHandler.disconnect();
            mainActivity.runOnUiThread(() -> {
                //updateConnectionText(null, "DISCONNECTED");
            });
        }).start();
    }

    // Listeners
    private UsbPermissionHandler.UsbPermissionListener permissionListener = new UsbPermissionHandler.UsbPermissionListener() {
        @Override
        public void permissionGranted(Identity identity) {
            doConnect(identity);
        }

        @Override
        public void permissionDenied(Identity identity) {
            Toast.makeText(mainActivity, "Permission was denied for identity " + identity, Toast.LENGTH_LONG).show();
        }

        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
            Toast.makeText(mainActivity, "Error when asking for permission for FLIR ONE, error:"+errorType+ " identity: " +identity, Toast.LENGTH_LONG).show();
        }
    };

    /**
     * Callback for discovery status, using it to update UI
     */
    private CameraHandler.DiscoveryStatus discoveryStatusListener = new CameraHandler.DiscoveryStatus() {
        @Override
        public void started() {
            Log.d(TAG, "discovery started");
        }

        @Override
        public void stopped() {
            Log.d(TAG, "discovery stopped");
        }
    };

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     */
    private ConnectionStatusListener connectionStatusListener = new ConnectionStatusListener() {
        @Override
        public void onDisconnected(@org.jetbrains.annotations.Nullable ErrorCode errorCode) {
            Log.d(TAG, "onDisconnected errorCode:" + errorCode);

            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "disconnected");
                }
            });
        }
    };

    /**
     * Camera Discovery thermalImageStreamListener, is notified if a new camera was found during a active discovery phase
     */
    private DiscoveryEventListener cameraDiscoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(Identity identity) {
            Log.d(TAG, "onCameraFound identity:" + identity);
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cameraHandler.add(identity);
                }
            });
        }

        @Override
        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
            Log.d(TAG, "onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);

            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopDiscovery();
                    Log.d(TAG, "onDiscoveryError communicationInterface: " + communicationInterface + " errorCode:" + errorCode);
                    //MainActivity.this.showMessage.show("onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
                }
            });
        }
    };
    private final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {

        @Override
        public void images(FrameDataHolder dataHolder) {
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    msxImage.setImageBitmap(dataHolder.msxBitmap);
                    photoImage.setImageBitmap(dataHolder.dcBitmap);
                }
            });
        }

        @Override
        public void images(Bitmap msxBitmap, Bitmap dcBitmap, double tempAtCenter) {
            try {
                framesBuffer.put(new FrameDataHolder(msxBitmap,dcBitmap));
            } catch (InterruptedException e) {
                //if interrupted while waiting for adding a new item in the queue
                Log.e(TAG,"images(), unable to add incoming images to frames buffer, exception:"+e);
            }

            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //Log.d(TAG,"framebuffer size:"+framesBuffer.size());
//                    FrameDataHolder poll = framesBuffer.poll();
//                    msxImage.setImageBitmap(poll.msxBitmap);
//                    photoImage.setImageBitmap(poll.dcBitmap);

                    // Convert temp from kelvin to degree celsius
                    temperature = tempAtCenter - 273.15;
                    // Set temperature on View
                    mainActivity.setTemperatureText(temperature);
                    // testing
                    //mainActivity.setTemperatureText(39.0);
                }
            });
        }
    };

}
