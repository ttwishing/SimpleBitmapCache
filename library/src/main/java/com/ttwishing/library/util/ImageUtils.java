package com.ttwishing.library.util;

/**
 * Created by kurt on 11/2/15.
 */
public class ImageUtils {

    public static int getInSampleSize(int width, int height, int target_width, int target_height, ScaleType scaleType) {
        int width_scaled = width;
        int inSampleSize_w = 1;
        while (width_scaled / 2 >= target_width || (width_scaled > target_width && scaleType == ScaleType.SMALLER)) {//cond_0 || cond_3
            width_scaled /= 2;
            inSampleSize_w *= 2;
        }

        int height_scaled = height;
        int inSampleSize_h = 1;

        while (height_scaled / 2 >= target_height || (height_scaled > target_height && scaleType == ScaleType.SMALLER)) {//cond_1 || cond_2
            height_scaled /= 2;
            inSampleSize_h *= 2;
        }

        int scale = Math.max(inSampleSize_w, inSampleSize_h);
        return scale;
    }

    public enum ScaleType {
        SMALLER, BIGGER
    }

}
