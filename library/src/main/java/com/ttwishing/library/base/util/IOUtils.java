package com.ttwishing.library.base.util;

import android.content.Context;
import android.net.Uri;
import android.os.StatFs;

import com.ttwishing.library.App;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by kurt on 10/29/15.
 */
public class IOUtils {

    public static float getRemainingSpaceOnPhone() {
        return getRemainingSpaceOnDisk(App.getInstance().getCacheDir());
    }

    public static float getRemainingSpaceOnSDCard() {
        return getRemainingSpaceOnDisk(App.getInstance().getExternalCacheDir());
    }

    private static float getRemainingSpaceOnDisk(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        StatFs statFs = new StatFs(file.getPath());
        return (float) (statFs.getBlockSize() * statFs.getAvailableBlocks()) / (1024 * 1024);

    }


    public static File copyToFilesDir(Context context, Uri uri) throws IOException {
        File file = null;
        InputStream is = null;
        BufferedOutputStream bos = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            file = File.createTempFile("copy", null, context.getCacheDir());
            bos = new BufferedOutputStream(new FileOutputStream(file), 1024 * 8);
            copy(is, bos);
        } catch (Exception e) {
            throw new IOException("Reading original file failed.");
        } finally {
            closeQuietly(is);
            closeQuietly(bos);
        }
        return file;
    }

    public static long copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[' '];
        long totalRead = 0L;
        int read;
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
            totalRead += read;
        }
        return totalRead;
    }

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception localException) {
            }
        }
    }

    public static void clearQuietly(SafeFile safeFile) {
        if (safeFile == null) {
            return;
        }
        safeFile.clearIfNecessary();
    }

    public static class SafeFile {
        public boolean copied = false;
        public File file;
        Uri uri;

        public SafeFile(Uri uri) {
            this.uri = uri;
        }

        public void clearIfNecessary() {
            if (this.copied && this.file != null) {
                this.file.delete();
            }
        }

        public boolean makeSafe(Context context) {
            if (this.file == null) {
                this.file = new File(this.uri.getEncodedPath());
                if (this.file.exists() && this.file.canRead()) {
                    this.copied = false;
                } else {
                    this.file = null;
                }
            }
            if (file == null) {
                this.file = new File(this.uri.toString());
                if ((this.file.exists()) && (this.file.canRead())) {
                    this.copied = false;
                } else {
                    this.file = null;
                }
            }

            if (this.file == null) {
                try {
                    this.file = IOUtils.copyToFilesDir(context, this.uri);
                    this.copied = true;
                } catch (IOException e) {
                    return false;
                }
            }
            return true;

        }
    }
}
