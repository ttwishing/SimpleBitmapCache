package com.ttwishing.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

import com.ttwishing.library.base.FixedSizeBitmapCache;
import com.ttwishing.library.base.RefCountedBitmapDrawable;
import com.ttwishing.library.base.RefCountingLruCache;
import com.ttwishing.library.disk.DiskLruCache;
import com.ttwishing.library.util.ThreadUtil;

import java.io.File;
import java.io.IOException;

/**
 * Created by kurt on 12/5/15.
 *
 * 1.实现定制BitmapCache
 * 2.实现定制DiskLurCache
 * 3.实现获取network_result
 * 3.子类需: 从network_result获取bitmap
 */
public abstract class BaseWishingFixedSizeBitmapCache extends FixedSizeBitmapCache<File> implements WishingCacheHelper.Callback {

    private DiskLruCache diskLruCache;
    private RefCountingMemoryCache<RefCountedBitmapDrawable> bitmapMemoryCache;

    private final WishingCacheHelper wishingCacheHelper;

    public BaseWishingFixedSizeBitmapCache(Context context, String diskCacheName, int width, int height, Bitmap.Config config, int memoryCacheSize, int poolSize, int count) {
        super(context, width, height, config, poolSize);
        try {
            //主要针对一些小图片,稍小
            this.wishingCacheHelper = new WishingCacheHelper(context, 60 * 1000, this);

            this.bitmapMemoryCache = new RefCountingLruCache(memoryCacheSize);
            this.diskLruCache = this.wishingCacheHelper.newDiskLruCache(context, diskCacheName, 1, this.perMemorySize, count);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //=========WishingCacheHelper.Callback============
    @Override
    public void onStart() {
        super.start();
    }

    public void onStop() {
        this.countCancelled = this.countCancelled + 1;
    }

    public void onDestroy() {
        super.clearCache();
    }


    //==========FixedSizeBitmapCache==============
    @Override
    protected RefCountingMemoryCache<RefCountedBitmapDrawable> getBitmapMemoryCache() {
        return this.bitmapMemoryCache;
    }

    @Override
    protected DiskLruCache getDiskLruCache() {
        return this.diskLruCache;
    }

    @Override
    protected File getNetworkResult(BitmapRequest bitmapRequest, ProcessCheck processCheck) {
        return this.wishingCacheHelper.getFileFromNetwork(bitmapRequest, processCheck);
    }

    @Override
    protected BitmapDrawable newBitmapDrawable() {
        return newBitmapDrawable(Bitmap.createBitmap(this.width, this.height, this.config));
    }

    protected BitmapDrawable newBitmapDrawable(Bitmap bitmap) {
        if (bitmap.getWidth() != this.width || bitmap.getHeight() != this.height) {
            bitmap = Bitmap.createScaledBitmap(bitmap, this.width, this.height, true);
        }
        return new BitmapDrawable(this.appContext.getResources(), bitmap);
    }

    @Override
    protected void handleOutOfMemoryError() {
        this.wishingCacheHelper.handleOutOfMemoryError();
    }

    @Override
    protected void executeLoadTask(Runnable task) {
        ThreadUtil.execute(task);
    }

    @Override
    protected void handleLoadResult(Callback callback, RefCountedBitmapDrawable refCountedBitmapDrawable, BitmapRequest bitmapRequest) {
        this.wishingCacheHelper.loadBitmapFinish(callback, refCountedBitmapDrawable, bitmapRequest);
    }

}
