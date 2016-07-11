package com.ttwishing.library.base.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.ttwishing.library.base.BaseRgbBitmapCache;
import com.ttwishing.library.base.VaryingSizeCappedBitmapCache;
import com.ttwishing.library.base.VaryingSizeRefCountedBitmapDrawable;
import com.ttwishing.library.base.util.RecycleHelper;

import java.lang.ref.WeakReference;

/**
 * Created by kurt on 11/13/15.
 */
public abstract class RefCountedFastResizeableImageView extends View implements RecycleHelper.RecyclingView, BaseRgbBitmapCache.Callback<VaryingSizeRefCountedBitmapDrawable> {

    private VaryingSizeRefCountedBitmapDrawable varyingSizeRefCountedBitmapDrawable;//boa
    private VaryingSizeBitmapRequest varyingSizeBitmapRequest;//bob
    protected RectF rectF = null;//boc
    private WeakCallback weakCallback = new WeakCallback(this);//bod

    private boolean playAnimation = false;
    private long startTime;//boe
    private long duration;//bof

    public RefCountedFastResizeableImageView(Context context) {
        super(context);
        init(context);
    }

    public RefCountedFastResizeableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public RefCountedFastResizeableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setDrawingCacheEnabled(false);
        this.duration = getAnimationDurationInNs();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (varyingSizeRefCountedBitmapDrawable != null) {//cond_3
            if (this.playAnimation) {//cond_0
                long interval = System.nanoTime() - startTime;
                float alpha;
                if (interval > duration) {//cond_2
                    this.playAnimation = false;
                    alpha = 255.0F;
//                  //goto_0
                } else {
                    alpha = 10.0F + 245.0F * ((float) interval / (float) duration);
                    //goto_0
                }
                varyingSizeRefCountedBitmapDrawable.setAlpha(Math.round(alpha));
                if (playAnimation && !this.varyingSizeRefCountedBitmapDrawable.isOpaque()) {//cond_0
                    drawBitmap(canvas);
                }
            }
            this.varyingSizeRefCountedBitmapDrawable.draw(canvas, rectF);
        } else {
            drawBitmap(canvas);
        }

        if (this.playAnimation) {
            postInvalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (this.varyingSizeRefCountedBitmapDrawable == null && this.varyingSizeBitmapRequest == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int paddingH = getPaddingLeft() + getPaddingRight();
        int paddingV = getPaddingTop() + getPaddingBottom();
        int width;
        int height;
        if (this.varyingSizeRefCountedBitmapDrawable != null) {
            width = paddingH + varyingSizeRefCountedBitmapDrawable.getWidth();
            height = paddingV + varyingSizeRefCountedBitmapDrawable.getHeight();
        } else {
            width = paddingH + varyingSizeBitmapRequest.width;
            height = paddingV + varyingSizeBitmapRequest.height;
        }
        if (rectF == null) {
            rectF = new RectF();
        }
        rectF.set(getPaddingLeft(), getPaddingTop(), width - getPaddingRight(), height - getPaddingBottom());
        setMeasuredDimension(width, height);
    }

    protected abstract long getAnimationDurationInNs();

    //noodles
    protected void drawBitmap(Canvas canvas) {
    }

    @Override
    public void recycle() {
        setRefCountedResizeableBitmapDrawable(null);
        this.varyingSizeBitmapRequest = null;
    }

    //gingerale
    private boolean isSameBitmapRequest(BaseRgbBitmapCache.BitmapRequest bitmapRequest) {
        if (bitmapRequest == this.varyingSizeBitmapRequest)
            return true;

        if (this.varyingSizeBitmapRequest != null && bitmapRequest != null && varyingSizeBitmapRequest.getKey().equals(bitmapRequest.getKey())) {
            return true;
        }

        return false;
    }

    public void set(VaryingSizeCappedBitmapCache bitmapCache, VaryingSizeBitmapRequest bitmapRequest) {

        if (isSameBitmapRequest(bitmapRequest)) {//cond_0
            return;
        }

        if (varyingSizeRefCountedBitmapDrawable != null) {//cond_1
            setRefCountedResizeableBitmapDrawable(null);
        }

        this.varyingSizeBitmapRequest = bitmapRequest;

        if (this.varyingSizeBitmapRequest != null) {
            bitmapCache.loadBitmap(this.varyingSizeBitmapRequest, this.weakCallback);
        }

        requestLayout();
    }

    public void setRefCountedResizeableBitmapDrawable(VaryingSizeRefCountedBitmapDrawable bitmapDrawable) {

        if (this.varyingSizeRefCountedBitmapDrawable != null) {//cond_0
            this.varyingSizeRefCountedBitmapDrawable.release();
        }

        if (this.varyingSizeRefCountedBitmapDrawable == bitmapDrawable) {//cond_1
            return;
        }

        this.varyingSizeRefCountedBitmapDrawable = bitmapDrawable;

        if (this.varyingSizeRefCountedBitmapDrawable != null && duration > 0L) {//cond_2
            this.playAnimation = true;
            this.startTime = System.nanoTime();
            this.varyingSizeRefCountedBitmapDrawable.setAlpha(10);
        } else {
            this.playAnimation = false;
        }
        invalidate();
    }

    @Override
    public void onLoadComplete(BaseRgbBitmapCache.BitmapRequest bitmapRequest, VaryingSizeRefCountedBitmapDrawable bitmapDrawable) {
        Log.d("BaseRgbBitmapCache", "onLoadComplete: "+bitmapRequest.getUrl()+", bitmapDrawable is null ? "+(bitmapDrawable == null));
        if (bitmapDrawable == null) {//cond_1
            //on drawable ready. is null ?
        }

        if (!isSameRequest(bitmapRequest)) {//cond_2
            //is not my request. releasing
            if (bitmapDrawable != null) {//cond_0
                bitmapDrawable.release();
            }
        }else{
            //my request, setting
            setRefCountedResizeableBitmapDrawable(bitmapDrawable);
        }
    }

    @Override
    public boolean isProcessCheck(BaseRgbBitmapCache.BitmapRequest bitmapRequest) {
        return isSameRequest(bitmapRequest);
    }

    //gingerale
    private boolean isSameRequest(BaseRgbBitmapCache.BitmapRequest bitmapRequest) {
        if (bitmapRequest == varyingSizeBitmapRequest || (varyingSizeBitmapRequest != null && bitmapRequest != null && varyingSizeBitmapRequest.getKey().equals(bitmapRequest.getKey()))) {
            return true;
        }
        return false;
    }

    class WeakCallback implements BaseRgbBitmapCache.Callback<VaryingSizeRefCountedBitmapDrawable> {

        private WeakReference<BaseRgbBitmapCache.Callback> callbackRef;

        public WeakCallback(BaseRgbBitmapCache.Callback callback) {
            this.callbackRef = new WeakReference(callback);
        }

        @Override
        public boolean isProcessCheck(BaseRgbBitmapCache.BitmapRequest bitmapRequest) {
            BaseRgbBitmapCache.Callback callback = callbackRef.get();
            if (callback != null && callback.isProcessCheck(bitmapRequest)) {
                return true;
            }
            return false;
        }

        @Override
        public void onLoadComplete(BaseRgbBitmapCache.BitmapRequest bitmapRequest, VaryingSizeRefCountedBitmapDrawable bitmapDrawable) {
            BaseRgbBitmapCache.Callback callback = this.callbackRef.get();
            if (callback == null) {
                if (bitmapDrawable != null) {
                    bitmapDrawable.release();
                }
                return;
            }
            callback.onLoadComplete(bitmapRequest, bitmapDrawable);
        }
    }

    public class VaryingSizeBitmapRequest implements BaseRgbBitmapCache.BitmapRequest {
        private final String key;
        private final String url;
        private final int width;
        private final int height;

        public VaryingSizeBitmapRequest(String url, String key, int width, int height) {
            this.url = url;
            this.key = key;
            this.width = width;
            this.height = height;
        }

        public String getKey() {
            return this.key;
        }

        public String getUrl() {
            return this.url;
        }
    }

}
