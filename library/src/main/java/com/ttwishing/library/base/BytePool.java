package com.ttwishing.library.base;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by kurt on 12/5/15.
 *
 * byte对象池
 */
public class BytePool {

    private final int bufferSize;
    private final int limit;

    private final ArrayBlockingQueue<Buffer> bufferQueue;//bnG

    public BytePool(int bufferSize, int limit) {
        this.bufferSize = bufferSize;
        this.limit = limit;
        this.bufferQueue = new ArrayBlockingQueue(this.limit, true);
        for (int i = 0; i < this.limit; i++) {
            this.bufferQueue.add(new Buffer(new byte[this.bufferSize]));
        }
    }

    public Buffer pop() {
        try {
            //如果队列中没有数据，则wait，而poll()则不会等待，直接返回null
            Buffer buffer = this.bufferQueue.take();
            buffer.clear();
            return buffer;
        } catch (InterruptedException e) {
        }
        return null;
    }

    public void push(Buffer buffer) {
        if (buffer == null) {
            return;
        }
        try {
            //空间耗尽时offer()函数不会等待，直接返回false，而put()则会wait
            this.bufferQueue.put(buffer);
        } catch (InterruptedException e) {
            Log.e("BytePool", "interrupt while releasing bytes. bad");
        }
    }

    public class Buffer {
        public final ByteBuffer byteBuffer;
        public final byte[] bytes;

        public Buffer(byte[] array) {
            this.bytes = array;
            this.byteBuffer = ByteBuffer.wrap(array);
        }

        public void clear() {
            this.byteBuffer.clear();
        }
    }
}
