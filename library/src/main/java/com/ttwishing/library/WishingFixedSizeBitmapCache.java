package com.ttwishing.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.widget.ImageView;

import com.ttwishing.library.base.RefCountedBitmapDrawable;
import com.ttwishing.library.base.RefCountedBitmapPool;
import com.ttwishing.library.util.DrawingCanvas;

import java.io.File;

/**
 * Created by kurt on 12/5/15.
 *
 * 设计为40*40尺寸的Cache
 * 因尺寸大小固定,reusableBitmap可巧妙用作inBitmap, 优化decode内存
 */
public class WishingFixedSizeBitmapCache extends BaseWishingFixedSizeBitmapCache {

    private Context context;

    private final Rect dstRect = new Rect();
    private final Rect srcRect = new Rect();
    private final Rect tempRect = new Rect();
    private final RectF bitmapRectF = new RectF();
    private final Paint paint;

    //file锁
    private Object fileLock = new Object();

    //内存优化
    private Bitmap reusableBitmap;

    private final Matrix imageMatrix;
    private final ImageView.ScaleType scaleType;

    public WishingFixedSizeBitmapCache(Context context, int width, int height, ImageView.ScaleType scaleType, Matrix imageMatrix) {
        super(context, "photo-" + width + "x" + height, width, height, Bitmap.Config.ARGB_8888, 40, 15, 200);
        this.context = context;
        this.paint = new Paint();
        this.paint.setFilterBitmap(true);
        this.scaleType = scaleType;
        this.imageMatrix = imageMatrix;
    }

    @Override
    public void onDestroy() {
        if (this.reusableBitmap != null) {
            this.reusableBitmap.recycle();
            this.reusableBitmap = null;
        }
        super.onDestroy();
    }

    @Override
    protected RefCountedBitmapDrawable getBitmapFromNetworkResult(File file, RefCountedBitmapPool<RefCountedBitmapDrawable> pool) {

        if (!file.exists() || !file.canRead()) {
            return null;
        }

        Log.d("BaseRgbBitmapCache", "getBitmapFromNetworkResult: " + file);

        synchronized (this.fileLock) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (options.outHeight < 1 || options.outWidth < 1) {
                //could not get dimensions from bitmap.
                file.delete();
                return null;
            }

            if (this.reusableBitmap == null) {
                this.reusableBitmap = Bitmap.createBitmap(options.outWidth, options.outHeight, Bitmap.Config.ARGB_8888);
            } else if (this.reusableBitmap.getWidth() != options.outWidth || this.reusableBitmap.getHeight() != options.outHeight) {//cond_4
                //
                this.reusableBitmap.recycle();
                this.reusableBitmap = Bitmap.createBitmap(options.outWidth, options.outHeight, Bitmap.Config.ARGB_8888);
            }else{
                //reusableBitmap is reusable
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;
            options.inScaled = false;


            Bitmap bitmap = createBitmap(file, options);
            if (bitmap == null) {
                //could not load bitmap for post processing
                return null;
            }

            RefCountedBitmapDrawable refCountedBitmapDrawable = pool.get();

            //此时先异步,在DrawingCanvas将bitmap绘制出来,在ui线程绘制时会更快速
            DrawingCanvas drawingCanvas = new DrawingCanvas(refCountedBitmapDrawable.getBitmap());
            customDraw(drawingCanvas, bitmap);

            return refCountedBitmapDrawable;
        }
    }

    private Bitmap createBitmap(File file, BitmapFactory.Options options) {
        options.inBitmap = this.reusableBitmap;
        options.inMutable = true;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap != this.reusableBitmap) {
            //could not load bitmap into reused, this is BAD
            this.reusableBitmap.recycle();
            this.reusableBitmap = bitmap;
        }
        return this.reusableBitmap;
    }

    /**
     * {@ImageView.configBounds}
     * @param drawingCanvas
     * @param bitmap
     */
    protected void customDraw(DrawingCanvas drawingCanvas, Bitmap bitmap) {
        drawingCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        int left = 0;
        int top = 0;
        int right = this.width;
        int bottom = this.height;

        if (this.scaleType == ImageView.ScaleType.CENTER_CROP) {
            //进行合理的
            this.srcRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
            this.dstRect.set(left, top, right, bottom);
            float scale;
            if ((this.srcRect.width() - this.dstRect.width()) / 2 < (this.srcRect.height() - this.dstRect.height()) / 2) {
                scale = (float) this.srcRect.width() / this.dstRect.width();
            } else {
                scale = (float) this.srcRect.height() / this.dstRect.height();
            }

            this.tempRect.set((int) (scale * this.dstRect.left), (int) (scale * this.dstRect.top), (int) (scale * this.dstRect.right), (int) (scale * this.dstRect.bottom));
            int dx = (this.srcRect.width() - this.tempRect.width()) / 2;
            int dy = (this.srcRect.height() - this.tempRect.height()) / 2;
            this.srcRect.inset(Math.abs(dx), Math.abs(dy));
            drawingCanvas.drawBitmap(bitmap, this.srcRect, this.dstRect, this.paint);
        } else if (this.scaleType == ImageView.ScaleType.MATRIX && this.imageMatrix != null) {
            this.bitmapRectF.set(0.0F, 0.0F, bitmap.getWidth(), bitmap.getHeight());
            this.imageMatrix.mapRect(this.bitmapRectF);
            this.bitmapRectF.offset(left, top);
            drawingCanvas.drawBitmap(bitmap, null, this.bitmapRectF, this.paint);
        } else {
            BitmapDrawable bitmapDrawable = new BitmapDrawable(this.context.getResources(), bitmap);
            bitmapDrawable.setBounds(left, top, right, bottom);
            bitmapDrawable.draw(drawingCanvas);
        }
    }
}
