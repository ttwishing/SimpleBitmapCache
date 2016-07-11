package com.ttwishing.library;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.ttwishing.library.base.BaseRgbBitmapCache;
import com.ttwishing.library.base.RefCountedBitmapDrawable;
import com.ttwishing.library.base.util.IOUtils;
import com.ttwishing.library.disk.DiskLruCache;
import com.ttwishing.library.http.HttpTaskController;
import com.ttwishing.library.util.ReusableStringBuilderPool;
import com.ttwishing.library.util.ThreadUtil;
import com.ttwishing.library.util.VersionUtil;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by kurt on 11/13/15.
 */
public class WishingCacheHelper {

    private static final Map<String, DiskLruCache> sDiskLurCaches = new HashMap();
    private static long waitout = 1000L;
    private final long waitCount;

    private final Callback callback;

    //TODO 理应是单例模式
    private HttpTaskController httpTaskController;

    /**
     * @param context
     * @param requestTimeout 请求超时时间
     * @param callback
     * @throws IOException
     */
    WishingCacheHelper(Context context, int requestTimeout, Callback callback) throws IOException {
        this.waitCount = requestTimeout / waitout;
        this.callback = callback;
        this.httpTaskController = new HttpTaskController(ReusableStringBuilderPool.getInstance());
        init(context);
    }

    private void init(Context context) {
        if (context == context.getApplicationContext()) {
            Log.e("CacheHelper", "you don't want to creates these bitmap caches width application context");
        }
    }

    public void handleOutOfMemoryError() {
        //TODO do sth?
    }

    /**
     * 根据手机空闲空间的大小,选择合适的存放位置
     *
     * @param context
     * @param name
     * @param valueCount
     * @param perSize
     * @param count
     * @return
     * @throws IOException
     */
    protected DiskLruCache newDiskLruCache(Context context, String name, int valueCount, int perSize, int count) throws IOException {
        synchronized (sDiskLurCaches) {
            DiskLruCache diskLruCache = sDiskLurCaches.get(name);
            if (diskLruCache != null) {
                return diskLruCache;
            }

            float remainingSpaceOnPhone = IOUtils.getRemainingSpaceOnPhone();
            float remainingSpaceOnSDCard = IOUtils.getRemainingSpaceOnSDCard();
            float sizeInM = (perSize * count) / (1024f * 1024f);

            if (sizeInM > remainingSpaceOnPhone && sizeInM > remainingSpaceOnSDCard) {
                Log.e("CacheHelper", "don\\'t have enough space on the phone for a proper disk cache. do sth?");
            }

            File dir;
            if (remainingSpaceOnPhone >= remainingSpaceOnSDCard) {
                dir = new File(context.getFilesDir(), name);
            } else {
                dir = context.getExternalFilesDir(name);
            }
            try {
                diskLruCache = DiskLruCache.open(dir, VersionUtil.getVersionCodeFromManifest(context), valueCount, perSize * count);
            } catch (IOException e) {
                //当出现异常
                if (dir != null) {
                    dir = new File(context.getFilesDir(), name);
                } else if (remainingSpaceOnSDCard > sizeInM) {
                    dir = context.getExternalFilesDir(name);
                } else {
                    throw e;
                }
                diskLruCache = DiskLruCache.open(dir, VersionUtil.getVersionCodeFromManifest(context), valueCount, perSize * count);
            }

            sDiskLurCaches.put(name, diskLruCache);
            return diskLruCache;
        }

    }

    protected void loadBitmapFinish(final BaseRgbBitmapCache.Callback callback, final RefCountedBitmapDrawable bitmapDrawable, final BaseRgbBitmapCache.BitmapRequest bitmapRequest) {
        if (callback instanceof BaseRgbBitmapCache.RunnableCallback) {
            ThreadUtil.post(((BaseRgbBitmapCache.RunnableCallback) callback).newInstance(bitmapRequest, bitmapDrawable));

        } else {
            ThreadUtil.post(new BaseRgbBitmapCache.RunnableCallback() {

                @Override
                public void onLoadComplete(BaseRgbBitmapCache.BitmapRequest abitmapRequest, RefCountedBitmapDrawable abitmapDrawable) {
                    callback.onLoadComplete(bitmapRequest, bitmapDrawable);
                }

            });
        }
    }

    /**
     * 此处通过CountDownLatch执行严格的超时计数,而不依赖你的http请求设置
     * @param bitmapRequest
     * @param processCheck
     * @return
     */
    protected File getFileFromNetwork(BaseRgbBitmapCache.BitmapRequest bitmapRequest, BaseRgbBitmapCache.ProcessCheck processCheck) {
        if (TextUtils.isEmpty(bitmapRequest.getUrl())) {
            return null;
        }

        CountDownLatch countDownLatch = new CountDownLatch(1);
        WeakFileListener weakFileListener = new WeakFileListener(countDownLatch, bitmapRequest, processCheck);

        httpTaskController.loadImage(bitmapRequest.getUrl(), null, weakFileListener);
        int i = 0;
        //每隔1秒查看下是否成功获取，走到达到最大次数waitCount(即超时),
        while (i < waitCount) {
            try {
                countDownLatch.await(waitout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {

            }
            if (countDownLatch.getCount() == 0L || !processCheck.isProcessCheck(bitmapRequest)) {
                this.callback.onStop();
                break;
            }
            i++;
        }
        File file = weakFileListener.getFile();
        return file;
    }

    interface Callback {

        void onStart();

        void onStop();

        void onDestroy();

    }

    public class WeakFileListener implements HttpTaskController.HttpDiskCacheListener {
        private File file;
        private final WeakReference<CountDownLatch> countDownLatchRef;
        private final BaseRgbBitmapCache.BitmapRequest bitmapRequest;
        private final BaseRgbBitmapCache.ProcessCheck processCheck;

        public WeakFileListener(CountDownLatch countDownLatch, BaseRgbBitmapCache.BitmapRequest bitmapRequest, BaseRgbBitmapCache.ProcessCheck processCheck) {
            this.countDownLatchRef = new WeakReference(countDownLatch);
            this.bitmapRequest = bitmapRequest;
            this.processCheck = processCheck;
        }

        private void finish() {
            CountDownLatch countDownLatch = this.countDownLatchRef.get();
            if (countDownLatch != null) {
                countDownLatch.countDown();
            }
        }

        public File getFile() {
            return this.file;
        }

        @Override
        public void onHttpDiskCacheSuccess(String url, String paramString2, File file) {
            this.file = file;
            finish();
        }

        @Override
        public void onHttpDiskCacheFailed(String url, String paramString2) {
            finish();
        }

        @Override
        public boolean isProcessCheck(String paramString1, String paramString2) {
            if (this.countDownLatchRef.get() != null) {
                if (this.processCheck.isProcessCheck(this.bitmapRequest)) {
                    return true;
                }
            }
            return false;
        }


    }
}
