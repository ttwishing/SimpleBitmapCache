package com.ttwishing.library.base;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;

/**
 * Created by kurt on 11/13/15.
 */
public class VaryingSizeRefCountedBitmapDrawable extends RefCountedBitmapDrawable {

    private Rect srcRect;
    private Paint paint;

    protected VaryingSizeRefCountedBitmapDrawable(RefCountedBitmapPool refCountedBitmapPool, BitmapDrawable bitmapDrawable, int width, int height, Paint paint) {
        super(refCountedBitmapPool, bitmapDrawable);
        this.srcRect = new Rect(0, 0, width, height);
        this.paint = new Paint(paint);
    }

    @Override
    public void draw(Canvas canvas) {
        throw new RuntimeException("cann draw(canvas, dstRect)");
    }

    /**
     * 图片尺寸并不固定
     * @param canvas
     * @param dstRectF
     */
    public void draw(Canvas canvas, RectF dstRectF) {
        canvas.drawBitmap(getBitmap(), srcRect, dstRectF, paint);
    }

    public void setWidth(int width) {
        this.srcRect.right = width;
    }

    public int getWidth() {
        return this.srcRect.right;
    }

    public void setHeight(int height) {
        this.srcRect.bottom = height;
    }

    public int getHeight() {
        return this.srcRect.bottom;
    }

    public void setAlpha(int alpha) {
        if (alpha < 0) {
            alpha = 0;
        } else if (alpha > 255) {
            alpha = 255;
        }
        this.paint.setAlpha(alpha);
    }

    public boolean isOpaque() {
        if (getBitmapDrawable().getOpacity() == -1) {
            return true;
        }
        return false;
    }

}
