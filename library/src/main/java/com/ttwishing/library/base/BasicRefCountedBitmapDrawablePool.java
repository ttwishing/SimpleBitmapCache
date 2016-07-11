package com.ttwishing.library.base;

import android.graphics.drawable.BitmapDrawable;

/**
 * Created by kurt on 11/3/15.
 */
public abstract class BasicRefCountedBitmapDrawablePool extends RefCountedBitmapPool<RefCountedBitmapDrawable> {


    public BasicRefCountedBitmapDrawablePool(int capacity) {
        super(capacity);
    }

    @Override
    protected RefCountedBitmapDrawable createDrawableType(BitmapDrawable bitmapDrawable) {
        return new RefCountedBitmapDrawable(this, bitmapDrawable);
    }
}
