package com.ttwishing.library.base;

import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by kurt on 11/3/15.
 */
public abstract class RefCountedBitmapPool<DrawableType extends RefCountedBitmapDrawable> {

    private boolean enabled;

    private final int capacity;
    private ArrayBlockingQueue<DrawableType> queue;//bnQ

    protected int created = 0;
    protected int reused = 0;
    protected int offered = 0;
    protected int recycled = 0;

    public RefCountedBitmapPool(int capacity) {
        this.capacity = capacity;
        this.queue = new ArrayBlockingQueue(this.capacity, false);
        this.enabled = true;
    }

    /**
     * 开启
     */
    public void start() {
        this.enabled = true;
    }

    /**
     * 关闭, 将对象池中的实例回收, 只是回收,并不清空
     */
    public void stop() {
        while (true) {
            RefCountedBitmapDrawable refCountedBitmapDrawable = this.queue.poll();
            if (refCountedBitmapDrawable == null) {
                break;
            }
            refCountedBitmapDrawable.recycle();
        }
        this.enabled = false;
    }

    /**
     * 从对象池中获取
     * @return
     */
    public DrawableType get() {
        DrawableType drawableType = this.queue.poll();
        if (drawableType != null) {
            //从对象池中获取
            this.reused = this.reused + 1;
            //并对引用进行声明
            drawableType.claim();
            return drawableType;
        }

        BitmapDrawable bitmapDrawable;
        try {
            bitmapDrawable = getBitmapDrawable();
        } catch (OutOfMemoryError e) {
            Log.d("RefCountedBitmapPool", "oom while creating bitmap drawable for pool");
            handleOutOfMemoryError();
            bitmapDrawable = getBitmapDrawable();
        }
        return generateDrawableType(bitmapDrawable);
    }

    protected abstract BitmapDrawable getBitmapDrawable();

    /**
     * 创建,并设置标识
     *
     * @param bitmapDrawable
     * @return
     */
    public DrawableType generateDrawableType(BitmapDrawable bitmapDrawable) {
        DrawableType drawableType = createDrawableType(bitmapDrawable);
        this.created += 1;
        //设置标识
        drawableType.id = this.created;
        //引用声明
        drawableType.claim();
        return drawableType;
    }

    protected abstract DrawableType createDrawableType(BitmapDrawable bitmapDrawable);

    protected abstract void handleOutOfMemoryError();

    public void release(DrawableType drawableType) {
        drawableType.reset();
        if (this.enabled && this.queue.offer(drawableType)) {
            //添加到对象池
            this.offered += 1;
        } else {
            //回收
            this.recycled += 1;
            drawableType.recycle();
        }
    }
}
