package com.ttwishing.library.base.sync;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by kurt on 10/28/15.
 * <p/>
 * 实现lock,并控制并发量
 */
public class NamedLockPool {

    private final boolean fair;
    //信号量控制并发
    private final Semaphore concurrencySemaphore;

    private final Map<String, NamedLock> lockMap;

    private final int lockPoolMinLimit;
    //lock的对象池
    private final List<NamedLock> lockPool;

    public NamedLockPool(int minLimit, boolean fair) {
        this(minLimit, fair, null);
    }

    public NamedLockPool(int minLimit, boolean fair, Integer permits) {
        this.lockPoolMinLimit = minLimit;
        this.fair = fair;
        this.lockMap = new HashMap(lockPoolMinLimit);
        this.lockPool = new LinkedList();
        if (permits == null) {
            this.concurrencySemaphore = null;
        } else {
            this.concurrencySemaphore = new Semaphore(permits.intValue(), this.fair);
        }
    }

    /**
     * 获取lock
     *
     * @param name
     */
    public void lock(String name) {
        NamedLock namedLock;
        synchronized (this.lockMap) {
            namedLock = getLock(name, true);
            namedLock.increment();
        }

        namedLock.lock();

        //在lock的基础上,通过信号量来控制并发量
        if (this.concurrencySemaphore != null) {
            this.concurrencySemaphore.acquireUninterruptibly();
        }
    }

    /**
     * 释放lock
     *
     * @param name
     */
    public void unlock(String name) {
        NamedLock namedLock = getLock(name, true);
        if (namedLock == null) {
            throw new RuntimeException("trying to unlock a key without holding a lock on it ?");
        }

        //lock是否执行回收
        boolean removed = true;
        synchronized (this.lockMap) {
            if (namedLock.decrement() < 1) {
                lockMap.remove(name);
            } else {
                removed = false;
            }
        }
        namedLock.unlock();
        if (concurrencySemaphore != null) {
            concurrencySemaphore.release();
        }
        if (removed) {
            //回收lock
            recycleLock(namedLock);
        }
    }

    private NamedLock getLock(String name, boolean create) {
        synchronized (this.lockMap) {
            NamedLock namedLock = this.lockMap.get(name);
            if (namedLock == null && create) {
                namedLock = getUnusedLock();
                this.lockMap.put(name, namedLock);
            }
            return namedLock;
        }
    }

    private NamedLock getUnusedLock() {
        NamedLock namedLock;
        synchronized (this.lockPool) {
            if (this.lockPool.size() > 0) {
                //获取老的
                namedLock = this.lockPool.remove(-1 + this.lockPool.size()).reset();
            } else {
                //新建
                namedLock = new NamedLock(this.fair);
            }
        }
        return namedLock;
    }

    private void recycleLock(NamedLock namedLock) {
        synchronized (lockPool) {
            if (lockPool.size() < lockPoolMinLimit) {
                lockPool.add(namedLock);
            }
        }
    }

    /**
     *
     */
    class NamedLock extends ReentrantLock {

        /**
         * 外置计数器
         */
        private AtomicInteger referenceCount = new AtomicInteger(0);

        public NamedLock(boolean fair) {
            super(fair);
        }

        /**
         * 引用-1
         *
         * @return
         */
        public int decrement() {
            return this.referenceCount.decrementAndGet();
        }

        /**
         * 引用+1
         *
         * @return
         */
        public int increment() {
            return this.referenceCount.incrementAndGet();
        }

        /**
         * 重置
         *
         * @return
         */
        public NamedLock reset() {
            this.referenceCount.set(0);
            return this;
        }
    }
}
