package com.ttwishing.library;

import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;
import android.util.LruCache;

import com.ttwishing.library.util.ReusableStringBuilderPool;

import java.lang.ref.WeakReference;

/**
 * Created by kurt on 10/28/15.
 */
public class WishingBitmapMemoryCache {
    int num_strong_hit = 0;
    int num_soft_hit = 0;
    int num_soft_hit_miss = 0;
    int num_miss = 0;

    int num_big_hit = 0;
    int num_big_hit_miss = 0;

    int num_added_big = 0;
    int num_added_small = 0;

    //双级缓存设计
    //第一级,此中数据在某下情景下,移除后置入第二缓存
    private final LruCache<String, CachedBitmap> mBitmapLruCache;
    //第二级,GC回收敏感,适合大尺寸图片,此处的大尺寸定义为210*210
    private final LruCache<String, CachedBitmap> mWeakRefBitmapLruCache;

    final ReusableStringBuilderPool reusableStringBuilderPool;

    public WishingBitmapMemoryCache(ReusableStringBuilderPool pool) {
        this.reusableStringBuilderPool = pool;

        //以memory size为计量的缓存
        this.mBitmapLruCache = new LruCache<String, CachedBitmap>(210 * 210 * 8 * 10) {
            @Override
            protected int sizeOf(String key, CachedBitmap value) {
                return value.getByteCount();
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, CachedBitmap oldValue, CachedBitmap newValue) {
                if ((!evicted || newValue == null) && oldValue.getBitmap() != null) {
                    oldValue.bitmap = null;
                    mWeakRefBitmapLruCache.put(key, newValue);
                }
            }
        };

        this.mWeakRefBitmapLruCache = new LruCache<String, CachedBitmap>(210 * 210 * 8 * 20) {
            @Override
            protected int sizeOf(String key, CachedBitmap value) {
                return value.getByteCount();
            }
        };
    }

    public void clearCache() {
        this.mBitmapLruCache.evictAll();
        this.mWeakRefBitmapLruCache.evictAll();
        System.gc();
    }

    public synchronized Bitmap getBitmap(String url, String suffix) {
        return getBitmap(generateUrl(url, 0, 0, suffix));
    }

    public synchronized Bitmap getBitmap(String url, int width, int height, String suffix) {
        return getBitmap(generateUrl(url, width, height, suffix));
    }

    private Bitmap getBitmap(String url) {
        CachedBitmap cachedBitmap = mBitmapLruCache.get(url);
        if (cachedBitmap != null) {
            this.num_strong_hit += 1;
            return cachedBitmap.getBitmap();
        }

        cachedBitmap = this.mWeakRefBitmapLruCache.get(url);

        if (cachedBitmap != null) {
            this.num_soft_hit += 1;
            Bitmap bitmap = cachedBitmap.getBitmap();
            if (bitmap == null) {
                this.num_soft_hit_miss += 1;
            }
            return bitmap;
        }

        this.num_miss += 1;
        return null;
    }

    public synchronized void putBitmap(String url, String suffix, Bitmap bitmap) {
        putBitmap(url, 0, 0, suffix, bitmap);
    }

    public synchronized void putBitmap(String url, int width, int height, String suffix, Bitmap bitmap) {
        putCachedBitmap(generateUrl(url, width, height, suffix), new CachedBitmap(bitmap));
    }

    private void putCachedBitmap(String url, CachedBitmap cachedBitmap) {
        try {
            if (cachedBitmap.size() > 44100) {
                this.num_added_big += 1;
                cachedBitmap.bitmap = null;
                this.mWeakRefBitmapLruCache.put(url, cachedBitmap);
            } else {
                this.num_added_small += 1;
                this.mBitmapLruCache.put(url, cachedBitmap);
            }
        } catch (Exception e) {
            //trying to cache bitmap but it does not exist or recycled
        }
    }

    /**
     * 构建url,可根据你的服务器情况而定
     *
     * @param url
     * @param width
     * @param height
     * @param suffix
     * @return
     */
    private String generateUrl(String url, int width, int height, String suffix) {
        if (suffix == null) {
            return url;
        }
        return reusableStringBuilderPool.pop().
                append(url)
                .append("x")
                .append(width)
                .append("x")
                .append(height)
                .append("x")
                .append(suffix)
                .toStringWithRelease();
    }

    public class CachedBitmap {
        private final WeakReference<Bitmap> bitmapRef;
        private Bitmap bitmap;
        private final int byteCount;

        private CachedBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            this.bitmapRef = new WeakReference(bitmap);
            if (bitmap == null) {
                this.byteCount = 0;
            } else {
                this.byteCount = getByteCount(bitmap);
            }
        }

        private int getByteCount(Bitmap bitmap) {
            if (bitmap == null) {
                return 0;
            }
            if (Build.VERSION.SDK_INT >= 12) {
                return bitmap.getByteCount();
            } else {
                return bitmap.getRowBytes() * bitmap.getHeight();
            }
        }

        private Bitmap getBitmap() {
            if (bitmap != null) {
                return bitmap;
            }
            if (bitmapRef != null) {
                return bitmapRef.get();
            } else {
                return null;
            }
        }

        private int getByteCount() {
            return this.byteCount;
        }

        private int size() throws Exception {
            Bitmap bitmap = getBitmap();
            if (bitmap == null || bitmap.isRecycled()) {
                throw new Exception("bitmap does not exist");
            }
            return bitmap.getWidth() * bitmap.getHeight();
        }
    }
}
