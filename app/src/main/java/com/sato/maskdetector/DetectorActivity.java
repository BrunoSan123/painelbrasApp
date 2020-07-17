/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sato.maskdetector;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.media.AudioManager;
import android.media.ImageReader.OnImageAvailableListener;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import com.sato.maskdetector.env.*;
import com.sato.maskdetector.customview.OverlayView;
import com.sato.maskdetector.flir.FlirInterface;
import com.sato.maskdetector.tflite.*;
import com.sato.maskdetector.tracking.MultiBoxTracker;


public class DetectorActivity extends CameraActivity implements OnImageAvailableListener, MainActivityInterface {
    private static final Logger LOGGER = new Logger();
    // Face Mask
    private static final int TF_OD_API_INPUT_SIZE = 224;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "mask_detector.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/mask_labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(800, 600);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;
    private Classifier detector;
    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap newrgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private boolean computingDetection = false;
    private long timestamp = 0;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private BorderedText borderedText;
    // Face detector
    private FaceDetector faceDetector;
    // here the preview image is drawn in portrait way
    private Bitmap portraitBmp = null;
    // here the face is cropped and drawn
    private Bitmap faceBmp = null;
    // Flir Manager
    //private FlirManager flirManager;
    private FlirInterface flirInterface;
    // Dynamic Widgets
    TextView tempView;
    Button btnConnectFlir;
    Button btnResultado;
    Button btnTopBar;
    Button btnBottomBar;

    DecimalFormat precision = new DecimalFormat("0.00");
    private double temperature = 0.0;

    // Text-To-Speech
    TextToSpeech tts;
    boolean didSpeak = false;
    // Beep
    private boolean didBeep = false;

    // Default for no enter
    private boolean hasMask = true;
    private boolean tempIsHigh = true;
    private boolean hasFace = false;
    private Timer stateCheckTimer;

    // Beep Media Player
    //MediaPlayer mediaPlayer;
    ToneGenerator dtmf;

    //=====================================================================
    // TODO: CONFIGURE AQUI O TIPO DE CAMERA A SER USADA: USB OU EMULADOR
    //=====================================================================
    //FlirInterface.CameraType cameraType = FlirInterface.CameraType.SimulatorOne;    // Testing
    FlirInterface.CameraType cameraType = FlirInterface.CameraType.USB;           // Production

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        btnConnectFlir = findViewById(R.id.btnConnectFlir);
        btnResultado = findViewById(R.id.btnResultado);
        btnTopBar = findViewById(R.id.buttonTop);
        btnBottomBar = findViewById(R.id.buttonBottom);

        // Beep
        dtmf = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // Real-time contour detection of multiple faces
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .build();


        faceDetector = FaceDetection.getClient(options);

        // Initialize TTS
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.ERROR) {
                tts.setLanguage(Locale.forLanguageTag("pt-BR"));
                //tts.setSpeechRate(0.8f);
            }
        });

        // Initialize Flir
        //flirManager = new FlirManager(this, showMessage, this);
        flirInterface = FlirInterface.getInstance(this, this);
        btnConnectFlir.setOnClickListener(v -> {
            flirInterface.connect(cameraType);  // /|\ configure cameraType lá em cima
            btnConnectFlir.setVisibility(View.INVISIBLE);
        });

        // reset
        stateCheckTimer = new Timer();
        resetReadings();
    }

    private void showOkDialog(String title, String content) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(content);
        builder.setPositiveButton("OK", (dialog, which) -> {
            // Just dismiss
        });
        builder.create().show();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        try {
            if (!flirInterface.isConnected() && !flirInterface.isDiscovering()) {
                flirInterface.startDiscovery();
                if (shouldFixFLIR) {
                    flirInterface.updateContext(this, this);
                    flirInterface.fixConnection(cameraType);
                } else {
                    btnConnectFlir.setVisibility(View.VISIBLE);
                }
            }
        } catch(Exception ex) {
            Log.e("DetectorActiviy", ex.getMessage());
        }
        stateCheckTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    manageAlerts();
                    // Check if connect button should be visible or not
                    if (flirInterface.isConnected()) {
                        btnConnectFlir.setVisibility(View.INVISIBLE);
                    } else {
                        btnConnectFlir.setVisibility(View.VISIBLE);
                    }
                });
            }
        }, 0, 2000);
    }

    @Override
    public synchronized void onPause() {
        if (flirInterface.isConnected()) {
            flirInterface.disconnect();
        }
        if (flirInterface.isDiscovering()) {
            flirInterface.stopDiscovery();
        }
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch(Exception ex) {
                Log.e("SPEECH PAUSE", ex.getMessage());
            }
        }
        super.onPause();
    }

    @Override
    public void setTemperature(double temperature) {
        this.temperature = temperature;
        String tmpString = String.format("%sºC", precision.format(temperature));
        if (temperature > 38.2) {
            tempIsHigh = true;
        } else {
            tempIsHigh = false;
        }
        runOnUiThread(() -> btnTopBar.setText(tmpString));
    }

    @Override
    public void showMessage(String message) {
        // TODO: Implement this method
    }

    @Override
    public Context getContext() {
        return this;
    }

    private void manageAlerts() {
        if (!hasFace) {
            return;
        }
        btnTopBar.setVisibility(View.VISIBLE);
        btnBottomBar.setVisibility(View.VISIBLE);
        btnResultado.setVisibility(View.VISIBLE);

        // Can enter?
        if (tempIsHigh || !hasMask) {
            btnTopBar.setBackground(getDrawable(R.drawable.topbar_red));
            btnBottomBar.setBackground(getDrawable(R.drawable.bottombar_red));
            btnBottomBar.setText("Não permitido");
        } else {
            btnTopBar.setBackground(getDrawable(R.drawable.topbar_green));
            btnBottomBar.setBackground(getDrawable(R.drawable.bottombar_green));
            btnBottomBar.setText("Entrada permitida");
        }

        // Button alert
        if (hasMask) {
            btnResultado.setText("Com máscara");
            btnResultado.setBackground(getDrawable(R.drawable.button_green));
        } else {
            btnResultado.setText("Sem máscara");
            btnResultado.setBackground(getDrawable(R.drawable.button_red));
            beepOnce();
        }
        speakOnce();
    }

    void speakOnce() {
        // Speak only when Flir enabled.
        if (temperature == 0) {
            return;
        }
        try {
            if (!didSpeak && hasFace) {
                speakTemp(temperature);
                didSpeak = true;
            }
        } catch(Exception ex) {
            Log.e("SPEECH", ex.getMessage());
        }
    }

    void beepOnce() {
        try {
            if (!didBeep && hasFace) {
                dtmf.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300);
                didBeep = true;
            }
        } catch (Exception ex) {
            Log.e("BEEP", ex.getMessage());
        }
    }

    void resetReadings() {
        didSpeak = false;
        didBeep = false;

        btnTopBar.setVisibility(View.INVISIBLE);
        btnBottomBar.setVisibility(View.INVISIBLE);
        btnResultado.setVisibility(View.INVISIBLE);
    }

    private void speakTemp(double temp) {
        if (tts == null) {
            return;
        }
        if (!tts.isSpeaking()) {
            if (temp > 38.2) {
                tts.speak("Temperatura alta.", TextToSpeech.QUEUE_FLUSH, null, "temp");
            } else {
                tts.speak("Temperatura normal.", TextToSpeech.QUEUE_FLUSH, null, "temp");
            }
        }
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            //cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);

        int targetW, targetH;
        if (sensorOrientation == 90 || sensorOrientation == 270) {
            targetH = previewWidth;
            targetW = previewHeight;
        }
        else {
            targetW = previewWidth;
            targetH = previewHeight;
        }
        int cropW = (int) (targetW / 2.0);
        int cropH = (int) (targetH / 2.0);

        croppedBitmap = Bitmap.createBitmap(cropW, cropH, Config.ARGB_8888);

        portraitBmp = Bitmap.createBitmap(targetW, targetH, Config.ARGB_8888);
        faceBmp = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropW, cropH,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(canvas -> {
            String title = tracker.getFirstTrackedTitle();

            if (title.equals("mask")) {
                hasMask = true;
            } else {
                hasMask = false;
            }
        });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);
        // This is needed only for LegacyCamera. Since we forced Camera2, we don't need this anymore.
//        if (getCameraFacing() == CameraCharacteristics.LENS_FACING_FRONT) {
//            Matrix rgbMatrix = new Matrix();
//            rgbMatrix.postRotate(180);
//            newrgbFrameBitmap = Bitmap.createBitmap(rgbFrameBitmap, 0, 0, rgbFrameBitmap.getWidth(), rgbFrameBitmap.getHeight(), rgbMatrix, true);
//            rgbFrameBitmap = newrgbFrameBitmap;
//        }
        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        InputImage image = InputImage.fromBitmap(croppedBitmap, 0);
        faceDetector.process(image)
            .addOnSuccessListener(faces -> {
                if (faces.size() == 0) {
                    // No faces detected
                    hasFace = false;
                    resetReadings();
                    updateResults(currTimestamp, new LinkedList<>());
                    return;
                }
                else {
                    hasFace = true;
                }
                runInBackground(() -> onFacesDetected(currTimestamp, faces));
            });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }

    // Face Mask Processing
    private Matrix createTransform(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation) {

        Matrix matrix = new Matrix();
        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                LOGGER.w("Rotation of %d % 90 != 0", applyRotation);
            }

            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }

        if (applyRotation != 0) {

            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        }

        return matrix;
    }
    private void updateResults(long currTimestamp, final List<Classifier.Recognition> mappedRecognitions) {
        tracker.trackResults(mappedRecognitions, currTimestamp);
        trackingOverlay.postInvalidate();
        computingDetection = false;
    }

    private void onFacesDetected(long currTimestamp, List<Face> faces) {
        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
        final Canvas canvas = new Canvas(cropCopyBitmap);
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(2.0f);

        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
        switch (MODE) {
            case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
        }

        final List<Classifier.Recognition> mappedRecognitions =
                new LinkedList<Classifier.Recognition>();

        // Note this can be done only once
        int sourceW = rgbFrameBitmap.getWidth();
        int sourceH = rgbFrameBitmap.getHeight();
        int targetW = portraitBmp.getWidth();
        int targetH = portraitBmp.getHeight();
        Matrix transform = createTransform(
                sourceW,
                sourceH,
                targetW,
                targetH,
                sensorOrientation);
        final Canvas cv = new Canvas(portraitBmp);

        // draws the original image in portrait mode.
        cv.drawBitmap(rgbFrameBitmap, transform, null);

        final Canvas cvFace = new Canvas(faceBmp);

        boolean saved = false;

        for (Face face : faces) {
            LOGGER.i("FACE" + face.toString());
            LOGGER.i("Running detection on face " + currTimestamp);

            final RectF boundingBox = new RectF(face.getBoundingBox());

            //final boolean goodConfidence = result.getConfidence() >= minimumConfidence;
            final boolean goodConfidence = true; //face.get;
            if (boundingBox != null && goodConfidence) {

                // maps crop coordinates to original
                cropToFrameTransform.mapRect(boundingBox);

                // maps original coordinates to portrait coordinates
                RectF faceBB = new RectF(boundingBox);
                transform.mapRect(faceBB);

                // translates portrait to origin and scales to fit input inference size
                //cv.drawRect(faceBB, paint);
                float sx = ((float) TF_OD_API_INPUT_SIZE) / faceBB.width();
                float sy = ((float) TF_OD_API_INPUT_SIZE) / faceBB.height();
                Matrix matrix = new Matrix();
                matrix.postTranslate(-faceBB.left, -faceBB.top);
                matrix.postScale(sx, sy);

                cvFace.drawBitmap(portraitBmp, matrix, null);

                String label = "";
                float confidence = -1f;
                Integer color = Color.BLUE;

                final long startTime = SystemClock.uptimeMillis();
                final List<Classifier.Recognition> resultsAux = detector.recognizeImage(faceBmp);
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                if (resultsAux.size() > 0) {

                    Classifier.Recognition result = resultsAux.get(0);

                    float conf = result.getConfidence();
                    if (conf >= 0.6f) {

                        confidence = conf;
                        label = result.getTitle();
                        if (result.getId().equals("0")) {
                            color = Color.GREEN;
                        }
                        else {
                            color = Color.RED;
                        }
                    }
                }

                if (getCameraFacing() == CameraCharacteristics.LENS_FACING_FRONT) {

                    // camera is frontal so the image is flipped horizontally
                    // flips horizontally
                    Matrix flip = new Matrix();
                    if (sensorOrientation == 90 || sensorOrientation == 270) {
                        flip.postScale(1, -1, previewWidth / 2.0f, previewHeight / 2.0f);
                    }
                    else {
                        flip.postScale(-1, 1, previewWidth / 2.0f, previewHeight / 2.0f);
                    }
                    //flip.postScale(1, -1, targetW / 2.0f, targetH / 2.0f);
                    flip.mapRect(boundingBox);
                }

                final Classifier.Recognition result = new Classifier.Recognition(
                        "0", label, confidence, boundingBox);

                result.setColor(color);
                result.setLocation(boundingBox);
                mappedRecognitions.add(result);
            }
        }
        updateResults(currTimestamp, mappedRecognitions);
    }

    /**
     * Show message on the screen
     */
    public interface ShowMessage {
        void show(String message);
    }

    private ShowMessage showMessage = new ShowMessage() {
        @Override
        public void show(String message) {
            Toast.makeText(DetectorActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    };

}
