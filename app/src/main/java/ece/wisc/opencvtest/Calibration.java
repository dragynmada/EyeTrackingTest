package ece.wisc.opencvtest;

import static java.lang.Thread.sleep;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

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

public class Calibration  extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    private static final String TAG = "OCVSample::Calibration";

    private Button startButton;

    // see if we need the RGB channel, we only have IR greyscale images
    private Mat mRgba;
    private Mat mGray;
    // matrix for zooming - this would be good to improve so we can run PCCR on this zoomed image
    private Mat mZoomWindow;
    private Mat mZoomWindow2;

    private String[] mDetectorName;

    // types of methods to interpret the data on
    private static final int TM_SQDIFF = 0;
    private static final int TM_SQDIFF_NORMED = 1;
    private static final int TM_CCOEFF = 2;
    private static final int TM_CCOEFF_NORMED = 3;
    private static final int TM_CCORR = 4;
    private static final int TM_CCORR_NORMED = 5;

    // while learn_frames is < 5, create a basic template to determine eye locations
    private int learn_frames = 0;
    // templates we create for left and right eye
    private Mat templateR;
    private Mat templateL;
    // the current method we use to interpret the data
    int method = 0;

    // center of the face
    double xCenter = -1;
    double yCenter = -1;
    // center of the eye
    double xEyeCenter = -1;
    double yEyeCenter = -1;

    // used to store the offsets for eye to
    public double[][] offsets = new double[4][2];

    // cascade classifiers for eye/face detection
    private File mCascadeFile;
    private CascadeClassifier mJavaDetector; // face
    private CascadeClassifier mJavaDetectorEye; // left eye

    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;

    public static final int JAVA_DETECTOR = 0;
    private CameraBridgeViewBase mOpenCvCameraView;

    private String[] instructionTexts = {
            "Look at the bottom left of your device and press \"Calibrate Eye Detect\". Make " +
                    "sure your keep your head stable. If you see a red box around your left eye," +
                    " press the Continue button.",
            "Look at the top left of your computer screen, keeping your head still.",
            "Look at the top right of your computer screen, keeping your head still.",
            "Look at the bottom left of your computer screen, keeping your head still.",
            "Look at the bottom right of your computer screen, keeping your head still.",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // don't let the screen timeout, we want the camera always visible
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.calibration_page);

        // get the OpenCV camera view
        mOpenCvCameraView = findViewById(R.id.cal_eye_direction);
        mOpenCvCameraView.setCvCameraViewListener(this);

        startButton = (Button)findViewById(R.id.button2);
        final TextView instructions = findViewById(R.id.instructions);

        instructions.setText(instructionTexts[0]);

        method = 3;
        setMinFaceSize(0.4f);

        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                    startButton.setVisibility(View.INVISIBLE);
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
                                    Thread.sleep(3000);
                                } catch (Exception e) {

                                }

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

                                // TODO - average these results over many pictures
                                offsets[i][0] = xEyeCenter - xCenter;
                                offsets[i][1] = yCenter - yEyeCenter;
                            }

                            Log.i("TestOffsets", "offsets[0][0] = " + offsets[0][0] +
                                    " offsets[0][1] = " + offsets[0][1] +
                                    " offsets[1][0] = " + offsets[1][0] +
                                    " offsets[1][1] = " + offsets[1][1] +
                                    " offsets[2][0] = " + offsets[2][0] +
                                    " offsets[2][1] = " + offsets[2][1] +
                                    " offsets[3][0] = " + offsets[3][0] +
                                    " offsets[4][1] = " + offsets[3][1]
                            );

                            Intent i = new Intent(getApplicationContext(), MainActivity.class);
                            startActivity(i);
                        }
                    }).start();
            }
        });
    }

    public Calibration() {
        mDetectorName = new String[2];
        mDetectorName[JAVA_DETECTOR] = "Java";

        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public void onPause() {
        super.onPause();
        // disable the cameraView while paused
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        // statically link the package manager and the callback - previously done by Google Play
        // but now we need to handle it to account for OpenCV3 vs OpenCV4
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this,
                mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        mOpenCvCameraView.disableView();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mGray.release(); // release() is OCV's version of free()
        mRgba.release();
        mZoomWindow.release();
        mZoomWindow2.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // grab the color and greyscale channels of the frame captured
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        // calculate face size to pass into cascade
        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }

        // populate zoomed in eye windows
        if (mZoomWindow == null || mZoomWindow2 == null)
            CreateAuxiliaryMats();

        MatOfRect faces = new MatOfRect();

        // detect the face based on the LBP cascade
        if (mJavaDetector != null)
            mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2,
                    2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize),
                    new Size());


        Rect[] facesArray = faces.toArray();
        for (int i = 0; i < facesArray.length; i++) {
            /* draw the face rectangle
            Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(),
                    FACE_RECT_COLOR, 3);
             */
            xCenter = (facesArray[i].x + facesArray[i].width + facesArray[i].x) / 2;
            yCenter = (facesArray[i].y + facesArray[i].y + facesArray[i].height) / 2;
            Point center = new Point(xCenter, yCenter);

            // draw the center of the face - for debug purposes
            Imgproc.circle(mRgba, center, 10, new Scalar(255, 0, 0, 255), 3);

            Imgproc.putText(mRgba, "[" + center.x + "," + center.y + "]",
                    new Point(center.x + 20, center.y + 20),
                    Core.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255,
                            255));

            Rect r = facesArray[i];

            // compute the eye area
            Rect eyearea = new Rect(r.x + r.width / 8,
                    (int) (r.y + (r.height / 4.5)), r.width - 2 * r.width / 8,
                    (int) (r.height / 3.0));
            // split it for left and right eye
            Rect eyearea_right = new Rect(r.x + r.width / 16,
                    (int) (r.y + (r.height / 4.5)),
                    (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));
            Rect eyearea_left = new Rect(r.x + r.width / 16
                    + (r.width - 2 * r.width / 16) / 2,
                    (int) (r.y + (r.height / 4.5)),
                    (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));

            /* draw the eye rectangles
            Imgproc.rectangle(mRgba, eyearea_left.tl(), eyearea_left.br(),
                    new Scalar(255, 0, 0, 255), 2);
            Imgproc.rectangle(mRgba, eyearea_right.tl(), eyearea_right.br(),
                    new Scalar(255, 0, 0, 255), 2);
            */
            if (learn_frames < 10) { // learn based on 5 frames of data
                templateR = get_template(mJavaDetectorEye, eyearea_right, 24);
                templateL = get_template(mJavaDetectorEye, eyearea_left, 24);
                learn_frames++;
            } else {
                // Learning finished, use the new templates for template
                // matching
                match_eye(eyearea_right, templateR, method);
                match_eye(eyearea_left, templateL, method);

            }
        }

        return mRgba;
    }

    /* Create Matrices for the left and right eye zoom images */
    private void CreateAuxiliaryMats() {
        if (mGray.empty())
            return;

        int rows = mGray.rows();
        int cols = mGray.cols();

        if (mZoomWindow == null) {
            mZoomWindow = mRgba.submat(rows / 2 + rows / 10, rows, cols / 2
                    + cols / 10, cols);
            mZoomWindow2 = mRgba.submat(0, rows / 2 - rows / 10, cols / 2
                    + cols / 10, cols);
        }

    }

    /* return the proper template based on the classifier */
    private Mat get_template(CascadeClassifier clasificator, Rect area, int size) {
        Mat template = new Mat();
        Mat mROI = mGray.submat(area);
        MatOfRect eyes = new MatOfRect();
        Point iris = new Point();
        Rect eye_template = new Rect();
        clasificator.detectMultiScale(mROI, eyes, 1.15, 2,
                Objdetect.CASCADE_FIND_BIGGEST_OBJECT
                        | Objdetect.CASCADE_SCALE_IMAGE, new Size(30, 30),
                new Size());

        Rect[] eyesArray = eyes.toArray();
        for (int i = 0; i < eyesArray.length;) {
            Rect e = eyesArray[i];
            e.x = area.x + e.x;
            e.y = area.y + e.y;
            Rect eye_only_rectangle = new Rect((int) e.tl().x,
                    (int) (e.tl().y + e.height * 0.4), (int) e.width,
                    (int) (e.height * 0.6));
            mROI = mGray.submat(eye_only_rectangle);
            Mat vyrez = mRgba.submat(eye_only_rectangle);


            Core.MinMaxLocResult mmG = Core.minMaxLoc(mROI);

            Imgproc.circle(vyrez, mmG.minLoc, 2, new Scalar(255, 255, 255, 255), 2);
            iris.x = mmG.minLoc.x + eye_only_rectangle.x;
            iris.y = mmG.minLoc.y + eye_only_rectangle.y;
            eye_template = new Rect((int) iris.x - size / 2, (int) iris.y
                    - size / 2, size, size);
            Imgproc.rectangle(mRgba, eye_template.tl(), eye_template.br(),
                    new Scalar(255, 0, 0, 255), 2);
            template = (mGray.submat(eye_template)).clone();
            return template;
        }
        return template;
    }

    private void match_eye(Rect area, Mat mTemplate, int type) {
        Point matchLoc;
        // this is our image to search
        Mat mROI = mGray.submat(area);
        int result_cols = mROI.cols() - mTemplate.cols() + 1;
        int result_rows = mROI.rows() - mTemplate.rows() + 1;

        // Check for bad template size
        if (mTemplate.cols() == 0 || mTemplate.rows() == 0) {
            return;
        }
        Mat mResult = new Mat(result_cols, result_rows, CvType.CV_8U);

        Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCOEFF_NORMED);

        Core.MinMaxLocResult mmres = Core.minMaxLoc(mResult);
        matchLoc = mmres.maxLoc;

        // used for center of the eye - only use for left eye since our classifier is made for
        // the left eye
        if (matchLoc.x + area.x > xCenter) {
            xEyeCenter = matchLoc.x + area.x + (mTemplate.cols() / 2);
            yEyeCenter = matchLoc.y + area.y + (mTemplate.rows() / 2);
        }

        // get the two corners of the pupil
        Point matchLoc_tx = new Point(matchLoc.x + area.x, matchLoc.y + area.y);
        Point matchLoc_ty = new Point(matchLoc.x + mTemplate.cols() + area.x,
                matchLoc.y + mTemplate.rows() + area.y);

        // draw box for the pupil
        Imgproc.rectangle(mRgba, matchLoc_tx, matchLoc_ty, new Scalar(255, 0, 0, 255));
        Rect rec = new Rect(matchLoc_tx,matchLoc_ty);

        /*
        // compute precise pupil and CR locations
        Mat houghResult = new Mat();
        // TODO - tweak the numerical parameters
        Imgproc.HoughCircles(mROI, houghResult, Imgproc.CV_HOUGH_GRADIENT, 1, 1, 80, 10, 1, 5);

        if (houghResult.cols() > 0) {
            for (int x = 0; x < Math.min(houghResult.cols(), 5); x++) {
                double circleVec[] = houghResult.get(0, x);

                if (circleVec == null)
                    break;

                Point center = new Point((int) circleVec[0] + area.x, (int) circleVec[1] + area.y);
                int radius = (int) circleVec[2];

                Log.i("HoughCircles", "center.x = " + center.x + " center.y = " + center.y);

                Imgproc.circle(mRgba, center, radius, new Scalar(255, 255, 255), 1);
            }
        }
        */
    }

    public void onRecreateClick(View v)
    {
        learn_frames = 0;
        startButton.setVisibility(View.VISIBLE);
    }

    /* Callback function for the OpenCV Package Manager - creates the cascades for face and eye
    detection and links them to the JavaDetectors */
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    try {
                        // https://docs.opencv.org/3.4/db/d28/tutorial_cascade_classifier.html
                        // load cascade file from application resources
                        // Here we have two options - use the HAAR cascade or Local Binary Pattern
                        // cascade for face detection. LBP performs faster, thus we use it
                        // the lack of accuracy isn't bad for this, we really care about the eye
                        // recognition, not so much face
                        InputStream is = getResources().openRawResource(
                                R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir,
                                "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        // load left eye classifier - both eyes have the same patterns
                        // so to increase speed, just classify the left one
                        // we use HAAR on this for the reasons above - accuracy matters in the eyes
                        InputStream iser = getResources().openRawResource(
                                R.raw.haarcascade_lefteye_2splits);
                        File cascadeDirER = getDir("cascadeER",
                                Context.MODE_PRIVATE);
                        File cascadeFileER = new File(cascadeDirER,
                                "haarcascade_eye_right.xml");
                        FileOutputStream oser = new FileOutputStream(cascadeFileER);

                        byte[] bufferER = new byte[4096];
                        int bytesReadER;
                        while ((bytesReadER = iser.read(bufferER)) != -1) {
                            oser.write(bufferER, 0, bytesReadER);
                        }
                        iser.close();
                        oser.close();

                        // use LBP FR cascade and link it to the javaDectecor
                        mJavaDetector = new CascadeClassifier(
                                mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from "
                                    + mCascadeFile.getAbsolutePath());

                        // Link Haar ER cascade to javaDetector 2
                        mJavaDetectorEye = new CascadeClassifier(
                                cascadeFileER.getAbsolutePath());
                        if (mJavaDetectorEye.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetectorEye = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from "
                                    + mCascadeFile.getAbsolutePath());

                        // We have the files in the directory properly linked, we no longer need the
                        // directory
                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    // give camera permissions
                    if (ActivityCompat.checkSelfPermission(Calibration.this, Manifest.permission.CAMERA) !=
                            PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(Calibration.this, new String[]{
                                        Manifest.permission.CAMERA},
                                101);
                        return;
                    }

                    // Initialize IR camera - You need to have JavaCamera2View to do this
                    mOpenCvCameraView.setCameraIndex(2);
                    mOpenCvCameraView.enableView();
                }
                break;

                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private void setMinFaceSize(float faceSize) {
        mRelativeFaceSize = faceSize;
        mAbsoluteFaceSize = 0;
    }
}
