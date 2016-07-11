package com.ttwishing.library.util;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by kurt on 10/28/15.
 */
public abstract class BaseObjectPool<T extends BaseObjectPool.Poolable> {

    protected final String TAG = getClass().getSimpleName();
    protected List<T> list;

    protected int reuseCount;
    protected int addCount;
    protected int createCount;

    protected int startSize;
    protected int maxSize;

    public BaseObjectPool(int startSize, int maxSize) {
        this.startSize = startSize;
        this.maxSize = maxSize;
        this.list = new LinkedList();

        for (int i = 0; i < startSize; i++) {
            this.list.add(create(this));
        }
    }

    public synchronized T pop() {
        if (list.size() > 0) {
            reuseCount = reuseCount + 1;
            logStats();
            return list.remove(list.size() - 1);
        }
        createCount = createCount + 1;
        logStats();
        return create(this);
    }

    protected abstract T create(BaseObjectPool<T> pool);

    protected synchronized void push(T t) {
        if (this.list.size() < this.maxSize) {
            this.addCount = (1 + this.addCount);
            this.list.add(t);
        }
        logStats();
    }

    public void logStats() {

    }

    @Override
    public synchronized String toString() {
        return this.TAG + ", unused pool size: " + this.list.size() + ", reuseCount:" + this.reuseCount + ", createCount:" + this.createCount;
    }

    public abstract class Poolable {

        protected abstract void cleanup();

        protected void release() {
            cleanup();
            BaseObjectPool.this.push((T) this);
        }
    }
}
