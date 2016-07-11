package com.ttwishing.library.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

import com.ttwishing.library.base.util.IOUtils;
import com.ttwishing.library.disk.DiskLruCache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by kurt on 12/5/15.
 * 图片尺寸大小固定的BitmapCache
 *
 * 1.实现从disk读取bitmap
 * 2.实现保存bitmap到disk
 * 3.子类需: 定制BitmapCache, DiskLurCache, 获取network_result, 从network_result获取bitmap
 */
public abstract class FixedSizeBitmapCache<NetworkResultType> extends BaseRgbBitmapCache<RefCountedBitmapDrawable, NetworkResultType> {

    final int limit = 3;
    private final BytePool bytePool = new BytePool(this.perMemorySize, limit);

    public FixedSizeBitmapCache(Context context, int width, int height, Bitmap.Config config, int poolSize) {
        super(context, width, height, config, poolSize);
    }

    @Override
    protected RefCountedBitmapDrawable createRefCountedBitmapDrawable(RefCountedBitmapPool<RefCountedBitmapDrawable> refCountedBitmapPool, BitmapDrawable bitmapDrawable) {
        return new RefCountedBitmapDrawable(refCountedBitmapPool, bitmapDrawable);
    }

    @Override
    protected void readFromDiskLruCache(DiskLruCache.Snapshot snapshot, RefCountedBitmapDrawable refCountedBitmapDrawable, BitmapRequest bitmapRequest, ProcessCheck processCheck) throws IOException {
        InputStream inputStream = null;
        BytePool.Buffer buffer = null;
        try {
            buffer = this.bytePool.pop();
            inputStream = snapshot.getInputStream(0);
            inputStream.read(buffer.bytes);
            if (!processCheck.isProcessCheck(bitmapRequest)) {
                this.countCancelled += 1;
                throw new IOException("no need to process further. cancelling");
            } else {
                refCountedBitmapDrawable.getBitmap().copyPixelsFromBuffer(buffer.byteBuffer);
            }
        } finally {
            IOUtils.closeQuietly(inputStream);
            this.bytePool.push(buffer);
        }
    }


    @Override
    protected void saveBitmapToDiskLruCache(RefCountedBitmapDrawable refCountedBitmapDrawable, DiskLruCache.Editor editor) throws IOException {
        OutputStream outputStream = null;
        BytePool.Buffer buffer = null;
        try {
            buffer = this.bytePool.pop();
            refCountedBitmapDrawable.getBitmap().copyPixelsToBuffer(buffer.byteBuffer);
            outputStream = editor.newOutputStream(0);
            outputStream.write(buffer.bytes);
        } finally {
            this.bytePool.push(buffer);
            IOUtils.closeQuietly(outputStream);
        }
    }
}
