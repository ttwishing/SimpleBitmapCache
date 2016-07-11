package com.ttwishing.library.base;

import android.util.LruCache;

/**
 * Created by kurt on 12/5/15.
 */
public class RefCountingLruCache<DrawableType extends RefCountedBitmapDrawable> implements BaseRgbBitmapCache.RefCountingMemoryCache<DrawableType> {

    final LruCache<String, DrawableType> lruCache;
    final int capacity;

    public RefCountingLruCache(int capacity) {
        this.capacity = capacity;

        //以数量为计量的缓存
        this.lruCache = new LruCache<String, DrawableType>(capacity) {

            @Override
            protected void entryRemoved(boolean evicted, String key, DrawableType oldValue, DrawableType newValue) {
                if (newValue != oldValue) {
                    oldValue.release();
                }
            }
        };
    }

    @Override
    public DrawableType get(String key) {
        DrawableType refCountedBitmapDrawable = this.lruCache.get(key);
        if (refCountedBitmapDrawable != null) {
            refCountedBitmapDrawable.acquire();
            return refCountedBitmapDrawable;
        }
        return null;
    }

    @Override
    public void put(String key, DrawableType drawableType) {
        drawableType.acquire();
        this.lruCache.put(key, drawableType);
    }

    @Override
    public void clear() {
        this.lruCache.evictAll();
    }
}
