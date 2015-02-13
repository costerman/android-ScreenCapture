/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.screencapture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
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
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.example.android.common.logger.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Provides UI for the screen capture.
 */
public class ScreenCaptureFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "ScreenCaptureFragment";

    private static final String STATE_RESULT_CODE = "result_code";
    private static final String STATE_RESULT_DATA = "result_data";

    private static final int REQUEST_MEDIA_PROJECTION = 1;

    private int mScreenDensity;

    private int mResultCode;
    private Intent mResultData;

    DisplayMetrics mMetrics = new DisplayMetrics();
    private Context mContext;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ImageReader mImageReader;
    private Surface mSurface;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionManager mMediaProjectionManager;
    private Button mButtonToggle;
    private SurfaceView mSurfaceView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mResultCode = savedInstanceState.getInt(STATE_RESULT_CODE);
            mResultData = savedInstanceState.getParcelable(STATE_RESULT_DATA);
        }
        mContext = getActivity();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_screen_capture, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mSurfaceView = (SurfaceView) view.findViewById(R.id.surface);
        mSurface = mSurfaceView.getHolder().getSurface();
        mButtonToggle = (Button) view.findViewById(R.id.toggle);
        mButtonToggle.setOnClickListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Activity activity = getActivity();

        activity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
        mScreenDensity = mMetrics.densityDpi;
        mMediaProjectionManager = (MediaProjectionManager)
                activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mResultData != null) {
            outState.putInt(STATE_RESULT_CODE, mResultCode);
            outState.putParcelable(STATE_RESULT_DATA, mResultData);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.toggle:
                if (mVirtualDisplay == null) {
                    startScreenCapture();
                } else {
                    stopScreenCapture();
                }
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "User cancelled");
                Toast.makeText(getActivity(), R.string.user_cancelled, Toast.LENGTH_SHORT).show();
                return;
            }
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            Log.i(TAG, "Starting screen capture");
            mResultCode = resultCode;
            mResultData = data;
            setUpMediaProjection();
            setUpVirtualDisplay();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopScreenCapture();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tearDownMediaProjection();
    }

    private boolean getScreenshotSetting(){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_CAPTURE_SCREENSHOT, false);
    }

    private void setUpMediaProjection() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(mResultCode, mResultData);
    }

    private void tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void startScreenCapture() {
        Activity activity = getActivity();
        if (mSurface == null || activity == null) {
            return;
        }
        if (mMediaProjection != null) {
            setUpVirtualDisplay();
        } else if (mResultCode != 0 && mResultData != null) {
            setUpMediaProjection();
            setUpVirtualDisplay();
        } else {
            Log.i(TAG, "Requesting confirmation");
            // This initiates a prompt dialog for the user to confirm screen projection.
            startActivityForResult(
                    mMediaProjectionManager.createScreenCaptureIntent(),
                    REQUEST_MEDIA_PROJECTION);
        }
    }

    private void setUpVirtualDisplay() {

        if(getScreenshotSetting()){
            setUpVirtualImageReaderDisplay();
        } else {
            setUpVirtualSurfaceViewDisplay();
        }
    }

    private void setUpVirtualSurfaceViewDisplay(){
        int height = mSurfaceView.getHeight();
        int width = mSurfaceView.getWidth();

        Log.i(TAG, "Setting up a VirtualDisplay: " +
                width + "x" + height +
                " (" + mScreenDensity + ")");
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("ScreenCapture",
                width, height, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface, null, null);
        mButtonToggle.setText(R.string.stop);
    }

    private void setUpVirtualImageReaderDisplay(){
        int height = mSurfaceView.getHeight();
        int width = mSurfaceView.getWidth();


        mImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("Screenshot",
                width, height, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(),
                new VirtualDisplayCallback(),
                mHandler);
        mImageReader.setOnImageAvailableListener(new ImageAvailableCallback(height, width), mHandler);
    }

    private void stopScreenCapture() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
        mButtonToggle.setText(R.string.start);
    }


    private class VirtualDisplayCallback extends VirtualDisplay.Callback{
        @Override
        public void onPaused(){
            super.onPaused();
            Log.d(TAG, "VirtualDisplayCallback: onPaused");
        }

        @Override
        public void onResumed(){
            super.onResumed();
            Log.d(TAG, "VirtualDisplayCallback: onResumed");
        }

        @Override
        public void onStopped(){
            super.onStopped();
            Log.d(TAG, "VirtualDisplayCallback: onStopped");
        }
    }

    private class ImageAvailableCallback implements ImageReader.OnImageAvailableListener{

        private int mHeight;
        private int mWidth;

        private ImageAvailableCallback(int height, int width){
            mHeight = height;
            mWidth = width;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            //Process the image
            Image image = null;
            FileOutputStream fos = null;
            Bitmap bitmap = null;

            try{
                String filename = String.format("Screenshot-%s.jpeg", UUID.randomUUID().toString());

                image = mImageReader.acquireLatestImage();
                int height = image.getHeight();
                int width = image.getWidth();
                fos = new FileOutputStream(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/" + filename);
                final Image.Plane[] planes = image.getPlanes();

                final int PIXEL_FORMAT = 4;
                final int HEADER_BUFFER_CAPACITY = 12;
                ByteBuffer.allocate(HEADER_BUFFER_CAPACITY).order(ByteOrder.LITTLE_ENDIAN).putInt(width).putInt(height).putInt(PIXEL_FORMAT).rewind();

                //Attempt #1
                final Buffer buffer = planes[0].getBuffer().rewind();
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);


                //Attempt #2
//                int offset = 0;
//                int pixelStride = planes[0].getPixelStride();
//                int rowStride = planes[0].getRowStride();
//                int rowPadding = rowStride - pixelStride * width;
//                bitmap = Bitmap.createBitmap(mMetrics, width, height, Bitmap.Config.ARGB_8888);
//                final ByteBuffer buffer = planes[0].getBuffer();
//                for (int i = 0; i < height; ++i) {
//                    for (int j = 0; j < width; ++j) {
//                        int pixel = 0;
//                        pixel |= (buffer.get(offset) & 0xff) << 16;     // R
//                        pixel |= (buffer.get(offset + 1) & 0xff) << 8;  // G
//                        pixel |= (buffer.get(offset + 2) & 0xff);       // B
//                        pixel |= (buffer.get(offset + 3) & 0xff) << 24; // A
//                        bitmap.setPixel(j, i, pixel);
//                        offset += pixelStride;
//                    }
//                    offset += rowPadding;
//                }


                bitmap.copyPixelsFromBuffer(buffer);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            } catch (Exception ex){
                ex.printStackTrace();
            } finally {
                if(fos != null){
                    try{
                        fos.close();
                    } catch (IOException ioe){
                        ioe.printStackTrace();
                    }
                }
                if(bitmap != null){
                    bitmap.recycle();
                }
                if(image != null){
                    image.close();
                }
            }
        }
    }

}
