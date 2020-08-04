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

package org.tensorflow.lite.examples.detection;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.IOException;
import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import org.tensorflow.lite.examples.detection.OpIO.Recognition;
import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.flir.FlirInterface;
import org.tensorflow.lite.examples.detection.tflite.SimilarityClassifier;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tflitemask.Classifier;
import org.tensorflow.lite.examples.detection.tflitemask.TFLiteObjectDetectionAPIModelMask;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;
import org.tensorflow.lite.examples.detection.WebService.Service;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener, MainActivityInterface {
    private static final Logger LOGGER = new Logger();

    Bitmap crop = null;
    // MobileFaceNet
    private static final int TF_OD_API_INPUT_SIZE = 112;
    private static final int TF_OD_API_INPUT_SIZE2 = 224;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "mobile_face_net.tflite";

    private static final String TF_OD_API_MASKMODEL_FILE = "mask_detector.tflite";
    //private static final String TF_OD_API_MASKMODEL_FILE = "mask_model2.tflite";

    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
    private static final String TF_OD_API_LABELS_FILE2 = "file:///android_asset/mask_labelmap.txt";
    //private static final String TF_OD_API_LABELS_FILE2 = "file:///android_asset/mask_labels2.txt";

    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    //private static final int CROP_SIZE = 320;
    //private static final Size CROP_SIZE = new Size(320, 320);


    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private SimilarityClassifier detector;
    private Classifier detectorMask;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;
    private ImageView msxImage = null;

    private boolean computingDetection = false;
    private boolean addPending = false;
    //private boolean adding = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    //private Matrix cropToPortraitTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;

    // Face detector
    private FaceDetector faceDetector;

    // here the preview image is drawn in portrait way
    private Bitmap portraitBmp = null;
    // here the face is cropped and drawn
    private Bitmap faceBmp = null, faceBmp2 = null;

    private FirebaseAuth auth;

    Button btnResultado;
    Button btnTopBar;
    Button btnBottomBar;

    DecimalFormat precision = new DecimalFormat("0.00");
    private double temperature = 0.0;

    // Text-To-Speech
    TextToSpeech tts;

    // Default for no enter
    private boolean hasFace = false;
    private boolean hasMask = false;
    private boolean isRecognized = false;
    private boolean didSpeak = false;
    private boolean didBeep = false;

    private Timer stateCheckTimer;
    private FlirInterface flirInterface;

    private ArrayList<Funcionario> people;

    // Estágios de Detecção
    private enum DetectionStages {
        FaceDetection,
        FaceRecognition,
        MaskDetection,
        TemperatureRead,
        Freedom,
        Jail
    };
    private DetectionStages currentStage;
    private String detectedName;

    // Usado no delay entre uma detecção e outra
    private long delayMillis = 0L;
    private long maskDelayMillis = 0L;

    // Beep Generatorr
    ToneGenerator dtmf;

    //=====================================================================
    // TODO: CONFIGURE AQUI O TIPO DE CAMERA A SER USADA: USB OU EMULADOR
    //=====================================================================
    //FlirInterface.CameraType cameraType = FlirInterface.CameraType.SimulatorOne;    // Testing
    FlirInterface.CameraType cameraType = FlirInterface.CameraType.USB;           // Production

    //==========================================================
    // TODO: VARIÁVEIS DE CONFIGURAÇÃO - LER DO FIREBASE
    //==========================================================
    private boolean activateFlir = true;
    private boolean activateMaskDetection = true;
    private double temperatureThreshold = 38.2;     // Temp que é considerada alta
    private long delayBetweetDetections = 10000L;    // Tempo em milisegundos entre detecções

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        btnResultado = findViewById(R.id.btnResultado);
        btnTopBar = findViewById(R.id.buttonTop);
        btnBottomBar = findViewById(R.id.buttonBottom);
        msxImage = findViewById(R.id.msx_image);

        btnTopBar.setVisibility(View.INVISIBLE);
        btnBottomBar.setVisibility(View.INVISIBLE);
        btnResultado.setVisibility(View.INVISIBLE);
        msxImage.setVisibility(View.INVISIBLE);

        // Beep
        dtmf = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // Real-time contour detection of multiple faces
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .build();


        faceDetector = FaceDetection.getClient(options);;

        // Initialize TTS
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.ERROR) {
                Locale desiredLanguage = Locale.forLanguageTag("pt-BR");
                if (tts.isLanguageAvailable(desiredLanguage) > 0) {
                    tts.setLanguage(Locale.forLanguageTag("pt-BR"));
                }
                //tts.setSpeechRate(0.8f);
            }
        });

        // Initialize Flir
        flirInterface = FlirInterface.getInstance(this, this);

        // reset
        stateCheckTimer = new Timer();
    }


    @Override
    public synchronized void onResume() {
        resetDetection();
        try {
            if (flirInterface != null) {
                flirInterface.updateContext(this, this);
                flirInterface.setDesiredCameraType(cameraType);
                flirInterface.autoConnect();
            }
        } catch(Exception ex) {
            Log.e("onResume", ex.getMessage());
        }
        stateCheckTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!hasFace) {
                    resetDetection();
                } else {
                    timerLoop();
                }
            }
        }, 0, 2000);
        super.onResume();
    }

    private void timerLoop() {
        //|| CONTROLA A STATE MACHINE ||\\
        switch (currentStage) {
            case FaceDetection:
                if (hasFace) {
                    currentStage = DetectionStages.FaceRecognition;
                }
                break;
            case FaceRecognition:
                if (isRecognized) {
                    askForMask();
                    currentStage = DetectionStages.MaskDetection;
                }
                break;
            case MaskDetection:
                if (hasMask) {
                    currentStage = DetectionStages.TemperatureRead;
                }
                break;
            case TemperatureRead:
                // Conditions to freedom
                if (temperature >= temperatureThreshold) {
                    currentStage = DetectionStages.Jail;
                } else {
                    currentStage = DetectionStages.Freedom;
                }
                delayMillis = System.currentTimeMillis();
                break;
            case Freedom:
                showAccessGranted();
                showMaskStatus();
                showThermalImage();
                speakAccessResult(true);
                openGate();
                if (System.currentTimeMillis() - delayMillis >= delayBetweetDetections) {
                    resetDetection();
                    closeGate();
                }
                break;
            case Jail:
                showAccessBlocked();
                showMaskStatus();
                speakAccessResult(false);
                beep();
                if (System.currentTimeMillis() - delayMillis >= delayBetweetDetections) {
                    resetDetection();
                }
                break;
        }
    }

    @Override
    public synchronized void onPause() {
        if (flirInterface != null) {
            if (flirInterface.isConnected()) {
                flirInterface.disconnect();
            }
            if (flirInterface.isDiscovering()) {
                flirInterface.stopDiscovery();
            }
        }
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ex) {
                Log.e("SPEECH PAUSE", ex.getMessage());
            }
        }
        super.onPause();
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        DetectorActivity that = this;

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

            detectorMask =
                    TFLiteObjectDetectionAPIModelMask
                            .create(
                                    getAssets(),
                                    TF_OD_API_MASKMODEL_FILE,
                                    TF_OD_API_LABELS_FILE2,
                                    TF_OD_API_INPUT_SIZE2,
                                    TF_OD_API_IS_QUANTIZED);

            ConfiguracaoFirebase.loadPeopleData(new ConfiguracaoFirebase.PeopleLoadListener() {
                @Override
                public void onComplete(ArrayList<Funcionario> funcionarios) {
                    people = funcionarios;
                    // Load Firebase Data
                    if (people != null) {
                        for (Funcionario p : people) {
                            if (p.getRecognitionData() != null) {
                                detector.register(p.getNome(), Recognition.fromJson(p.getRecognitionData()), that);
                            }
                        }
                    }
                }

                @Override
                public void onError(String message) {
                    //
                }
            });

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
        } else {
            targetW = previewWidth;
            targetH = previewHeight;
        }
        int cropW = (int) (targetW / 2.0);
        int cropH = (int) (targetH / 2.0);

        croppedBitmap = Bitmap.createBitmap(cropW, cropH, Config.ARGB_8888);

        portraitBmp = Bitmap.createBitmap(targetW, targetH, Config.ARGB_8888);
        faceBmp = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Config.ARGB_8888);
        faceBmp2 = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE2, TF_OD_API_INPUT_SIZE2, Config.ARGB_8888);
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropW, cropH,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);


        Matrix frameToPortraitTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        targetW, targetH,
                        sensorOrientation, MAINTAIN_ASPECT);


        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(canvas -> {
            tracker.draw(canvas);
            if (isDebug()) {
                tracker.drawDebug(canvas);
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

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        InputImage image = InputImage.fromBitmap(croppedBitmap, 0);
        faceDetector
                .process(image)
                .addOnSuccessListener(faces -> {
                    if (faces.size() == 0) {
                        hasFace = false;
                        updateResults(currTimestamp, new LinkedList<>());
                        return;
                    }
                    else {
                        hasFace = true;
                    }
                    runInBackground(() -> {
                        onFacesDetected(currTimestamp, faces, addPending);
                        addPending = false;
                    });
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
    public void setThermalInfo(Bitmap thermal, double temperature) {
        this.temperature = temperature;
        String tmpString = String.format("%sºC", precision.format(temperature));
        runOnUiThread(() -> {
            btnTopBar.setText(tmpString);
            msxImage.setImageBitmap(thermal);
        });
    }

    @Override
    public Context getContext() {
        return null;
    }

    private void showAccessBlocked() {
        // Show elements according to access blocked
        runOnUiThread(() -> {
            btnTopBar.setVisibility(View.VISIBLE);
            btnBottomBar.setVisibility(View.VISIBLE);

            btnTopBar.setBackground(getDrawable(R.drawable.topbar_red));
            btnBottomBar.setBackground(getDrawable(R.drawable.bottombar_red));
            btnBottomBar.setText("Não permitido");
        });
    }

    private void showAccessGranted() {
        runOnUiThread(() -> {
            btnTopBar.setVisibility(View.VISIBLE);
            btnBottomBar.setVisibility(View.VISIBLE);

            btnTopBar.setBackground(getDrawable(R.drawable.topbar_green));
            btnBottomBar.setBackground(getDrawable(R.drawable.bottombar_green));
            btnBottomBar.setText("Entrada permitida");
        });
    }

    private void showMaskStatus() {
        // Mask Alert
        runOnUiThread(() -> {
            if (hasMask) {
                btnResultado.setText("Com máscara");
                btnResultado.setBackground(getDrawable(R.drawable.button_green));
            } else {
                btnResultado.setText("Sem máscara");
                btnResultado.setBackground(getDrawable(R.drawable.button_red));
            }
            btnResultado.setVisibility(View.VISIBLE);
        });
    }

    private void showThermalImage() {
        runOnUiThread(() -> {
            msxImage.setVisibility(View.VISIBLE);
        });
    }

    private void askForMask() {
        try {
            String speakText = "Bem vindo " + detectedName.trim() + "! Por favor coloque sua máscara!";
            if (tts != null) {
                tts.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "putYourMask");
            }
        } catch(Exception ex) {
            Log.e("TTS", ex.toString());
        }
    }

    private void speakAccessResult(boolean accessResult) {
        if (didSpeak) return;

        String speakText = "";
        if (accessResult) {
            speakText = "Acesso permitido.";
        } else {
            speakText = "Acesso não permitido";
        }
        didSpeak = true;
        try {
            if (tts != null) {
                tts.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "accessStatus");
            }
        } catch(Exception ex) {
            Log.e("TTS", ex.toString());
        }
    }

    private void resetDetection() {
        // Go back to IDLE state
        delayMillis = 0L;
        detectedName = "";
        currentStage = DetectionStages.FaceDetection;
        hasFace = false;
        hasMask = false;
        isRecognized = false;
        temperature = 0.0;
        didBeep = false;
        didSpeak = false;

        runOnUiThread(() -> {
            btnTopBar.setVisibility(View.INVISIBLE);
            btnBottomBar.setVisibility(View.INVISIBLE);
            btnResultado.setVisibility(View.INVISIBLE);
            msxImage.setVisibility(View.INVISIBLE);
        });
    }

    private void openGate() {
        // TODO: INSIRA AQUI O CÓDIGO DE ABERTURA DA CATRACA/CANCELA

        Service conexao =new Service();


        Log.d("GATE", "Open Gate");
    }

    private void closeGate() {
        // TODO: INSIRA AQUI O CÓDIGO DE FECHAMENTO DA CATRACA/CANCELA
        Log.d("GATE", "Close Gate");
    }
    void beep() {
        if (didBeep) return;
        try {
            didBeep = true;
            dtmf.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300);
        } catch (Exception ex) {
            Log.e("BEEP", ex.getMessage());
        }
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
        runInBackground(() -> detectorMask.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
        runInBackground(() -> detectorMask.setNumThreads(numThreads));
    }


    // Face Processing
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


    private void updateResults(long currTimestamp, final List<Recognition> mappedRecognitions) {
        tracker.trackResults(mappedRecognitions, currTimestamp);
        trackingOverlay.postInvalidate();
        computingDetection = false;
    }

    private void onFacesDetected(long currTimestamp, List<Face> faces, boolean add) {

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

        final List<Recognition> mappedRecognitions =
                new LinkedList<Recognition>();


        //final List<Classifier.Recognition> results = new ArrayList<>();

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
        final Canvas cvFace2 = new Canvas(faceBmp2);
        boolean saved = false;

        for (Face face : faces) {

            LOGGER.i("FACE" + face.toString());
            LOGGER.i("Running detection on face " + currTimestamp);
            //results = detector.recognizeImage(croppedBitmap);

            final RectF boundingBox = new RectF(face.getBoundingBox());

            //final boolean goodConfidence = result.getConfidence() >= minimumConfidence;
            final boolean goodConfidence = true; //face.get;
            if (boundingBox != null && goodConfidence) {

                // maps crop coordinates to original
                cropToFrameTransform.mapRect(boundingBox);

                // maps original coordinates to portrait coordinates
                RectF faceBB = new RectF(boundingBox);
                transform.mapRect(faceBB);


                float sx2 = ((float) TF_OD_API_INPUT_SIZE2) / faceBB.width();
                float sy2 = ((float) TF_OD_API_INPUT_SIZE2) / faceBB.height();
                Matrix matrix2 = new Matrix();
                matrix2.postTranslate(-faceBB.left, -faceBB.top);
                matrix2.postScale(sx2, sy2);

                cvFace2.drawBitmap(portraitBmp, matrix2, null);

                // translates portrait to origin and scales to fit input inference size
                //cv.drawRect(faceBB, paint);
                float sx = ((float) TF_OD_API_INPUT_SIZE) / faceBB.width();
                float sy = ((float) TF_OD_API_INPUT_SIZE) / faceBB.height();
                Matrix matrix = new Matrix();
                matrix.postTranslate(-faceBB.left, -faceBB.top);
                matrix.postScale(sx, sy);

                cvFace.drawBitmap(portraitBmp, matrix, null);
                //canvas.drawRect(faceBB, paint);

                String label = "";
                float confidence = -1f;
                Integer color = Color.BLUE;
                float[][] extra = null;


                if (add) {
                    crop = Bitmap.createBitmap(portraitBmp,
                            (int) faceBB.left,
                            (int) faceBB.top,
                            (int) faceBB.width(),
                            (int) faceBB.height());
                }

                final long startTime = SystemClock.uptimeMillis();

                List<org.tensorflow.lite.examples.detection.tflitemask.Classifier.Recognition>
                        resultsAux2 = new ArrayList<>();
                resultsAux2 = detectorMask.recognizeImage(faceBmp2);

                if (resultsAux2.get(0).getTitle().equals("mask")) {
                    hasMask = true;
                } else {
                    hasMask = false;
                }

                //  final List< Recognition> resultsAux = new ArrayList<>();
                final List<Recognition> resultsAux = detector.recognizeImage(faceBmp, add);
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                if (resultsAux.size() > 0) {
                    Recognition result = resultsAux.get(0);
                    extra = result.getExtra();
                    float conf = result.getDistance();
                    if (conf < 1.0f) {
                        confidence = conf;
                        label = result.getTitle();
                        detectedName = label;
                        if (result.getId().equals("0")) {
                            color = Color.GREEN;
                            isRecognized = true;
//                            usuarioAtual = true;
                        } else {
                            color = Color.RED;
                            //isRecognized = false;
                        }
                    }
                }

                if (getCameraFacing() == CameraCharacteristics.LENS_FACING_FRONT) {

                    // camera is frontal so the image is flipped horizontally
                    // flips horizontally
                    Matrix flip = new Matrix();
                    if (sensorOrientation == 90 || sensorOrientation == 270) {
                        flip.postScale(1, -1, previewWidth / 2.0f, previewHeight / 2.0f);
                    } else {
                        flip.postScale(-1, 1, previewWidth / 2.0f, previewHeight / 2.0f);
                    }
                    //flip.postScale(1, -1, targetW / 2.0f, targetH / 2.0f);
                    flip.mapRect(boundingBox);

                }

                final Recognition result = new Recognition(
                        "0", label, confidence, boundingBox);

                result.setColor(color);
                result.setLocation(boundingBox);
                result.setExtra(extra);
                //  result.setCrop(crop);
                mappedRecognitions.add(result);

            }
        }
        updateResults(currTimestamp, mappedRecognitions);
    }
}
