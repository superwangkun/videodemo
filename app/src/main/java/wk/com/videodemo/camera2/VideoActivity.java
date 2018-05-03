package wk.com.videodemo.camera2;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import wk.com.videodemo.R;

/*
       * 1.SurfaceTextureListener - SurfaceView是第一步，有了surface才能显示camera捕获到的画面。第一步就是等待Surface创建完成的callback
       * 2.CameraDevice.StateCallback - 第二步是openCamera，等待open完成的callback就可以创建camera会话管道了
       * 3.CameraCaptureSession.StateCallback() - 第三步是建立会话，等待会话创建完成，就发起各种操作请求了
       * 4.CameraCaptureSession.CaptureCallback - 第四步是发起相机操作请求，操作请求完成后会通过
       *
       * */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class VideoActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "VideoActivity";

    private TextureView mTextureView;

    private CameraHelper mCameraHelper;
    private RecorderHelper mRecorderHelper;
    private TextureHelper mTextureHelper;

    // 一个device同一时间只能存在一个会话session，对应一个request、surfaceList
    private CaptureRequest.Builder mRequest;
    private CameraDevice mCameraDevice;
    private List<Surface> mSurfaceList = new ArrayList<>();
    private CameraCaptureSession mSession;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        mTextureView = findViewById(R.id.texture);
        findViewById(R.id.video_record).setOnClickListener(this);
        findViewById(R.id.video_stop).setOnClickListener(this);

        mCameraHelper = new CameraHelper(this);
        mRecorderHelper = new RecorderHelper(this);
        mTextureHelper = new TextureHelper(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.video_record:
                if (!mRecorderHelper.isRecording()) {
                    startRecord();
                }
                break;
            case R.id.video_stop:
                if (mRecorderHelper.isRecording()) {
                    stopRecord();
                }
                break;
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        // 设置预览大小、方向/角度
        mTextureHelper.configPreview(mTextureView, mTextureView.getWidth(), mTextureView.getHeight());

        // 开启后置摄像头
        mCameraHelper.openCamera(mCameraHelper.getBackCameraId(), new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                Log.i(TAG, "opened");
                mCameraDevice = cameraDevice;
                startPreviewRequest();

                if (null != mTextureView) {
                    mTextureHelper.configPreview(mTextureView, mTextureView.getWidth(), mTextureView.getHeight());
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                cameraDevice.close();
                mCameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int error) {
                cameraDevice.close();
                mCameraDevice = null;
                finish();
            }

        }, null);
    }

    private void addTextureViewSurface() {
        Surface previewSurface = mTextureHelper.getSurface(mTextureView);

        if (null != previewSurface) {
            mRequest.addTarget(previewSurface);
            mSurfaceList.add(previewSurface);
        }
    }

    private void addRecorderSurface() {
        Surface recorderSurface = mRecorderHelper.getSurface();

        if (null != recorderSurface) {
            mRequest.addTarget(recorderSurface);
            mSurfaceList.add(recorderSurface);
        }
    }

    private void closeCamera() {
        closePreviewSession();
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        mRecorderHelper.release();
    }

    private void startPreviewRequest() {
        if (null == mCameraDevice || !mTextureView.isAvailable()) {
            return;
        }

        try {
            // 创建新的会话前，关闭以前的会话
            closePreviewSession();

            // 创建预览会话请求
            mSurfaceList.clear();
            mRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            addTextureViewSurface();

            // 参数1：camera捕捉到的画面分别输出到surfaceList的各个surface中;
            // 参数2：会话状态监听;
            // 参数3：监听器中的方法会在指定的线程里调用，通过一个handler对象来指定线程;
            mCameraDevice.createCaptureSession(mSurfaceList, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }

                @Override
                public void onClosed(@NonNull CameraCaptureSession session) {
                    super.onClosed(session);
                }
            }, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startRecordRequest() {
        if (null == mCameraDevice || !mTextureView.isAvailable()) {
            return;
        }

        try {
            closePreviewSession();

            // 创建录像会话请求
            mSurfaceList.clear();
            mRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            addTextureViewSurface();
            addRecorderSurface();

            // 启动会话
            mCameraDevice.createCaptureSession(mSurfaceList, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mSession = session;
                    updatePreview();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mRecorderHelper.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(VideoActivity.this, "Failed", Toast.LENGTH_SHORT).show();
                }
            }, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }

        try {
            mRequest.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            mSession.setRepeatingRequest(mRequest.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closePreviewSession() {
        if (mSession != null) {
            try {
                mSession.stopRepeating();
                mSession.abortCaptures();
                mSession.close();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void startRecord() {
        // 设置Recorder配置，启动录像会话
        int sensorOrientation = mCameraHelper.getSensorOrientation(mCameraHelper.getBackCameraId());
        mRecorderHelper.configRecorder(sensorOrientation, getWindowManager().getDefaultDisplay().getRotation());

        startRecordRequest();
    }

    private void stopRecord() {
        // 关闭录像会话，停止录像，重新进入预览
        mRecorderHelper.stop();
        startPreviewRequest();
    }

    // TextureView状态监听
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            mTextureHelper.configPreview(mTextureView, width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };
}
