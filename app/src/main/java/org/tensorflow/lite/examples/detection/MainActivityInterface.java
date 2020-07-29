package org.tensorflow.lite.examples.detection;

import android.content.Context;
import android.graphics.Bitmap;

public interface MainActivityInterface {
    void setThermalInfo(Bitmap thermal, double temperature);
    Context getContext();
    Context getApplicationContext();
    int checkPermission(String permission, int myPid, int myUid);
}
