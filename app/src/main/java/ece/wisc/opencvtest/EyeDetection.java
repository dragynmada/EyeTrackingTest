package ece.wisc.opencvtest;

import android.content.Context;
import android.util.Log;
import android.view.View;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
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

public class EyeDetection implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String TAG = "iTrakr_Main";

    // type of method to interpret the data on
    private static final int TM_CCOEFF_NORMED = 3;

    // while learn_frames is < 5, create a basic template to determine eye locations
    private int learnFrames = 0;

    // templates we create for left and right eye
    // private Mat templateR;
    private Mat templateL;

    // the current method we use to interpret the data
    int method;

    // see if we need the RGB channel, we only have IR greyscale images
    public Mat mRGB;
    public Mat mGrey;

    private CascadeClassifier mJavaDetector; // face
    private CascadeClassifier mJavaDetectorEye; // left eye

    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;

    // Allows OCV to communicate with the camera
    public CameraBridgeViewBase mOpenCvCameraView;

    // center of the face
    public double xCenter = -1;
    public double yCenter = -1;
    // center of the eye
    public double xEyeCenter = -1;
    public double yEyeCenter = -1;

    private Context context;

    private final int cameraID;

    public EyeDetection(Context context, CameraBridgeViewBase mOpenCvCameraView, int cameraID) {
        this.context = context;

        // get the OpenCV camera view
        this.mOpenCvCameraView = mOpenCvCameraView;
        this.mOpenCvCameraView.setCvCameraViewListener(this);

        method = TM_CCOEFF_NORMED;

        this.cameraID = cameraID;

        // set the face size to 40% of normal (determined from testing, good size)
        setFaceSize(0.4f);
    }

    /* Callback function for the OpenCV Package Manager - creates the cascades for face and eye
        detection and links them to the JavaDetectors */
    public BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(context) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    try {
                        // https://docs.opencv.org/3.4/db/d28/tutorial_cascade_classifier.html
                        // load cascade file from application resources
                        // Here we have two options - use the HAAR cascade or Local Binary Pattern
                        // cascade for face detection. LBP performs faster, thus we use it
                        // the lack of accuracy isn't bad for this, we really care about the eye
                        // recognition, not so much face
                        InputStream inpStr = context.getResources().openRawResource(
                                R.raw.lbpcascade_frontalface);
                        File cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE);
                        // cascade classifiers for eye/face detection
                        File mCascadeFile = new File(cascadeDir,
                                "lbpcascade_frontalface.xml");
                        FileOutputStream outStr = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;

                        // read into buffer, return the number of bytes read
                        // if -1, the file is done
                        while ((bytesRead = inpStr.read(buffer)) != -1) {
                            outStr.write(buffer, 0, bytesRead);
                        }
                        inpStr.close();
                        outStr.close();

                        // load left eye classifier - both eyes have the same patterns
                        // so to increase speed, just classify the left one
                        // we use HAAR on this for the reasons above - accuracy matters in the eyes
                        InputStream inpStrEye = context.getResources().openRawResource(
                                R.raw.haarcascade_lefteye_2splits);
                        File cascadeDirER = context.getDir("cascadeER",
                                Context.MODE_PRIVATE);
                        File cascadeFileER = new File(cascadeDirER,
                                "haarcascade_eye_right.xml");
                        FileOutputStream outStrEye = new FileOutputStream(cascadeFileER);

                        byte[] bufferEye = new byte[4096];
                        int bytesReadEye;

                        // same as above
                        while ((bytesReadEye = inpStrEye.read(bufferEye)) != -1) {
                            outStrEye.write(bufferEye, 0, bytesReadEye);
                        }
                        inpStrEye.close();
                        outStrEye.close();

                        // use LBP cascade and link it to the javaDetector - FACE RECOGNITION
                        mJavaDetector = new CascadeClassifier(
                                mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load classifier");
                            mJavaDetector = null;
                        } else {
                            Log.i(TAG, "Loaded classifier from "
                                    + mCascadeFile.getAbsolutePath());
                        }

                        // Link Haar ER cascade to javaDetector2 - EYE RECOGNITION
                        mJavaDetectorEye = new CascadeClassifier(
                                cascadeFileER.getAbsolutePath());
                        if (mJavaDetectorEye.empty()) {
                            Log.e(TAG, "Failed to load classifier");
                            mJavaDetectorEye = null;
                        } else {
                            Log.i(TAG, "Loaded classifier from "
                                    + mCascadeFile.getAbsolutePath());
                        }

                        // We have the files in the directory properly linked, we no longer need the
                        // directory
                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Initialize IR camera - You need to have JavaCamera2View to do this
                    /*TODO - add button to change between IR and Normal camera. We have increased
                     * resolution with the regular cameras which may prove better in testing
                     */
                    mOpenCvCameraView.setCameraIndex(cameraID);
                    mOpenCvCameraView.enableView();
                    break;
                }

                default: {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };

    /* Set up matrices for greyscale and RGB channels
     * We don't use width and height, but it's an implemented method */
    public void onCameraViewStarted(int width, int height) {
        mGrey = new Mat();
        mRGB = new Mat();
    }

    /* Deallocate camera resources */
    public void onCameraViewStopped() {
        mGrey.release(); // release() is OCV's version of free()
        mRGB.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // grab the color and greyscale channels of the frame captured
        mRGB = inputFrame.rgba();
        mGrey = inputFrame.gray();

        // calculate face size to pass into classifier
        if (mAbsoluteFaceSize == 0) {
            int height = mGrey.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }

        // results of the detector will be placed in here
        MatOfRect faces = new MatOfRect();

        // detect the face based on the LBP cascade
        if (mJavaDetector != null)
            mJavaDetector.detectMultiScale(mGrey, faces, 1.1, 2,
                    2, new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());

        Rect[] facesArray = faces.toArray();
        // included in OpenCV examples even though this doesn't loop?
        for (int i = 0; i < facesArray.length; i++) {
            /* draw the face rectangle - debug only
            Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(),
                    FACE_RECT_COLOR, 3);
             */

            // Center of the face, this is our reference point
            xCenter = (facesArray[i].x + facesArray[i].width + facesArray[i].x) / 2;
            yCenter = (facesArray[i].y + facesArray[i].y + facesArray[i].height) / 2;
            Point center = new Point(xCenter, yCenter);

            /* draw the center of the face - for debug purposes
            Imgproc.circle(mRgba, center, 10, new Scalar(255, 0, 0, 255), 3);

            Imgproc.putText(mRgba, "[" + center.x + "," + center.y + "]",
                    new Point(center.x + 20, center.y + 20),
                    Core.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(255, 255, 255,
                            255));
            */

            Rect r = facesArray[i];

            // compute the eye area, it's just a general guess off of generic face structure
            Rect eyeArea = new Rect(r.x + r.width / 8,
                    (int) (r.y + (r.height / 4.5)), r.width - 2 * r.width / 8,
                    (int) (r.height / 3.0));

            // split it for left and right eye
            Rect eyeAreaRight = new Rect(r.x + r.width / 16,
                    (int) (r.y + (r.height / 4.5)),
                    (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));
            Rect eyeAreaLeft = new Rect(r.x + r.width / 16
                    + (r.width - 2 * r.width / 16) / 2,
                    (int) (r.y + (r.height / 4.5)),
                    (r.width - 2 * r.width / 16) / 2, (int) (r.height / 3.0));

            /* draw the eye rectangles - debug only
            Imgproc.rectangle(mRgba, eyeAreaLeft.tl(), eyeAreaLeft.br(),
                    new Scalar(255, 0, 0, 255), 2);
            Imgproc.rectangle(mRgba, eyeAreaRight.tl(), eyeAreaRight.br(),
                    new Scalar(255, 0, 0, 255), 2);
            */

            if (learnFrames < 20) { // learn based on 10 frames of data
                // templateR = getTemplate(mJavaDetectorEye, eyeAreaRight, 24);
                templateL = getTemplate(mJavaDetectorEye, eyeAreaLeft, 24);
                learnFrames++;
            } else {
                // Learning finished, use the new templates for template matching
                // locateEye(eyeAreaRight, templateR, method); - if needed in the future,
                // we can re-add the right eye. All calculations are based off of the left eye
                // since we specifically have a left eye classifier
                locateEye(eyeAreaLeft, templateL, method);
            }
        }

        return mRGB;
    }

    private void setFaceSize(float faceSize) {
        mRelativeFaceSize = faceSize;
        mAbsoluteFaceSize = 0;
    }

    /* Locate the pupil within the eye */
    private void locateEye(Rect area, Mat mTemplate, int type) {
        Point matchLoc;
        // this is our image to search
        Mat mROI = mGrey.submat(area);
        int result_cols = mROI.cols() - mTemplate.cols() + 1;
        int result_rows = mROI.rows() - mTemplate.rows() + 1;

        // Check for bad template size
        if (mTemplate.cols() == 0 || mTemplate.rows() == 0) {
            return;
        }
        Mat mResult = new Mat(result_cols, result_rows, CvType.CV_8U);

        // run the template for the normed coefficient on the image, works best from testing
        Imgproc.matchTemplate(mROI, mTemplate, mResult, Imgproc.TM_CCOEFF_NORMED);

        Core.MinMaxLocResult mmres = Core.minMaxLoc(mResult);
        matchLoc = mmres.maxLoc;

        // only grabbing the left eye now
        xEyeCenter = matchLoc.x + area.x + (mTemplate.cols() / 2);
        yEyeCenter = matchLoc.y + area.y + (mTemplate.rows() / 2);

        // get the two corners of the pupil
        Point matchLoc_tx = new Point(matchLoc.x + area.x, matchLoc.y + area.y);
        Point matchLoc_ty = new Point(matchLoc.x + mTemplate.cols() + area.x,
                matchLoc.y + mTemplate.rows() + area.y);

        // draw box for the pupil
        Imgproc.rectangle(mRGB, matchLoc_tx, matchLoc_ty, new Scalar(255, 0, 0, 255));

        /*

        Rest in peace PCCR and Hough Circle detection. The resolution of the pixel 4 IR camera is
        way too low to get a solid image. We can accurately detect pupils with this, however.

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

    /* How we learn the data for left and right eye based on javaDetector2 */
    private Mat getTemplate(CascadeClassifier classifier, Rect area, int size) {
        Mat template = new Mat();
        // image to search
        Mat mROI = mGrey.submat(area);
        MatOfRect eyes = new MatOfRect();
        Point iris = new Point();
        Rect eye_template = new Rect();

        classifier.detectMultiScale(mROI, eyes, 1.15, 2,
                Objdetect.CASCADE_FIND_BIGGEST_OBJECT
                        | Objdetect.CASCADE_SCALE_IMAGE, new Size(30, 30),
                new Size());

        Rect[] eyesArray = eyes.toArray();

        // non looping for loop, basically a check
        for (int i = 0; i < eyesArray.length;) {
            Rect e = eyesArray[i];
            e.x = area.x + e.x;
            e.y = area.y + e.y;
            Rect eye_only_rectangle = new Rect((int) e.tl().x,
                    (int) (e.tl().y + e.height * 0.4), e.width,
                    (int) (e.height * 0.6));
            mROI = mGrey.submat(eye_only_rectangle);
            Mat vyrez = mRGB.submat(eye_only_rectangle);

            Core.MinMaxLocResult mmG = Core.minMaxLoc(mROI);

            Imgproc.circle(vyrez, mmG.minLoc, 2, new Scalar(255, 255, 255, 255), 2);
            iris.x = mmG.minLoc.x + eye_only_rectangle.x;
            iris.y = mmG.minLoc.y + eye_only_rectangle.y;
            eye_template = new Rect((int) iris.x - size / 2, (int) iris.y
                    - size / 2, size, size);
            Imgproc.rectangle(mRGB, eye_template.tl(), eye_template.br(),
                    new Scalar(255, 0, 0, 255), 2);
            template = (mGrey.submat(eye_template)).clone();
            return template;
        }
        return template;
    }

    public void relearnFace(View v)
    {
        learnFrames = 0;
    }
}
