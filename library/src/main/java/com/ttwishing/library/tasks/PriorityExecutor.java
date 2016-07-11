package com.ttwishing.library.tasks;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by kurt on 10/28/15.
 */
public class PriorityExecutor<T extends PriorityExecutor.Item> {

    private static final int WRAPPER_POOL_LIMIT = 10;
    private boolean allowDuplicateTasks = false;
    private final ComparePriority comparePriority;
    private final int corePoolSize;
    private ThreadPoolExecutor executor;
    private final int maximumPoolSize;
    PowerMode powerMode;
    private final PriorityBlockingQueue<Runnable> workQueue;
    private final Map<String, RunnableWrapper> runnableMap = new HashMap();
    private final LinkedList<RunnableWrapper> wrapperPool = new LinkedList();

    public PriorityExecutor(String name, int corePoolSize, int maximumPoolSize, int keepAliveTime, PowerMode powerMode, boolean paramBoolean) {
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.comparePriority = new ComparePriority();
        this.allowDuplicateTasks = paramBoolean;
        this.workQueue = new PriorityBlockingQueue(300, this.comparePriority);
        this.powerMode = powerMode;
        int threadCount = powerModeToThreadCount(this.powerMode);
        this.executor = new ThreadPoolExecutor(threadCount, threadCount, keepAliveTime, TimeUnit.SECONDS, this.workQueue, new NamedThreadFactory(name));
        setPowerMode(powerMode);
    }

    public boolean execute(T t) {
        if (this.allowDuplicateTasks) {
            this.executor.execute(getWrapper(t));
            return true;
        }
        RunnableWrapper runnableWrapper;
        synchronized (this.runnableMap) {
            runnableWrapper = this.runnableMap.get(t.getItemKey());
            if (runnableWrapper != null) {//cond_2
                if (this.comparePriority.compare((Runnable) t, (Runnable) runnableWrapper.wrapped) < 0) {//cond_1
                    if (!runnableWrapper.canRun.getAndSet(false)) {//cond_2
                        return false;
                    }
                } else {
                    return false;
                }
            }
            runnableWrapper = getWrapper(t);
            this.runnableMap.put(t.getItemKey(), runnableWrapper);
            this.executor.execute(runnableWrapper);
            return true;
        }
    }

    private RunnableWrapper getWrapper(T t) {
        synchronized (this.wrapperPool) {
            RunnableWrapper runnableWrapper = null;
            int size = this.wrapperPool.size();
            if (size > 0) {
                runnableWrapper = (RunnableWrapper) this.wrapperPool.remove(-1 + this.wrapperPool.size());
            }
            if (runnableWrapper == null) {
                runnableWrapper = new RunnableWrapper(t);
            } else {
                runnableWrapper.reset(t);
            }
            return runnableWrapper;
        }
    }

    public int powerModeToThreadCount(PowerMode powerMode) {
        switch (powerMode) {
            case ECONOMY:
                return corePoolSize;

            case NORMAL:
                return (this.corePoolSize + this.maximumPoolSize) / 2;

            case SPEED:
                return this.maximumPoolSize;

            default:
                return 1;
        }
    }

    public synchronized void setPowerMode(PowerMode powerMode) {
        if (this.powerMode == powerMode) {
            return;
        }

        try {
            this.powerMode = powerMode;
            setThreadCount(powerModeToThreadCount(powerMode));
        } catch (Exception e) {

        }
    }

    private synchronized void setThreadCount(int threadCount) {
        try {
            if (threadCount < this.executor.getCorePoolSize()) {
                this.executor.setCorePoolSize(threadCount);
                this.executor.setMaximumPoolSize(threadCount);
                return;
            }
            if (threadCount > this.executor.getCorePoolSize()) {
                this.executor.setMaximumPoolSize(threadCount);
                this.executor.setCorePoolSize(threadCount);
                return;
            }
        } finally {
        }
    }

    private void recycle(RunnableWrapper runnableWrapper) {
        synchronized (this.wrapperPool) {
            if (this.wrapperPool.size() < WRAPPER_POOL_LIMIT) {
                this.wrapperPool.add(runnableWrapper);
            }
            return;
        }
    }

    public interface Item {
        public String getItemKey();

        public long getItemPriority();
    }

    public class ComparePriority implements Comparator<Runnable> {

        @Override
        public int compare(Runnable lhs, Runnable rhs) {
            Item lhs_item = (Item) lhs;
            Item rhs_item = (Item) rhs;
            if (lhs_item.getItemPriority() > rhs_item.getItemPriority()) {
                return -1;
            }
            if (lhs_item.getItemPriority() < rhs_item.getItemPriority()) {
                return 1;
            }

            return 0;
        }
    }

    public class RunnableWrapper implements Item, Runnable {

        AtomicBoolean canRun = new AtomicBoolean(true);
        T wrapped;

        public RunnableWrapper(T wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public String getItemKey() {
            return this.wrapped.getItemKey();
        }

        @Override
        public long getItemPriority() {
            return this.wrapped.getItemPriority();
        }

        @Override
        public void run() {
            if (!this.canRun.getAndSet(false) || executor.isShutdown()) {
                recycle();
                return;
            }

            if (!allowDuplicateTasks) {//cond_2
                synchronized (runnableMap) {
                    runnableMap.remove(getItemKey());
                }
            }
            try {
                ((Runnable) wrapped).run();
            } catch (Throwable t) {
                //error while running runnable
            } finally {
                recycle();
            }

        }

        public RunnableWrapper reset(T wrapped) {
            this.wrapped = wrapped;
            this.canRun.set(true);
            return this;
        }

        public void recycle() {
            this.canRun.set(false);
            this.wrapped = null;
            PriorityExecutor.this.recycle(this);
        }
    }

    public enum PowerMode {
        ECONOMY,
        NORMAL,
        SPEED;
    }
}
