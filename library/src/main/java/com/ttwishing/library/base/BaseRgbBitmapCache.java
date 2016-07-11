package com.ttwishing.library.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import com.ttwishing.library.base.sync.NamedLockPool;
import com.ttwishing.library.disk.DiskLruCache;

import java.io.IOException;

/**
 * Created by kurt on 11/13/15.
 * <p/>
 * 缓存基类
 */
public abstract class BaseRgbBitmapCache<DrawableType extends RefCountedBitmapDrawable, NetworkResultType> {

    private static final ProcessCheck processCheck = new ProcessCheck() {////bny
        @Override
        public boolean isProcessCheck(BitmapRequest bitmapRequest) {
            return true;
        }
    };

    protected int countFromMemory = 0;
    protected int countFromDisk = 0;
    protected int countFromNetwork = 0;
    protected int countCancelled = 0;

    protected final Context appContext;
    //锁池,此处只控制并发,并不需要控制并发量
    private final NamedLockPool namedLockPool;
    //对象池
    private final RefCountedBitmapPool<DrawableType> bitmapPool;

    protected final int height;
    protected final int width;
    protected final Bitmap.Config config;//bns
    //单体缓存大小,针对固定大小的图像,比如人物头像
    protected final int perMemorySize;

    public BaseRgbBitmapCache(Context context, int width, int height, Bitmap.Config config, int poolSize) {
        this.appContext = context.getApplicationContext();
        this.width = width;
        this.height = height;
        this.config = config;

        this.perMemorySize = width * height * sizePerPix(config);
        this.namedLockPool = new NamedLockPool(10, true);

        this.bitmapPool = new RefCountedBitmapPool<DrawableType>(poolSize) {

            @Override
            protected BitmapDrawable getBitmapDrawable() {
                return BaseRgbBitmapCache.this.newBitmapDrawableSafe();
            }

            @Override
            protected DrawableType createDrawableType(BitmapDrawable bitmapDrawable) {
                return BaseRgbBitmapCache.this.createRefCountedBitmapDrawable(this, bitmapDrawable);
            }

            @Override
            protected void handleOutOfMemoryError() {
                BaseRgbBitmapCache.this.handleOutOfMemoryError();
            }
        };
    }

    private BitmapDrawable newBitmapDrawableSafe() {
        BitmapDrawable bitmapDrawable;
        try {
            bitmapDrawable = newBitmapDrawable();
        } catch (OutOfMemoryError e) {
            handleOutOfMemoryError();
            bitmapDrawable = newBitmapDrawable();
        }
        return bitmapDrawable;
    }

    protected abstract BitmapDrawable newBitmapDrawable();

    protected abstract void handleOutOfMemoryError();

    protected abstract DrawableType createRefCountedBitmapDrawable(RefCountedBitmapPool<DrawableType> refCountedBitmapPool, BitmapDrawable bitmapDrawable);

    public void start() {
        this.bitmapPool.start();
    }

    public void clearCache() {
        getBitmapMemoryCache().clear();
        this.bitmapPool.stop();
    }

    /**
     * 针对单体,获取bitmap,UI线程调用
     * step1: 同步从缓存中获取, 如果缓存中不存在,执行step2
     * step2: 异步获取
     *
     * @param bitmapRequest
     * @param callback
     * @return
     */
    public boolean loadBitmap(final BitmapRequest bitmapRequest, final Callback callback) {
        //优先从缓存中获取
        DrawableType drawableType = getBitmapFromCache(bitmapRequest);
        if (drawableType != null) {
            handleLoadResult(callback, drawableType, bitmapRequest);
            return true;
        }

        //执行异步下载
        executeLoadTask(new Runnable() {

            @Override
            public void run() {
                DrawableType drawableType = getBitmap(bitmapRequest, callback);
                handleLoadResult(callback, drawableType, bitmapRequest);
            }
        });
        return false;
    }

    protected abstract void handleLoadResult(Callback callback, DrawableType drawableType, BitmapRequest bitmapRequest);

    /**
     * custom你的线程控制
     *
     * @param task
     */
    protected abstract void executeLoadTask(Runnable task);

    /**
     * 获取bitmap, ui线程需异步执行
     * step1: 从缓存中获取
     * step2: 从disk中获取
     * step3: 从network获取,并保存
     *
     * @param bitmapRequest
     * @param processCheck
     * @return
     */
    public DrawableType getBitmap(BitmapRequest bitmapRequest, ProcessCheck processCheck) {
        //缓存中获取
        DrawableType drawableType = getBitmapFromCache(bitmapRequest);
        if (drawableType != null) {//cond_0
            return drawableType;
        }
        //缓存中无,需要执行较耗时的获取方式, 此时判断任务是否取消
        if (!processCheck.isProcessCheck(bitmapRequest)) {
            this.countCancelled = this.countCancelled + 1;
            return null;
        }
        try {
            //获取锁资源
            this.namedLockPool.lock(bitmapRequest.getKey());

            //从disk中获取
            drawableType = getBitmapFromDisk(bitmapRequest, processCheck);
            if (drawableType != null) {
                return drawableType;
            }

            //从网络中获取NetworkResult
            NetworkResultType networkResult = getNetworkResultSafe(bitmapRequest, processCheck);
            if (networkResult == null) {
                Log.e("BitmapCache", "could not or did not load bitmap from network");
                return null;
            }

            //保存NetworkResult, 并返回结果
            return getBitmapFromNetworkResultAndSave(bitmapRequest, networkResult, bitmapPool);

        } catch (Throwable t) {
            Log.e("BitmapCache", "get bitmap failed");
        } finally {
            this.namedLockPool.unlock(bitmapRequest.getKey());
        }
        return null;
    }

    /**
     * 从缓存中获取
     *
     * @param bitmapRequest
     * @return
     */
    private DrawableType getBitmapFromCache(BitmapRequest bitmapRequest) {
        DrawableType drawableType = getBitmapMemoryCache().get(bitmapRequest.getKey());
        if (drawableType != null) {
            this.countFromMemory += 1;
        }
        return drawableType;
    }

    /**
     * custom你的BitmapMemoryCache
     *
     * @return
     */
    protected abstract RefCountingMemoryCache<DrawableType> getBitmapMemoryCache();

    /**
     * 从disk中获取
     *
     * @param bitmapRequest
     * @param processCheck
     * @return
     */
    private DrawableType getBitmapFromDisk(BitmapRequest bitmapRequest, ProcessCheck processCheck) {
        //再次尝试从缓存获取
        DrawableType drawableType = getBitmapFromCache(bitmapRequest);
        if (drawableType != null) {
            return drawableType;
        }

        //因此后将执行较耗时操作,确认任务是否取消
        if (!processCheck.isProcessCheck(bitmapRequest)) {
            this.countCancelled += 1;
            return null;
        }

        DiskLruCache.Snapshot snapshot = null;
        try {
            snapshot = getDiskLruCache().get(bitmapRequest.getKey());
        } catch (IOException e) {

        }
        if (snapshot == null) {
            return null;
        }

        //再次确认任务是否取消
        if (!processCheck.isProcessCheck(bitmapRequest)) {
            this.countCancelled += 1;
            return null;
        }

        //从硬盘中获取
        drawableType = getBitmapFromDiskSnapshot(snapshot, bitmapRequest, processCheck);

        if (drawableType != null) {
            this.countFromDisk += 1;
            getBitmapMemoryCache().put(bitmapRequest.getKey(), drawableType);
            drawableType.acquire();
            return drawableType;
        }
        return null;
    }

    /**
     * custom你的DiskLruCache
     *
     * @return
     */
    protected abstract DiskLruCache getDiskLruCache();

    /**
     * @param snapshot
     * @param bitmapRequest
     * @param processCheck
     * @return
     */
    private synchronized DrawableType getBitmapFromDiskSnapshot(DiskLruCache.Snapshot snapshot, BitmapRequest bitmapRequest, ProcessCheck processCheck) {
        DrawableType drawableType = null;
        try {
            drawableType = bitmapPool.get();
            readFromDiskLruCache(snapshot, drawableType, bitmapRequest, processCheck);
        } catch (IOException e) {
            if (drawableType != null) {
                drawableType.release();
            }
            drawableType = null;
        } finally {
            snapshot.close();

        }
        return drawableType;
    }

    //将数据读取到DrawableType
    protected abstract void readFromDiskLruCache(DiskLruCache.Snapshot snapshot, DrawableType drawableType, BitmapRequest bitmapRequest, ProcessCheck processCheck) throws IOException;

    /**
     * 尝试从网络获取
     *
     * @param bitmapRequest
     * @param processCheck
     * @return
     */
    private NetworkResultType getNetworkResultSafe(BitmapRequest bitmapRequest, ProcessCheck processCheck) {
        try {

            NetworkResultType result = getNetworkResult(bitmapRequest, processCheck);
            if (result != null) {
                this.countFromNetwork += 1;
            }
            return result;
        } catch (Throwable t) {
            Log.e("BitmapCache", "get from network failed");
        }
        return null;
    }

    /**
     * 网络请求
     *
     * @param bitmapRequest
     * @param processCheck
     * @return
     */
    protected abstract NetworkResultType getNetworkResult(BitmapRequest bitmapRequest, ProcessCheck processCheck);


    private synchronized DrawableType getBitmapFromNetworkResultAndSave(BitmapRequest bitmapRequest, NetworkResultType networkResult, RefCountedBitmapPool<DrawableType> refCountedBitmapPool) {
        DrawableType drawableType = getBitmapFromNetworkResult(networkResult, refCountedBitmapPool);

        if (drawableType == null) {
            Log.e("BitmapCache", "getBitmapFromNetworkResult failed");
            return null;
        }
        DiskLruCache diskLruCache = getDiskLruCache();
        try {
            DiskLruCache.Editor editor = diskLruCache.edit(bitmapRequest.getKey());
            if (editor != null) {
                saveBitmapToDiskLruCache(drawableType, editor);
                editor.commit();
            }
            return drawableType;
        } catch (IOException e) {
            Log.d("BitmapCache", "could not save drawable");
        }
        return null;
    }

    protected abstract DrawableType getBitmapFromNetworkResult(NetworkResultType networkResult, RefCountedBitmapPool<DrawableType> bitmapPool);

    protected abstract void saveBitmapToDiskLruCache(DrawableType drawableType, DiskLruCache.Editor editor) throws IOException;

    /**
     * 每像素字节数
     *
     * @param config
     * @return
     */
    private int sizePerPix(Bitmap.Config config) {
        switch (config) {
            case ALPHA_8:
                return 1;
            case RGB_565:
                return 2;
            case ARGB_4444:
                return 2;
            case ARGB_8888:
                return 4;
            default:
                throw new RuntimeException("unsupported bitmap config. ");

        }
    }

    /**
     * 加载回调
     *
     * @param <DrawableType>
     */
    public interface Callback<DrawableType extends RefCountedBitmapDrawable> extends ProcessCheck {
        void onLoadComplete(BitmapRequest bitmapRequest, DrawableType refCountedBitmapDrawable);
    }

    public interface ProcessCheck {
        boolean isProcessCheck(BitmapRequest bitmapRequest);
    }

    public interface BitmapRequest {
        String getKey();

        String getUrl();
    }

    /**
     * bitmap缓存
     *
     * @param <DrawableType>
     */
    public interface RefCountingMemoryCache<DrawableType extends RefCountedBitmapDrawable> {

        DrawableType get(String key);

        void put(String key, DrawableType refCountedBitmapDrawable);

        void clear();
    }


    public static abstract class RunnableCallback<DrawableType extends RefCountedBitmapDrawable> implements Callback, Runnable {

        BitmapRequest bitmapRequest;
        DrawableType drawableType;

        public final synchronized RunnableCallback newInstance(BitmapRequest bitmapRequest, DrawableType drawableType) {
            if (this.drawableType != null) {
                this.drawableType.release();
            }
            this.bitmapRequest = bitmapRequest;
            this.drawableType = drawableType;
            return this;
        }

        @Override
        public boolean isProcessCheck(BitmapRequest bitmapRequest) {
            return true;
        }

        @Override
        public synchronized void run() {
            BitmapRequest ori_bitmapRequest = this.bitmapRequest;
            DrawableType ori_drawableType = this.drawableType;
            this.bitmapRequest = null;
            this.drawableType = null;
            onLoadComplete(ori_bitmapRequest, ori_drawableType);
        }
    }
}
