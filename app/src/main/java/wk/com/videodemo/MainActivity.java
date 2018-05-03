package wk.com.videodemo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.VideoView;

import wk.com.videodemo.camera2.VideoActivity;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "wkMainActivity";
    private static final String VIDEO_DIR = "/storage/sdcard0/zzzccc/videotest/";
    private static final String VIDEO_DIR_0 = "/storage/sdcard0/zzzccc/videotest/video00.mp4";
    private static final String VIDEO_DIR_1 = "/storage/sdcard0/zzzccc/videotest/video01.mp4";
    private static final String VIDEO_DIR_2 = "/storage/sdcard0/zzzccc/videotest/video02.mp4";
    private static final String VIDEO_DIR_3 = "/storage/sdcard0/zzzccc/videotest/video03.mp4";
    private static final String VIDEO_DIR_4 = "/storage/sdcard0/zzzccc/videotest/video04.mp4";
    private static final String VIDEO_DIR_5 = "/storage/sdcard0/zzzccc/videotest/video05.mp4";
    private static final String VIDEO_DIR_6 = "/storage/sdcard0/zzzccc/videotest/video06.mp4";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_system).setOnClickListener(this);
        findViewById(R.id.btn_videoview).setOnClickListener(this);
        findViewById(R.id.btn_surfaceview).setOnClickListener(this);
        findViewById(R.id.btn_textureview).setOnClickListener(this);
    }

    private void playBySystem() {
        Uri uri = Uri.parse(VIDEO_DIR_0);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "video/mp4");
        startActivity(intent);
    }

    private void playByVideoView() {
        Uri uri = Uri.parse(VIDEO_DIR_0);
        VideoView videoView = findViewById(R.id.videoview);
//        videoView.setMediaController(new MediaController(this));
        videoView.setVideoURI(uri);
        videoView.start();
        videoView.requestFocus();
    }

    private void playBySurfaceView() {
        startActivity(new Intent(this, PlayerActivity.class));
    }

    private void playByTextureView() {

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_system:
                playBySystem();
                break;
            case R.id.btn_videoview:
                playByVideoView();
                break;
            case R.id.btn_surfaceview:
                playBySurfaceView();
                break;
            case R.id.btn_textureview:
                startActivity(new Intent(this, VideoActivity.class));
//                startActivity(new Intent(this, SurfaceActivity.class));
//                playByTextureView();
                break;
        }
    }


}
