package com.ttwishing.library.tasks;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 创建可跟踪的任务队列
 */
public abstract class ExecutorWithPerfTracking implements ExecutorService {

    private final String mName;
    private final ExecutorService mExecutorService;

    public ExecutorWithPerfTracking(ExecutorService executorService, String name) {
        this.mExecutorService = executorService;
        this.mName = name;
    }

    @Override
    public void execute(Runnable command) {
        mExecutorService.execute(new RunnableWrapper(command));
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return mExecutorService.awaitTermination(timeout, unit);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return mExecutorService.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return mExecutorService.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return mExecutorService.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        return mExecutorService.invokeAny(tasks, timeout, unit);
    }

    @Override
    public boolean isShutdown() {
        return mExecutorService.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return mExecutorService.isTerminated();
    }

    @Override
    public void shutdown() {
        mExecutorService.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return mExecutorService.shutdownNow();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return mExecutorService.submit(task);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return mExecutorService.submit(task);
    }

    public <T extends Object> Future<T> submit(Runnable task, T result) {
        return mExecutorService.submit(task, result);
    }

    @Override
    public String toString() {
        return "ExecutorWithPerfTracking ~ " + mName;
    }

    class RunnableWrapper implements Runnable {

        Runnable runnable;

        public RunnableWrapper(Runnable r) {
            this.runnable = r;
        }

        @Override
        public void run() {
            //此处记录
            runnable.run();
        }
    }
}