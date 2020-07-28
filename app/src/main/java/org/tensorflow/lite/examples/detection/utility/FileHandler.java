/******************************************************************
 * @title FLIR THERMAL SDK
 * @file FileHandler.java
 * @Author FLIR Systems AB
 *
 * @brief Helper class to get a read / writeable file path
 *
 * Copyright 2019:    FLIR Systems
 * *******************************************************************/
package org.tensorflow.lite.examples.detection.utility;

import android.content.Context;

import java.io.File;

public class FileHandler {
    private final File filesDir;

    public FileHandler(Context applicationContext) {
        filesDir = applicationContext.getFilesDir();
    }

    public String getImageStoragePathStr() {
        return filesDir.getAbsolutePath();
    }

    public File getImageStoragePath() {
        return filesDir;
    }
}
