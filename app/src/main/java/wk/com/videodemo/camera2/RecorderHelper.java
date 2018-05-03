package wk.com.videodemo.camera2;


import android.content.Context;
import android.media.MediaRecorder;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.Surface;

import java.io.File;
import java.io.IOException;

public class RecorderHelper {
    private Context mContext;
    private MediaRecorder mRecorder;
    private String mPath;
    private boolean hasPrepared;

    private static final int SENSOR_DEFAULT_DEGREES = 90;
    private static final int SENSOR_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    public RecorderHelper(Context context) {
        mContext = context;
    }

    private void initIfNecessary() {
        if (null == mRecorder) {
            mRecorder = new MediaRecorder();
        }
    }

    private void updatePath() {
        final File dir = mContext.getExternalFilesDir(null);
        if (null != dir) {
            mPath = dir.getAbsolutePath() + "/" + System.currentTimeMillis() + ".mp4";
        }
    }

    public void configRecorder(int sensorOrientation, int displayRotation) {
        initIfNecessary();

        // 设置存储路径
        updatePath();
        if (TextUtils.isEmpty(mPath)) {
            return;
        }
        mRecorder.setOutputFile(mPath);

        // 设置音、视频采集源
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        // 设置音、视频编码格式，以及文件封装格式。设置顺序必须跟下面一模一样，否则报错
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        // 设置比特率、帧率和分辨率
        mRecorder.setVideoEncodingBitRate(800 * 800);
        mRecorder.setVideoFrameRate(30);
        // 这里只是展示用法，实际开发中需要根据摄像头的支持size来取
        mRecorder.setVideoSize(960, 720);

        // 根据camera方向和屏幕角度，设置录制视频的角度补偿
        if (SENSOR_DEFAULT_DEGREES == sensorOrientation) {
            mRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(displayRotation));
        } else if (SENSOR_INVERSE_DEGREES == sensorOrientation) {
            mRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(displayRotation));
        }

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        hasPrepared = true;
    }

    public void start() {
        if (hasPrepared) {
            mRecorder.start();
        }
    }

    // 停止之后，MediaRecorder不需要置空，下次使用时需要重新配置
    public void stop() {
        if (hasPrepared) {
            mRecorder.stop();
            mRecorder.reset();
            hasPrepared = false;
        }
    }

    public void release() {
        if (null != mRecorder) {
            mRecorder.release();
            hasPrepared = false;
            mRecorder = null;
        }
    }

    public Surface getSurface() {
        return hasPrepared ? mRecorder.getSurface() : null;
    }

    public boolean isRecording() {
        return hasPrepared;
    }
}
