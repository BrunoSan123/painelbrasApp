package com.sato.maskdetector;

import android.app.Activity;
import android.content.Context;

public interface MainActivityInterface {
    void setTemperature(double temperature);
    void showMessage(String message);
    Context getContext();
    int checkPermission(String permission, int myPid, int myUid);
    void runOnUiThread(Runnable action);
}
