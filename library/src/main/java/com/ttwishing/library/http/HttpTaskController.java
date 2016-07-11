package com.ttwishing.library.http;

import android.content.Context;

import com.ttwishing.library.App;
import com.ttwishing.library.base.sync.NamedLockPool;
import com.ttwishing.library.base.util.IOUtils;
import com.ttwishing.library.tasks.PriorityExecutor;
import com.ttwishing.library.util.HashUtil;
import com.ttwishing.library.util.ReusableStringBuilderPool;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Created by kurt on 10/28/15.
 *
 * 下载任务并发,优先级, 并发量控制
 */
public class HttpTaskController {
    private static long sCurrentMaxPriority = Long.MAX_VALUE;
    private static long sCurrentMinPriority = 0L;

    private Context context = App.getInstance();

    //下载线程池
    private PriorityExecutor<ImageDownloadTask> mDownloadExecutor;

    private final Object mListenerLock = new Object();
    //监听池
    private final Map<String, List<HttpDiskCacheListener>> mUrlBasedListenersMap = new HashMap<>();

    //lock池,此处未限制并发量
    private NamedLockPool mDownloadLockPool = new NamedLockPool(10, true);

    //控制并发量
    private Semaphore mSemaphore = new Semaphore(10, false);

    //下载的文件保存路径
    private final File mDownloadCacheDir;


    private final ReusableStringBuilderPool mReusableStringBuilderPool;

    public HttpTaskController(ReusableStringBuilderPool stringBuilderPool) {
        this.mReusableStringBuilderPool = stringBuilderPool;
        this.mDownloadCacheDir = context.getExternalFilesDir("my-photos");
        this.mDownloadExecutor = new PriorityExecutor("http-disk-cache-download", 1, 4, 30, PriorityExecutor.PowerMode.NORMAL, false);
    }

    /**
     * 加载图片
     *
     * @param url
     * @param suffix   后缀
     * @param listener
     */
    public void loadImage(String url, String suffix, HttpDiskCacheListener listener) {
        if (StringUtils.isBlank(url)) {
            if (listener != null) {
                listener.onHttpDiskCacheFailed(url, suffix);
            }
            return;
        }

        if (!isExited(url, suffix, listener)) {
            download(url, suffix, listener);
        }
    }

    /**
     * 注册监听,并执行下载任务
     *
     * @param url
     * @param suffix
     * @param listener
     */
    private void download(String url, String suffix, HttpDiskCacheListener listener) {
        //注册监听
        synchronized (mListenerLock) {
            if (mUrlBasedListenersMap.containsKey(url)) {
                if (listener != null) {
                    (mUrlBasedListenersMap.get(url)).add(listener);
                }
            } else {
                LinkedList listeners = new LinkedList();
                if (listener != null) {
                    listeners.add(listener);
                }
                mUrlBasedListenersMap.put(url, listeners);
            }
        }

        executeDownload(url, suffix, true);
    }

    private boolean executeDownload(String url, String suffix, boolean isHighPriority) {
        return this.mDownloadExecutor.execute(new ImageDownloadTask(url, suffix, filePath(url, suffix), isHighPriority));
    }

    /**
     * 是否已下载, 如果已下载则回调
     *
     * @param url
     * @param suffix
     * @param listener
     * @return
     */
    private boolean isExited(String url, String suffix, HttpDiskCacheListener listener) {
        if (isExisted(url, suffix)) {
            if (listener != null) {
                listener.onHttpDiskCacheSuccess(url, suffix, filePath(url, suffix));
            }
            return true;
        }
        return false;
    }

    public boolean isExisted(String url, String suffix) {
        synchronized (mListenerLock) {
            if (!mUrlBasedListenersMap.containsKey(url)) {
                File path = filePath(url, suffix);
                if (path.exists()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 获取并移除监听
     *
     * @param url
     * @return
     */
    private List<HttpDiskCacheListener> getAndRemoveListenerList(String url) {
        synchronized (mListenerLock) {
            List localList = (List) mUrlBasedListenersMap.remove(url);
            return localList;
        }
    }

    /**
     * 根据 url和suffix获取File
     *
     * @param url
     * @param suffix
     * @return
     */
    private File filePath(String url, String suffix) {
        return new File(mDownloadCacheDir, fileName(url, suffix));
    }

    /**
     * 根据 url和suffix生成 filename
     *
     * @param url
     * @param suffix
     * @return
     */
    private String fileName(String url, String suffix) {
        if (suffix != null) {
            url = (mReusableStringBuilderPool.pop()).append(url).append("x").append(suffix).toStringWithRelease();
        }
        return HashUtil.md5(url);
    }

    /**
     * 获取优先级,
     * 当获取高的优先级时, 越早创建的优先级越高, sCurrentMaxPriority执行递减
     *
     * @param high
     * @return
     */
    private long getPriority(boolean high) {
        long priority;
        if (high) {
            priority = sCurrentMaxPriority;
            sCurrentMaxPriority = priority - 1L;
        } else {
            priority = sCurrentMinPriority;
            sCurrentMinPriority = priority - 1L;
        }
        return priority;
    }

    public interface HttpDiskCacheListener {
        void onHttpDiskCacheFailed(String url, String suffix);

        void onHttpDiskCacheSuccess(String url, String suffix, File file);

        boolean isProcessCheck(String url, String suffix);
    }

    class ImageDownloadTask implements PriorityExecutor.Item, Runnable {

        //retry次数
        int retryCount;
        //保存到的文件
        File savedFile;
        //锁资源获取许可等级
        int permits;
        //当前任务是高或低优先级
        boolean highPriority;
        //当前任务的优先级
        long priority;

        long created;
        String url;
        String suffix;


        public ImageDownloadTask(String url, String suffix, File file, boolean highPriority) {
            this.url = url;
            this.suffix = suffix;
            this.savedFile = file;
            this.highPriority = highPriority;

            //TODO 根据url来判断锁的许可等级
            this.permits = 1;
            this.retryCount = 0;
            reset();
        }

        @Override
        public String getItemKey() {
            return this.url;
        }

        @Override
        public long getItemPriority() {
            return this.priority;
        }

        /**
         * 执行重试
         *
         * @return
         */
        public boolean retry() {
            if (this.highPriority && this.retryCount < 5L) {
                //重置,当前任务,重新执行
                reset();
                return HttpTaskController.this.mDownloadExecutor.execute(this);
            }
            //低优先级,或者重试次数已过,则取消
            return false;
        }

        @Override
        public void run() {
            //获取锁
            mDownloadLockPool.lock(url);
            try {
                this.retryCount += 1;
                if (!highPriority) {
                    //非高的优先级,设置为经济模式
                    mDownloadExecutor.setPowerMode(PriorityExecutor.PowerMode.ECONOMY);
                } else {
                    long wait = System.currentTimeMillis() - created;
                    if (wait > 2000) {
                        //等待时间过长,设置为高速模式
                        mDownloadExecutor.setPowerMode(PriorityExecutor.PowerMode.SPEED);
                    } else if (wait > 1000) {
                        //等待时间正常,设置为正常模式
                        mDownloadExecutor.setPowerMode(PriorityExecutor.PowerMode.NORMAL);
                    } else {
                        //等待时间短,设置为经济模式
                        mDownloadExecutor.setPowerMode(PriorityExecutor.PowerMode.ECONOMY);
                    }
                }
                boolean success = false;
                if (!savedFile.exists()) {
                    //并发量控制
                    mSemaphore.acquireUninterruptibly(permits);

                    InputStream inputStream = null;
                    BufferedOutputStream bufferedOutputStream = null;
                    try {
                        inputStream = null;//TODO 通过你的方法获取http response的InputStream
                        bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(savedFile), 8 * 1024);
                        IOUtils.copy(inputStream, bufferedOutputStream);
                        success = true;

                    } catch (IOException e) {
                        savedFile.delete();
                        if (retry()) {
                            //下载异常,重新执行
                            return;
                        }
                    } finally {
                        IOUtils.closeQuietly(inputStream);
                        IOUtils.closeQuietly(bufferedOutputStream);
                        mSemaphore.release(permits);
                    }
                }

                List<HttpDiskCacheListener> listeners = getAndRemoveListenerList(url);
                if (listeners != null) {
                    if (success) {
                        //下载成功
                        for (HttpDiskCacheListener listener : listeners) {
                            boolean checked = listener.isProcessCheck(url, suffix);
                            //任务有效
                            if (checked) {
                                listener.onHttpDiskCacheSuccess(url, suffix, savedFile);
                            }
                        }
                    } else {
                        //下载失败
                        for (HttpDiskCacheListener listener : listeners) {
                            boolean checked = listener.isProcessCheck(url, suffix);
                            //任务有效
                            if (checked) {
                                listener.onHttpDiskCacheFailed(url, suffix);
                            }
                        }
                        throw new RuntimeException("could not download file");
                    }
                }
            } catch (Exception e) {

            } finally {
                mDownloadLockPool.unlock(url);
            }
        }

        public void reset() {
            this.priority = getPriority(highPriority);
            //许可证越高,优先级越低
            this.priority -= 100 * this.permits;
            this.created = System.currentTimeMillis();
            //运行次数(retry)越高,优先级越低
            this.priority -= 1000 * this.retryCount;
        }
    }

}