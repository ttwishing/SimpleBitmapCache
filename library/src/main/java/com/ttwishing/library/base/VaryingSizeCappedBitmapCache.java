package com.ttwishing.library.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Created by kurt on 11/13/15.
 *
 * 多种尺寸图片的BitmapCache
 *
 * 1.从network_result获取bitmap
 * 2.把File剪切成目标bitmap
 * 3.绘制出剪切后的图片
 * 3.子类需: 实现从disk读取bitmap, 实现保存bitmap到disk, 定制BitmapCache, DiskLurCache, 获取network_result, 从network_result获取bitmap
 */

public abstract class VaryingSizeCappedBitmapCache extends BaseRgbBitmapCache<VaryingSizeRefCountedBitmapDrawable, File> {

    private final Paint paint = new Paint();

    private final int tmpWidth;
    private final int tmpHeight;
    private boolean isEnabled;

    private Bitmap tmpLoadingBitmap;

    public VaryingSizeCappedBitmapCache(Context context, int width, int height, Bitmap.Config config, int poolSize) {
        super(context, width, height, config, poolSize);
        //防锯齿
        this.paint.setAntiAlias(true);
        //防抖动
        this.paint.setDither(true);
        //对Bitmap进行滤波处理
        this.paint.setFilterBitmap(true);

        this.tmpWidth = (int) (1.5D * width);
        this.tmpHeight = (int) (1.5D * height);

        this.isEnabled = true;
    }

    @Override
    public void start() {
        super.start();
        this.isEnabled = true;
    }

    @Override
    public void clearCache() {
        releaseBitmap();
        super.clearCache();
    }

    protected synchronized void releaseBitmap() {
        if (this.tmpLoadingBitmap != null) {
            this.tmpLoadingBitmap.recycle();
            this.tmpLoadingBitmap = null;
        }
        this.isEnabled = false;
    }

    @Override
    protected VaryingSizeRefCountedBitmapDrawable createRefCountedBitmapDrawable(RefCountedBitmapPool<VaryingSizeRefCountedBitmapDrawable> refCountedBitmapPool, BitmapDrawable bitmapDrawable) {
        return new VaryingSizeRefCountedBitmapDrawable(refCountedBitmapPool, bitmapDrawable, bitmapDrawable.getIntrinsicWidth(), bitmapDrawable.getIntrinsicHeight(), this.paint);
    }

    @Override
    protected synchronized final VaryingSizeRefCountedBitmapDrawable getBitmapFromNetworkResult(File file, RefCountedBitmapPool<VaryingSizeRefCountedBitmapDrawable> pool) {
        //网络结果不存在
        if (file == null || !file.exists()) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        int width = options.outWidth;
        int height = options.outHeight;

        Bitmap tmpBitmap = getTmpLoadingBitmap(width, height);

        if (tmpBitmap == null) {
            Log.d("VaryingSize", "i dont know, silently die");
            return null;
        }

        int left = 0;
        if (width > tmpBitmap.getWidth()) {
            left = (int) Math.ceil((width - tmpBitmap.getWidth()) / 2.0F);
            width = tmpBitmap.getWidth() - left * 2;
        }

        int top = 0;
        if (height > tmpBitmap.getHeight()) {
            top = (int) Math.ceil((height - tmpBitmap.getHeight()) / 2.0F);
            height = tmpBitmap.getHeight() - top * 2;
        }

        if (!cropToBitmap(file, tmpBitmap, left, top, width, height)) {
            Log.d("VaryingSize", "cant crop bitmap: " + file);
            return null;
        }

        int[] sizes = getDrawSizes(width, height);

        VaryingSizeRefCountedBitmapDrawable varyingSizeRefCountedBitmapDrawable = pool.get();

        Canvas canvas = new Canvas(varyingSizeRefCountedBitmapDrawable.getBitmap());

        //确认绘制的rect
        canvas.drawBitmap(tmpBitmap, new Rect(0, sizes[2], width, height - sizes[2]), new Rect(0, 0, sizes[0], sizes[1]), this.paint);
        externalDraw(canvas, sizes[0], sizes[1]);

        varyingSizeRefCountedBitmapDrawable.setWidth(sizes[0]);
        varyingSizeRefCountedBitmapDrawable.setHeight(sizes[1]);

        return varyingSizeRefCountedBitmapDrawable;
    }

    /**
     * 要保证缩略图的大小不能超过 (maxWidth, maxHeight)
     *
     * @param width  图片的实际width
     * @param height 图片的实际height
     * @return
     */
    protected synchronized final Bitmap getTmpLoadingBitmap(int width, int height) {
        if (!isEnabled) {
            return null;
        }

        //图片真实大小 大于tmp设置的大小,则取tmp 的大小,后面会进行剪切操作
        if (width > this.tmpWidth || height > this.tmpHeight) {
            width = this.tmpWidth;
            height = this.tmpHeight;
        }

        if (this.tmpLoadingBitmap != null) {
            //判断还有tmpLoadingBitmap是否可用
            if (this.tmpLoadingBitmap.getWidth() >= width && this.tmpLoadingBitmap.getHeight() >= height) {
                return this.tmpLoadingBitmap;
            }
            //临时bitmap不可用
            this.tmpLoadingBitmap.recycle();
            this.tmpLoadingBitmap = null;
        }

        if (width < 1 || height < 1) {
            Log.e("VaryingSize", "getTmpLoadingBitmap has been called w/ 0 width or height");
            return null;
        }

        try {
            if (isEnabled) {
                this.tmpLoadingBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            }
        } catch (OutOfMemoryError e) {
            Log.d("VaryingSize", "oom while creating tmp loading bitmap");
            handleOutOfMemoryError();
            this.tmpLoadingBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }
        return this.tmpLoadingBitmap;
    }


    /**
     * custom你的绘制方法
     *
     * @param canvas
     * @param width
     * @param height
     */
    protected abstract void externalDraw(Canvas canvas, int width, int height);

    /**
     * custom你的剪切方法
     *
     * @param file
     * @param bitmap
     * @param left
     * @param top
     * @param width
     * @param height
     * @return
     */
    protected abstract boolean cropToBitmap(File file, Bitmap bitmap, int left, int top, int width, int height);

    /**
     * 以width为基准，获取正确的大小
     *
     * @param width  图片的实际width
     * @param height 图片的实际height
     * @return
     */
    public int[] getDrawSizes(int width, int height) {
        float radio = (float) this.width / width;
        int sHeight = Math.round(radio * height);//以width的比例，算出高的大小
        int top;
        int w = this.width;
        int h;
        if (sHeight > this.height) {//当预计的高度 > 要求的高度
            h = this.height;//取为 要求的高度
            top = Math.round((height - this.height / radio) / 2.0F); //所以 高度上要截取掉一部分
        } else {
            h = sHeight;//
            top = 0;
        }
        return new int[]{w, h, top};
    }

    public Paint getPaint() {
        return this.paint;
    }

    public class Metadata implements Serializable {
        int realHeight;
        int realWidth;

        public Metadata() {
        }

        public Metadata(int realWidth, int realHeight) {
            this.realWidth = realWidth;
            this.realHeight = realHeight;
        }

        public int getRealWidth() {
            return this.realWidth;
        }

        public int getRealHeight() {
            return this.realHeight;
        }

        public void read(InputStream is) throws IOException {
            this.realHeight = 0;
            this.realWidth = 0;
            this.realWidth = ((is.read() << 24) + (is.read() << 16) + (is.read() << 8) + is.read());
            this.realHeight = ((is.read() << 24) + (is.read() << 16) + (is.read() << 8) + is.read());
        }

        public void write(OutputStream os) throws IOException {
            os.write((byte) (this.realWidth >> 24));
            os.write((byte) (this.realWidth >> 16));
            os.write((byte) (this.realWidth >> 8));
            os.write((byte) this.realWidth);
            os.write((byte) (this.realHeight >> 24));
            os.write((byte) (this.realHeight >> 16));
            os.write((byte) (this.realHeight >> 8));
            os.write((byte) this.realHeight);
        }
    }
}
