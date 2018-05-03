package wk.com.videodemo.camera2;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

public class TextureHelper {
    private Activity mActivity;

    // 这里只是展示用法，实际开发中需要根据摄像头的支持size来取
    private Size mPreviewSize = new Size(960, 720);

    public TextureHelper(Activity activity) {
        mActivity = activity;
    }

    // 配置预览图的大小、方向/角度
    public void configPreview(TextureView textureView, int targetWidth, int targetHeight) {
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, targetWidth, targetHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) targetHeight / mPreviewSize.getHeight(), (float) targetWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    // 根据TextureView和预览size，获取surface
    public Surface getSurface(TextureView textureView) {
        SurfaceTexture texture = textureView.getSurfaceTexture();
        assert texture != null;
        texture.setDefaultBufferSize(960, 720);
        return new Surface(texture);
    }
}
