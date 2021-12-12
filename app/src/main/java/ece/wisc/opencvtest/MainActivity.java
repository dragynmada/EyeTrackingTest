package ece.wisc.opencvtest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

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
    private String ipAddress;

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


        // pop up for user to input HOST (IPv4) Address
        if (ipAddress == null || ipAddress.length() == 0)
            thing();

        setupEyeDetectionUnit();

    }

    private void thing() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Title");

        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ipAddress = input.getText().toString();

                // Open TCP socket connection
                // Use your IPv4 (gather from cmd console ipconfig)
                messageSender = new MessageSender(ipAddress);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void setupEyeDetectionUnit() {
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

    public void onButtonShowPopupWindowClick(View view) {

        // inflate the layout of the popup window
        LayoutInflater inflater = (LayoutInflater)
                getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.input_ipv4_popup, null);

        // create the popup window
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true; // lets taps outside the popup also dismiss it
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);

        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);

        // dismiss the popup window when touched
        popupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                popupWindow.dismiss();
                return true;
            }
        });
    }
}