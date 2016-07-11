package com.ttwishing.library.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;

/**
 * Created by kurt on 10/26/15.
 */
public class DrawingCanvas extends Canvas {

    private Bitmap bitmap; //Canvas所绘制出的bitmap

    public static DrawingCanvas newInstance(int w, int h) {
        return new DrawingCanvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888));
    }

    /**
     * 新建画板
     * @param bitmap
     */
    public DrawingCanvas(Bitmap bitmap) {
        super(bitmap);
        this.bitmap = bitmap;
    }

    public boolean setSize(int w, int h) {
        if (w != getWidth() || h != getHeight()) {
            setBitmap(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888));
            return true;
        }
        return false;
    }

    public Bitmap getBitmap() {
        return this.bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        super.setBitmap(bitmap);
        this.bitmap = bitmap;
    }
}
