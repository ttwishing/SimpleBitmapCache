package com.ttwishing.library;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;

import com.ttwishing.library.base.VaryingSizeCappedBitmapCache;
import com.ttwishing.library.base.VaryingSizeRefCountedBitmapDrawable;
import com.ttwishing.library.base.util.IOUtils;
import com.ttwishing.library.disk.DiskLruCache;
import com.ttwishing.library.util.ThreadUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by kurt on 11/13/15.
 * <p/>
 * 多种尺寸图片的BitmapCache,适合程序的主list中的imageView
 * <p/>
 * 1.实现从disk读取bitmap,
 * 2.实现保存bitmap到disk,
 * 3.定制BitmapCache,
 * 4.定制DiskLurCache,
 * 5.获取network_result,
 * 6.从network_result获取bitmap
 */
public class WishingVaryingSizeCappedBitmapCache extends VaryingSizeCappedBitmapCache implements WishingCacheHelper.Callback {

    private final WishingCacheHelper wishingCacheHelper;
    private DiskLruCache diskLruCache;
    private RefCountingMemoryCache<VaryingSizeRefCountedBitmapDrawable> memoryCache;

    public WishingVaryingSizeCappedBitmapCache(Context context, int width, int height) {
        super(context, width, height, Bitmap.Config.ARGB_8888, 4);
        try {
            //因为图片可能有大有小,超时时间稍长
            this.wishingCacheHelper = new WishingCacheHelper(context, 120 * 1000, this);

            //此处不计数量的存储,存储对象WeakReference
            this.memoryCache = new RefCountingMemoryCache<VaryingSizeRefCountedBitmapDrawable>() {
                Map<String, Entry> map = new ConcurrentHashMap();

                @Override
                public VaryingSizeRefCountedBitmapDrawable get(String key) {
                    Entry entry = this.map.get(key);
                    if (entry == null) {
                        return null;
                    }
                    VaryingSizeRefCountedBitmapDrawable varyingSizeRefCountedBitmapDrawable = entry.get();
                    if (varyingSizeRefCountedBitmapDrawable == null) {
                        this.map.remove(key);
                    }
                    return varyingSizeRefCountedBitmapDrawable;
                }

                @Override
                public void clear() {
                    this.map.clear();
                }

                @Override
                public void put(String key, VaryingSizeRefCountedBitmapDrawable refCountedBitmapDrawable) {
                    this.map.put(key, new Entry(refCountedBitmapDrawable));
                }
            };

            //index 0: file数据, index 1: metadata数据
            this.diskLruCache = this.wishingCacheHelper.newDiskLruCache(context, "moment-photos", 2, perMemorySize, 5);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //=========WishingCacheHelper.Callback==========
    @Override
    public void onStart() {
        super.start();
    }

    @Override
    public void onStop() {
        this.countCancelled = this.countCancelled + 1;
    }

    @Override
    public void onDestroy() {
        super.clearCache();
    }

    //=====================================
    @Override
    protected RefCountingMemoryCache<VaryingSizeRefCountedBitmapDrawable> getBitmapMemoryCache() {
        return this.memoryCache;
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
    protected void readFromDiskLruCache(DiskLruCache.Snapshot snapshot, VaryingSizeRefCountedBitmapDrawable varyingSizeRefCountedBitmapDrawable, BitmapRequest bitmapRequest, ProcessCheck processCheck) throws IOException {
        InputStream inputStream = null;
        Metadata metadata;
        try {
            inputStream = snapshot.getInputStream(1);
            metadata = new Metadata();
            metadata.read(inputStream);
            if (metadata.getRealWidth() < 1 || metadata.getRealHeight() < 1) {
                throw new IOException("cannot read metadata");
            }
            varyingSizeRefCountedBitmapDrawable.setWidth(metadata.getRealWidth());
            varyingSizeRefCountedBitmapDrawable.setHeight(metadata.getRealHeight());
        } catch (Throwable e) {
            throw new IOException("cannot read metadata");
        } finally {
            IOUtils.closeQuietly(inputStream);
        }

        File file = snapshot.getInputFile(0);
        Bitmap bitmap = varyingSizeRefCountedBitmapDrawable.getBitmap();
        if (!processCheck.isProcessCheck(bitmapRequest)) {
            this.countCancelled += 1;
            throw new IOException("stopping decoding because callback does not want it anymore");
        }

        //TODO 从file读取出bitmap, 最优的方法是native来实现
    }

    @Override
    protected void saveBitmapToDiskLruCache(VaryingSizeRefCountedBitmapDrawable varyingSizeRefCountedBitmapDrawable, DiskLruCache.Editor editor) throws IOException {
        File file = editor.newOutputFile(0);
        //TODO 将varyingSizeRefCountedBitmapDrawable保存到file, 最优的方法是native来实现

        OutputStream outputStream = null;
        try {
            Metadata metadata = new Metadata(varyingSizeRefCountedBitmapDrawable.getWidth(), varyingSizeRefCountedBitmapDrawable.getHeight());
            outputStream = editor.newOutputStream(1);
            metadata.write(outputStream);
            getDiskLruCache().flush();

        } finally {
            IOUtils.closeQuietly(outputStream);
        }
    }

    @Override
    protected void handleLoadResult(Callback callback, VaryingSizeRefCountedBitmapDrawable varyingSizeRefCountedBitmapDrawable, BitmapRequest bitmapRequest) {
        this.wishingCacheHelper.loadBitmapFinish(callback, varyingSizeRefCountedBitmapDrawable, bitmapRequest);
    }


    @Override
    protected BitmapDrawable newBitmapDrawable() {
        return new BitmapDrawable(this.appContext.getResources(), Bitmap.createBitmap(this.width, this.height, this.config));

    }

    @Override
    protected synchronized void handleOutOfMemoryError() {
        this.wishingCacheHelper.handleOutOfMemoryError();
    }

    /**
     * @param file
     * @param bitmap
     * @param left
     * @param top
     * @param width
     * @param height
     * @return
     */
    @Override
    protected boolean cropToBitmap(File file, Bitmap bitmap, int left, int top, int width, int height) {
        //TODO 剪切图片, 最好的方式是native来实现
        return true;
    }

    @Override
    protected void executeLoadTask(Runnable r) {
        ThreadUtil.execute(r);
    }

    @Override
    protected void externalDraw(Canvas canvas, int width, int height) {

    }

    class Entry {
        WeakReference<VaryingSizeRefCountedBitmapDrawable> varyingSizeRefCountedBitmapDrawableRef;
        int version;

        Entry(VaryingSizeRefCountedBitmapDrawable bitmapDrawable) {
            this.varyingSizeRefCountedBitmapDrawableRef = new WeakReference(bitmapDrawable);
            this.version = bitmapDrawable.getClaim();
        }

        public VaryingSizeRefCountedBitmapDrawable get() {
            VaryingSizeRefCountedBitmapDrawable bitmapDrawable = this.varyingSizeRefCountedBitmapDrawableRef.get();
            if (bitmapDrawable == null) {
                return null;
            }
            if (!bitmapDrawable.tryAcquire(this.version)) {
                //claim不同
                return null;
            }
            return bitmapDrawable;

        }
    }

}
