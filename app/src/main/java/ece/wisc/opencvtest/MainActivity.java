package ece.wisc.opencvtest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    EyeDetection eyeDetect;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // don't let the screen timeout, we want the camera always visible
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // use the XML for the face detect
        // TODO if the phone is vertical, we should display a screen to say turn phone sideways
        setContentView(R.layout.face_detect_surface_view);

        eyeDetect = new EyeDetection(MainActivity.this, findViewById(R.id.fd_activity_surface_view));

        // Camera Permission requests
        if (ActivityCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                            Manifest.permission.CAMERA},
                    101);
            return;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // disable the cameraView while paused
        if (eyeDetect.mOpenCvCameraView != null)
            eyeDetect.mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        // statically link the package manager and the callback - previously done by Google Play
        // but now we need to handle it to account for OpenCV3 vs OpenCV4
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this,
                eyeDetect.mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        if (eyeDetect.mOpenCvCameraView != null)
            eyeDetect.mOpenCvCameraView.disableView();
    }

    public void calibrateGaze(View view) {
        Intent i = new Intent(getApplicationContext(), Calibration.class);
        startActivity(i);
    }

    public void relearnFace(View view) {
        eyeDetect.relearnFace(view);
    }
}