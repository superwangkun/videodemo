package wk.com.videodemo.camera2;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import wk.com.videodemo.R;

/**
 * Camera2的操作都是基于管道的，就是发送请求、等待回应的过程，使用起来没有代码结构不如Camera那种线性调用清晰。通过下面四个回调就能说清楚使用过程：
 * <p>
 * 1.等待surface创建成功的回调，即SurfaceTextureListener（或者是SurfaceView的listener，demo用的是TextureView）
 * 做Camera开发就必须要预览，要预览就得有Surface，所以第一步就是等待Surface创建完成；
 * <p>
 * 2.等待Camera启动完成的回调，即CameraDevice.StateCallback
 * Camera的启动需要一个过程，只有Camera启动后才可进行各种操作
 * <p>
 * 3.等待会话建立的回调，即CameraCaptureSession.StateCallback
 * 要向Camera发送各种操作请求，就必须先建立会话通道
 * <p>
 * 4.等待操作请求的回调，即CameraCaptureSession.CaptureCallback
 * 向Camera发起了"拍照"请求后，Camera需要一定时间才能完成，等待完成后就可以对图像数据进行处理了
 * <p>
 * 总结起来就是：创建surface、启动camera、创建camera会话、发起拍照请求
 */
public class VideoActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "VideoActivity";

    private TextureView mTextureView;

    private CameraDevice mCameraDevice;

    // 方便理清camera2使用的主逻辑，一些配置代码、计算代码放在各个helper类里
    private CameraHelper mCameraHelper;
    private RecorderHelper mRecorderHelper;
    private TextureHelper mTextureHelper;

    // 一个device同一时间只能存在一个session，新的session启动时会关闭其它session；
    // 一个session对应一个request、surfaceList，注意处理好一一对应关系
    private CaptureRequest.Builder mRequest;
    private List<Surface> mSurfaceList = new ArrayList<>();
    private CameraCaptureSession mSession;

    // camera2中用到的几个回调，通过指定handler，回调方法就会在该handler所在线程被调用
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

        // 启动后台线程，用于执行回调中的代码
        startBackgroundThread();

        // 如果Activity是从stop/pause回来，TextureView是OK的，只需要重新开启camera就行
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            // Activity创建时，添加TextureView的监听，TextureView创建完成后就可以开启camera就行了
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        // 关闭camera，关闭后台线程
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
                // 如果openCamera()方法的第三个参数指定了handler，那么下面的代码就会在该handler所在线程中执行，如果不指定就在openCamera()方法所在线程执行
                mCameraDevice = cameraDevice;
                startPreviewSession();

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
        // 获取TextureView中的surface，添加到request中、添加到surfaceList中
        Surface previewSurface = mTextureHelper.getSurface(mTextureView);

        if (null != previewSurface) {
            mRequest.addTarget(previewSurface);
            mSurfaceList.add(previewSurface);
        }
    }

    private void addRecorderSurface() {
        // 获取MediaRecorder中的surface，添加到request中、添加到surfaceList中
        Surface recorderSurface = mRecorderHelper.getSurface();

        if (null != recorderSurface) {
            mRequest.addTarget(recorderSurface);
            mSurfaceList.add(recorderSurface);
        }
    }

    private void closeCamera() {
        // 关闭camera预览，关闭MediaRecorder
        closePreviewSession();
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        mRecorderHelper.release();
    }

    private void startPreviewSession() {
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

            // 启动会话
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

    private void startRecordSession() {
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

            // 启动会话。可以看出跟上面的"预览session"是一样的，只是surfaceList多加了一个
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

            // 这个接口是预览。作用是把camera捕捉到的画面输出到surfaceList中的各个surface上，每隔一定时间重复一次
            mSession.setRepeatingRequest(mRequest.build(), null, mBackgroundHandler);

            // 这个接口是拍照。由于拍照需要获得图像数据，所以这里需要实现CaptureCallback，在回调里获得图像数据
//            mSession.capture(CaptureRequest request, CaptureCallback listener, Handler handler);
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
        int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
        mRecorderHelper.configRecorder(sensorOrientation, displayRotation);

        startRecordSession();
    }

    private void stopRecord() {
        // 关闭录像会话，停止录像，重新进入预览
        mRecorderHelper.stop();
        startPreviewSession();
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
