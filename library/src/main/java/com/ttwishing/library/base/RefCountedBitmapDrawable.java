package com.ttwishing.library.base;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

/**
 * Created by kurt on 11/3/15.
 */
public class RefCountedBitmapDrawable {

    private final RefCountedBitmapPool pool;

    private final BitmapDrawable bitmapDrawable;

    private int refCount = 0;

    private int claim = 0;
    private long claimTime;
    int id;

    RefCountedBitmapDrawable(RefCountedBitmapPool pool, BitmapDrawable bitmapDrawable) {
        this.pool = pool;
        this.bitmapDrawable = bitmapDrawable;
    }

    /**
     * 绘制前判断是否已回收
     *
     * @param canvas
     */
    public void draw(Canvas canvas) {
        if (refCount < 1) {
            throw new RuntimeException("cannot draw w/o referencing");
        }
        bitmapDrawable.draw(canvas);
    }

    public BitmapDrawable getBitmapDrawable() {
        return this.bitmapDrawable;
    }

    public Bitmap getBitmap() {
        return bitmapDrawable.getBitmap();
    }

    /**
     * 引用声明
     */
    protected synchronized void claim() {
        if (this.refCount != 0) {
            throw new RuntimeException("cannot claim because ref count is " + refCount);
        }
        this.claimTime = System.currentTimeMillis();
        this.claim += 1;
        this.refCount = 1;
    }

    /**
     * 根据claim来获取使用权
     *
     * @param claim
     * @return
     */
    public synchronized boolean tryAcquire(int claim) {
        if (claim != claim) {
            return false;
        }
        acquire();
        return true;
    }

    /**
     * 获取使用权
     */
    public synchronized void acquire() {
        if (bitmapDrawable.getBitmap().isRecycled()) {
            throw new RuntimeException("trying to acquire a recycled bitmap. sth is wrong");
        }
        if (this.refCount > 0) {
            this.refCount += 1;
            return;
        }
        throw new RuntimeException("cannot acquire bitmap. no one should try this, sth is wrong");
    }

    protected synchronized void reset() {
        this.refCount = 0;
        this.bitmapDrawable.setAlpha(255);
    }

    public synchronized void release() {
        this.refCount -= 1;
        if (refCount <= 0) {
            Log.d("RefCounted", "release");
            this.claim += 1;
            pool.release(this);
        }
    }

    public synchronized void recycle() {
        if (refCount != 0) {
            throw new RuntimeException("trying to recycle bitmap but there are references to me :");
        }
        bitmapDrawable.getBitmap().recycle();
    }

    public synchronized int getClaim() {
        return claim;
    }

}
