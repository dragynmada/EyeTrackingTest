package ece.wisc.opencvtest;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.opencv.android.OpenCVLoader;

import java.util.ArrayList;

public class Calibration extends AppCompatActivity {

    private static final String TAG = "OCVSample::Calibration";

    private Button startButton;
    private Button eyeDetectButton;

    // used to store the offsets for eye to
    public double[][] offsets;

    private EyeDetection eyeDetection;

    private int cameraID;

    private String[] instructionTexts = {
            "Look at the bottom left of your device and press \"Calibrate Eye Detect\". Make " +
                    "sure your keep your head stable. If you see a red box around your left eye," +
                    " press the Continue button.",
            "Look at the top left of your computer screen, keeping your head still.",
            "Look at the top right of your computer screen, keeping your head still.",
            "Look at the bottom left of your computer screen, keeping your head still.",
            "Look at the bottom right of your computer screen, keeping your head still.",
    };

    // arraylist used to store offsets at every camera frame update, will average these after
    private ArrayList<double[]> offsetBuffer;

    private boolean calibrating;
    private boolean startAveraging;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // don't let the screen timeout, we want the camera always visible
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.calibration_page);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            this.cameraID = (int) extras.get("cameraID");
            if (cameraID == 2) {
                eyeDetection = new EyeDetection(this,
                        findViewById(R.id.calViewNorm), cameraID);
            } else {
                eyeDetection = new EyeDetection(this,
                        findViewById(R.id.calViewIR), cameraID);
            }
        }

        offsets = new double[4][2];

        offsetBuffer = new ArrayList<double[]>();

        startAveraging = false;
        calibrating = false;

        startButton = (Button)findViewById(R.id.startCalibrateButton);
        eyeDetectButton = (Button)findViewById(R.id.findEyesButton);
        final TextView instructions = findViewById(R.id.instructions);

        instructions.setText(instructionTexts[0]);

        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                    startButton.setVisibility(View.INVISIBLE);
                    eyeDetectButton.setVisibility(View.INVISIBLE);
                    calibrating = true;
                    new Thread(new Runnable() {
                        public void run() {
                            for (int i = 0; i < 4; i++) {
                                int finalI = i;
                                Calibration.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        instructions.setText(instructionTexts[finalI + 1]);
                                    }
                                });

                                try {
                                    Thread.sleep(4000);
                                } catch (Exception e) {

                                }

                                startAveraging = true;

                                for (int j = 3; j > 0; j--) {
                                    int finalJ = j;
                                    Calibration.this.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            instructions.setText(String.valueOf(finalJ));
                                        }
                                    });

                                    try {
                                        Thread.sleep(1000);
                                    } catch (Exception e) {

                                    }
                                }

                                startAveraging = false;

                                // add all offsets up
                                for (int j = 0; j < offsetBuffer.size(); j++) {
                                    offsets[i][0] += offsetBuffer.get(j)[0];
                                    offsets[i][1] += offsetBuffer.get(j)[1];
                                }

                                // compute averages
                                offsets[i][0] /= offsetBuffer.size();
                                offsets[i][1] /= offsetBuffer.size();

                                // reset for next go
                                offsetBuffer.clear();
                            }

                            Log.i("TestOffsets", "offsets[0][0] = " + offsets[0][0] +
                                    " offsets[0][1] = " + offsets[0][1] +
                                    " offsets[1][0] = " + offsets[1][0] +
                                    " offsets[1][1] = " + offsets[1][1] +
                                    " offsets[2][0] = " + offsets[2][0] +
                                    " offsets[2][1] = " + offsets[2][1] +
                                    " offsets[3][0] = " + offsets[3][0] +
                                    " offsets[3][1] = " + offsets[3][1]
                            );

                            Intent i = new Intent(getApplicationContext(), MainActivity.class);
                            i.putExtra("cameraID", cameraID);
                            startActivity(i);
                        }
                    }).start();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!calibrating) {
                    // busy wait and sleep
                    try {
                        Thread.sleep(10);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                while (calibrating) {
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    while (startAveraging) {
                        try {
                            Thread.sleep(10);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        offsetBuffer.add(new double[]{eyeDetection.xEyeCenter -
                                eyeDetection.xCenter, eyeDetection.yCenter -
                                eyeDetection.yEyeCenter});
                    }
                }
            }
        }).start();
    }

    @Override
    public void onPause() {
        super.onPause();
        // disable the cameraView while paused
        if (eyeDetection.mOpenCvCameraView != null)
            eyeDetection.mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        // statically link the package manager and the callback - previously done by Google Play
        // but now we need to handle it to account for OpenCV3 vs OpenCV4
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this,
                eyeDetection.mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        eyeDetection.mOpenCvCameraView.disableView();
    }

    public void relearnFace(View view) {
        eyeDetection.relearnFace(view);
        startButton.setVisibility(View.VISIBLE);
    }
}
