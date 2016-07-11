package com.ttwishing.library.util;

import java.util.concurrent.Executor;

import android.os.Handler;
import android.os.Looper;

import com.ttwishing.library.tasks.AppTaskExecutor;


/**
 *
 */
public class ThreadUtil {

    /**
     * 网络任务线程池，不限核心线程数，
     */
    private static AppTaskExecutor mExecutor;
    private static Handler mHandler;
    private static Looper mLooper;

    public static Looper getMainLooper() {
        if (mLooper == null)
            mLooper = Looper.getMainLooper();
        return mLooper;
    }

    public static boolean isWorkerThread() {
        return !isMainThread();
    }

    public static boolean isMainThread() {
        return getMainLooper().equals(Looper.myLooper());
    }


    public static Handler getHandler() {
        if (mHandler == null)
            mHandler = new Handler(getMainLooper());
        return mHandler;
    }

    public static Executor getExecutor() {
        if (mExecutor == null)
            mExecutor = AppTaskExecutor.getInstanse();
        return mExecutor;
    }

    public static void post(Runnable r) {
        if (isMainThread()) {
            r.run();
        } else {
            getHandler().post(r);
        }
    }

    public static void postDelayed(Runnable r, long delayMillis) {
        getHandler().postDelayed(r, delayMillis);
    }

    public static void execute(Runnable r) {
        if (isWorkerThread()) {
            r.run();
        } else {
            getExecutor().execute(r);
        }
    }

}
