package com.example.android.screencapture;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.UUID;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;


public class ScreenCaptureImageActivity extends Activity {

    private static final String TAG = ScreenCaptureImageActivity.class.getName();
    private static final int REQUEST_CODE= 100;

    private static final int PIXEL_FORMAT = 4;
    private static final int HEADER_BUFFER_CAPACITY = 12;

    private MediaProjectionManager mProjectionManager;
    private MediaProjection mProjection;
    private ImageReader mImageReader;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private int imagesProduced;
    private long startTimeInMillis;
    private Buffer mHeaderBuffer;
    private int mImageCount = 0;
    private Canvas mCanvas;
    private Bitmap mTempBitmap;
    private final int NUMBER_OF_LAYERS = 10;
    private Drawable[] mLayers = new Drawable[NUMBER_OF_LAYERS];
    private String[] mFilePaths = new String[NUMBER_OF_LAYERS];
    private ImageView mImageView;
    private String mPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_capture_image);

        mImageView = (ImageView) findViewById(R.id.imageView1);

        // call for the projection manager
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // start projection
        Button startButton = (Button)findViewById(R.id.startButton);
        startButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                startProjection();
            }
        });

        // stop projection
        Button stopButton = (Button)findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                stopProjection();
            }
        });

        // start capture handling thread
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                mHandler = new Handler();
                Looper.loop();
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            // for statistics -- init
            imagesProduced = 0;
            startTimeInMillis = System.currentTimeMillis();

            mProjection = mProjectionManager.getMediaProjection(resultCode, data);

            if (mProjection != null) {
                //final String STORE_DIRECTORY = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

                DisplayMetrics metrics = getResources().getDisplayMetrics();
                int density = metrics.densityDpi;
                int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
                Display display = getWindowManager().getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);
                final int width = size.x;
                final int height = size.y;

                mHeaderBuffer = createImageHeaderBuffer(width, height);
                mImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 10);
                mProjection.createVirtualDisplay("screencap", width, height, density, flags, mImageReader.getSurface(), new VirtualDisplayCallback(), mHandler);
                mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {

                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = null;
                        FileOutputStream fos = null;
                        Bitmap bitmap = null;

                        try {
                            image = mImageReader.acquireLatestImage();
                            if (image != null) {
                                Image.Plane[] planes = image.getPlanes();
                                Buffer imageBuffer = planes[0].getBuffer().rewind();

                                // create bitmap
                                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                                bitmap.copyPixelsFromBuffer(imageBuffer);
                                // write bitmap to a file

                                //Create the canvas we want to write to
                                if(mCanvas == null){
                                    mTempBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                                    mCanvas = new Canvas(mTempBitmap);
                                }

                                String filename = String.format("Screenshot-%d.png", new Date().getTime());
                                mPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/" + filename;
                                fos = new FileOutputStream(mPath);
                                bitmap.compress(CompressFormat.JPEG, 100, fos);

                                // for statistics
                                imagesProduced++;
                                long now = System.currentTimeMillis();
                                long sampleTime = now - startTimeInMillis;
                                Log.e(TAG, "produced images at rate: " + (imagesProduced/(sampleTime/1000.0f)) + " per sec");

                                //Display Image on View
                                //ImageView mImageView = (ImageView) findViewById(R.id.imageView1);
                                //mImageView.setImageBitmap(bitmap);

                                //Draw on the canvas
                                //mCanvas.drawBitmap(bitmap, 0, 0, null);
                                //mImageView.setImageDrawable(new BitmapDrawable(getResources(), mTempBitmap));

                                mFilePaths[mImageCount] = mPath;
                                mImageCount++;


                                if (mImageCount == NUMBER_OF_LAYERS) {


                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            for (int i = 0; i < mImageCount; i++) {
                                                Bitmap myBitmap = BitmapFactory.decodeFile(mPath);
                                                mLayers[i] = new BitmapDrawable(getResources(), myBitmap);
                                            }

                                            mImageCount = 0; //reset the image count
                                            mFilePaths = new String[NUMBER_OF_LAYERS]; //reset the file paths

                                            LayerDrawable layerDrawable = new LayerDrawable(mLayers);
                                            mImageView.setImageDrawable(layerDrawable);
                                            mLayers = new Drawable[NUMBER_OF_LAYERS]; //Reset the layers
                                        }
                                    });


                                }

                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            if (fos!=null) {
                                try {
                                    fos.close();
                                    //Call function to layer the new graphic
                                } catch (IOException ioe) {
                                    ioe.printStackTrace();
                                }
                            }

//                            if (bitmap!=null) {
//                                bitmap.recycle();
//                            }

                            if (image!=null) {
                                image.close();
                            }
                        }
                    }

                }, mHandler);
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }


    private Buffer createImageHeaderBuffer(int width, int height) {
        return ByteBuffer.allocate(HEADER_BUFFER_CAPACITY).order(ByteOrder.LITTLE_ENDIAN).putInt(width).putInt(height).putInt(PIXEL_FORMAT).rewind();
    }

    private void startProjection() {
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
    }

    private void stopProjection() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mProjection.stop();
            }
        });
    }

    private class VirtualDisplayCallback extends VirtualDisplay.Callback {

        @Override
        public void onPaused() {
            super.onPaused();
            Log.e(TAG, "VirtualDisplayCallback: onPaused");
        }

        @Override
        public void onResumed() {
            super.onResumed();
            Log.e(TAG, "VirtualDisplayCallback: onResumed");
        }

        @Override
        public void onStopped() {
            super.onStopped();
            Log.e(TAG, "VirtualDisplayCallback: onStopped");
        }
    }
}
