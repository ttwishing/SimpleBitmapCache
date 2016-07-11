package com.ttwishing.library.base;

/**
 * Created by kurt on 12/5/15.
 */
public class SimpleBitmapRequest implements BaseRgbBitmapCache.BitmapRequest {

    private final String key;
    private final String url;

    public SimpleBitmapRequest(String url, String key) {
        this.url = url;
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getUrl() {
        return url;
    }
}
