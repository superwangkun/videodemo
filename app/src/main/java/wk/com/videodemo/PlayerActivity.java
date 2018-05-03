package wk.com.videodemo;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class PlayerActivity extends Activity {
    private static final String TAG = "PlayerActivity";
    private static final String VIDEO_DIR_0 = "/storage/sdcard0/zzzccc/videotest/video00.mp4";
    private MyPlayer mPlayer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_surfaceviewplayer);

        // 播放视频
        mPlayer = new MyPlayer();
        mPlayer.play(this, Uri.parse(VIDEO_DIR_0));

        // 初始化Surface，创建成功后，调用player的setDisplay()方法设置Surface
        SurfaceView surfaceView = findViewById(R.id.sv_player);
        SurfaceHolder holder = surfaceView.getHolder();
        holder.setFormat(PixelFormat.RGB_888);
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mPlayer.setDisplay(holder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                // 这里可以实时监听视频界面的变化
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                // 界面不可见时就会销毁，比如Activity的onStop()方法
            }
        });
    }
}
