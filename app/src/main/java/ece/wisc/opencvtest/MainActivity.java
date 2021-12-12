package ece.wisc.opencvtest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import org.opencv.android.JavaCamera2View;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    public EyeDetection eyeDetect;

    private Button switchIRButton;

    private Button switchNormButton;

    private JavaCameraView normalCamera;
    private JavaCamera2View irCamera;

    private int cameraID;

    private MessageSender messageSender;

    public class NewDataCallback implements Runnable {
        public int x, y;

        @Override
        public void run() {
            messageSender.sendMouse(x, y);
        }
    }

    private NewDataCallback cb = new NewDataCallback();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // don't let the screen timeout, we want the camera always visible
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // change view for custom XML file
        // TODO if the phone is vertical, we should display a screen to say turn phone sideways
        setContentView(R.layout.face_detect_surface_view);

        // Camera Permission requests
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new
                    String[]{Manifest.permission.CAMERA}, 101);
            return;
        }

        switchIRButton = findViewById(R.id.irCameraButton);
        switchNormButton = findViewById(R.id.normalCameraButton);

        normalCamera = findViewById(R.id.cameraViewNorm);
        irCamera = findViewById(R.id.cameraViewIR);

        this.cameraID = 2;

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            this.cameraID = (int) extras.get("cameraID");
            if (this.cameraID == 1)
                eyeDetect = new EyeDetection(this, normalCamera, this.cameraID);
            else
                eyeDetect = new EyeDetection(this, irCamera, this.cameraID);
            // send over the coordinates of corners
            eyeDetect.setOffsets((double[][]) extras.get("offsets"));
            // Configure the callback
            eyeDetect.setNewDataCallback(cb);
            // Open TCP socket connection
            // Use your IPv4 (gather from cmd console ipconfig)
            messageSender = new MessageSender("");
        } else {// always default to IR camera
            eyeDetect = new EyeDetection(MainActivity.this, irCamera, 2); // Normalcamera, 1
        }

        if (this.cameraID == 2) {
            switchNormButton.setVisibility(View.VISIBLE);
            switchIRButton.setVisibility(View.INVISIBLE);
        } else {
            switchNormButton.setVisibility(View.INVISIBLE);
            switchIRButton.setVisibility(View.VISIBLE);
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
        if (!OpenCVLoader.initDebug()) {
            // statically link the package manager and the callback - previously done by Google Play
            // but now we need to handle it to account for OpenCV3 vs OpenCV4
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this,
                    eyeDetect.mLoaderCallback);
        } else {
            eyeDetect.mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (eyeDetect.mOpenCvCameraView != null)
            eyeDetect.mOpenCvCameraView.disableView();
        messageSender.destroy();
    }

    public void calibrateGaze(View view) {
        Intent i = new Intent(getApplicationContext(), Calibration.class);
        i.putExtra("cameraID", cameraID);
        startActivity(i);
    }

    public void relearnFace(View view) {
        eyeDetect.relearnFace(view);
    }

    public void switchIR(View view) {
        switchNormButton.setVisibility(View.VISIBLE);
        switchIRButton.setVisibility(View.INVISIBLE);
        irCamera.setVisibility(View.VISIBLE);
        normalCamera.setVisibility(View.INVISIBLE);
        eyeDetect.mOpenCvCameraView.disableView();
        eyeDetect.mOpenCvCameraView = irCamera;
        eyeDetect.mOpenCvCameraView.setCvCameraViewListener(eyeDetect);
        eyeDetect.mOpenCvCameraView.setCameraIndex(2);
        eyeDetect.mOpenCvCameraView.enableView();
        cameraID = 2;
    }

    public void switchNorm(View view) {
        switchNormButton.setVisibility(View.INVISIBLE);
        switchIRButton.setVisibility(View.VISIBLE);
        irCamera.setVisibility(View.INVISIBLE);
        normalCamera.setVisibility(View.VISIBLE);
        eyeDetect.mOpenCvCameraView.disableView();
        eyeDetect.mOpenCvCameraView = normalCamera;
        eyeDetect.mOpenCvCameraView.setCvCameraViewListener(eyeDetect);
        eyeDetect.mOpenCvCameraView.setCameraIndex(1);
        eyeDetect.mOpenCvCameraView.enableView();
        cameraID = 1;
    }
}