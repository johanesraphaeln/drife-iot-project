package com.example.drife;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.DialogInterface;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    JavaCameraView javaCameraView;
    String TAG = "OCV::MainActivity";
    File cascFile;
    File cascEyeFile;

    private Mat mRgb, mGrey;

    CascadeClassifier faceDetector;
    CascadeClassifier eyeDetector;

    boolean alertToggle;
    MediaPlayer mp;


    LocationManager locationManager;
    Location location;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        OpenCVLoader.initDebug();

        javaCameraView = findViewById(R.id.javaCamView);

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, baseCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            try {
                baseCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //change to front camera
        javaCameraView.setCameraIndex(1);
        javaCameraView.setCvCameraViewListener(this);

        alertToggle = false;

    }

    public void alert(){
        //Alert Sound, Timer For sending data, and the proses of sending data is from here

        Log.i("AlertDrow", "Showing Alert");
        alertToggle = true;

        mp = MediaPlayer.create(MainActivity.this, R.raw.alarm_sound);
        mp.setLooping(true);
        mp.start();

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this);
        dialogBuilder.setCancelable(true);
        dialogBuilder.setTitle("Drowsy Detected");
        dialogBuilder.setMessage("Wake up and stop the car. Click the STOP button to close this alert.");
        dialogBuilder.setNegativeButton("Stop", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                alertToggle = false;
                mp.stop();
                mp.reset();
                dialog.cancel();
            }
        });
        dialogBuilder.show();
    }


    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgb = new Mat();
        mGrey = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mGrey.release();
        mGrey.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgb = inputFrame.rgba();
        mGrey = inputFrame.gray();

        // detect face
        MatOfRect faceDetections = new MatOfRect();
        faceDetector.detectMultiScale(mRgb, faceDetections);

        for (Rect face: faceDetections.toArray()){
            // Draw Rectangle around the face
            Imgproc.rectangle(mRgb,
                    new Point(face.x, face.y),
                    new Point(face.x + face.width, face.y + face.height),
                    new Scalar(255,0,0),
                    4
            );

            // get face pixels, pixels that are only from the face detected
            Mat faceROI = mGrey.submat(face);

            // In each face, detect eyes
            MatOfRect eyes = new MatOfRect();
            eyeDetector.detectMultiScale(faceROI, eyes);

            List<Rect> listOfEyes = eyes.toList();

            // for every eye detected, draw a circle and calculated its radius
            for (Rect eye : listOfEyes) {
                Point eyeCenter = new Point(face.x + eye.x + eye.width / 2, face.y + eye.y + eye.height / 2);
                Point eyeIdk = new Point(eye.x + eye.width / 2, eye.y + eye.height / 2);
                Log.i("eyeCenter", String.valueOf(eyeCenter));
                int radius = (int) Math.round((eye.width + eye.height) * 0.25);
                Log.i("eyeRadius", String.valueOf(radius));
                Imgproc.circle(mRgb, eyeCenter, radius, new Scalar(255, 0, 0), 4);
                Log.d("RADIUS", String.valueOf(radius));
                // if the radius is below 70, then its drowsy
                if (radius < 35 && !alertToggle){
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            alert();
                        }
                    });

                    Log.i("eyeDrowsiness", "Drowsiness detected");

                }
            }
        }
        return mRgb;
    }

    // This Callback Loads the model for Haar Cascade
    private BaseLoaderCallback baseCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status){
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    try {
                        InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_alt2);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        cascFile = new File(cascadeDir, "haarcascade_frontalface_alt2.xml");

                        FileOutputStream fos = new FileOutputStream(cascFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;

                        while ((bytesRead = is.read(buffer)) != -1){
                            fos.write(buffer,0,bytesRead);
                        }

                        is.close();
                        fos.close();

                        InputStream isEye = getResources().openRawResource(R.raw.haarcascade_eye_tree_eyeglasses);
                        File cascadeEyeDir = getDir("cascadeEye", Context.MODE_PRIVATE);
                        cascEyeFile = new File(cascadeEyeDir, "haarcascade_eye_tree_eyeglasses.xml");

                        FileOutputStream fosEye = new FileOutputStream(cascEyeFile);

                        byte[] bufferEye = new byte[4096];
                        int bytesReadEye;

                        while ((bytesReadEye = isEye.read(bufferEye)) != -1){
                            fosEye.write(bufferEye,0,bytesReadEye);
                        }

                        isEye.close();
                        fosEye.close();

                        faceDetector = new CascadeClassifier(cascFile.getAbsolutePath());
                        eyeDetector = new CascadeClassifier(cascEyeFile.getAbsolutePath());

                        if (faceDetector.empty()){
                            Log.e(TAG, "Failed to load cascade classifier");
                            faceDetector = null;
                        }
                        else {
                            Log.i(TAG, "Loaded cascade classifier from " + cascFile.getAbsolutePath());
                            cascadeDir.delete();
                        }

                        if (eyeDetector.empty()){
                            Log.e(TAG, "Failed to load cascade eye classifier");
                            eyeDetector = null;
                        }
                        else {
                            Log.i(TAG, "Loaded cascade classifier from " + cascEyeFile.getAbsolutePath());
                            cascadeEyeDir.delete();
                        }

                        javaCameraView.enableView();
                        javaCameraView.enableFpsMeter();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                }
                break;

                default: {
                    try {
                        super.onManagerConnected(status);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed onManagerConnected. Exception thrown: " + e);
                    }
                }
                break;
            }
        }
    };
}