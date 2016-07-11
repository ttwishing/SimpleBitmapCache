package com.ttwishing.library.base.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.ttwishing.library.base.BaseRgbBitmapCache;
import com.ttwishing.library.base.FixedSizeBitmapCache;
import com.ttwishing.library.base.RefCountedBitmapDrawable;
import com.ttwishing.library.base.util.RecycleHelper;

import java.lang.ref.WeakReference;

/**
 * Created by kurt on 12/5/15.
 */
public abstract class RefCountedImageView extends View implements RecycleHelper.RecyclingView, BaseRgbBitmapCache.Callback {

    private ImageView.ScaleType scaleType = ImageView.ScaleType.MATRIX;
    private Matrix matrix = new Matrix();

    //要加载的bitmap
    RefCountedBitmapDrawable refCountedBitmapDrawable;
    //empty状态下的bitmap
    RefCountedBitmapDrawable emptyStateRefCountedDrawable;

    //要加载的bitmap request
    BaseRgbBitmapCache.BitmapRequest bitmapRequest;
    //empty状态下Bitmap的request
    BaseRgbBitmapCache.BitmapRequest emptyStateBitmapRequest;

    //要加载的bitmap callback
    private WeakCallback weakCallback = new WeakCallback(this);
    //empty状态下的bitmap callback
    private WeakCallback weakEmptyStateCallback = new WeakCallback(new BaseRgbBitmapCache.Callback() {

        @Override
        public boolean isProcessCheck(BaseRgbBitmapCache.BitmapRequest bitmapRequest) {
            if ((refCountedBitmapDrawable == null || refCountedBitmapDrawable.getBitmapDrawable() == null) && isSameEmptyStateBitmapRequest(bitmapRequest)) {
                return true;
            }
            return false;
        }

        @Override
        public void onLoadComplete(BaseRgbBitmapCache.BitmapRequest bitmapRequest, RefCountedBitmapDrawable refCountedBitmapDrawable) {
            if (!isSameEmptyStateBitmapRequest(bitmapRequest)) {
                if (refCountedBitmapDrawable != null) {
                    refCountedBitmapDrawable.release();
                }
                return;
            }
            setRefCountedEmptyStateDrawable(refCountedBitmapDrawable);
        }
    });

    public RefCountedImageView(Context context) {
        super(context);
    }

    public RefCountedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RefCountedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //如果bitmap存在,则绘制
        BitmapDrawable bitmapDrawable = null;
        if (this.refCountedBitmapDrawable != null) {
            bitmapDrawable = refCountedBitmapDrawable.getBitmapDrawable();
        }
        if (bitmapDrawable != null) {
            bitmapDrawable.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
            bitmapDrawable.draw(canvas);
            return;
        }

        //bitmap不存在,绘制empty状态下的bitmap
        BitmapDrawable emptyStateBitmapDrawable = null;
        if (emptyStateRefCountedDrawable != null) {
            emptyStateBitmapDrawable = this.emptyStateRefCountedDrawable.getBitmapDrawable();
        }
        if (emptyStateBitmapDrawable != null) {
            emptyStateBitmapDrawable.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
            emptyStateBitmapDrawable.draw(canvas);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
    }

    @Override
    public void recycle() {
        setRefCountedBitmapDrawable(null);
        setRefCountedEmptyStateDrawable(null);
        this.bitmapRequest = null;
        this.emptyStateBitmapRequest = null;
    }

    /**
     * 设置请求,并load
     * @param bitmapCache
     * @param bitmapRequest
     * @param emptyStateBitmapRequest
     */
    public void set(FixedSizeBitmapCache bitmapCache, BaseRgbBitmapCache.BitmapRequest bitmapRequest, BaseRgbBitmapCache.BitmapRequest emptyStateBitmapRequest) {
        //是否已设置过
        if (!isSameBitmapRequest(bitmapRequest)) {
            this.bitmapRequest = bitmapRequest;
            if (this.bitmapRequest == null) {
                setRefCountedBitmapDrawable(null);
            } else if (!bitmapCache.loadBitmap(this.bitmapRequest, this.weakCallback)) {
                setRefCountedBitmapDrawable(null);
            }
        }

        if (!isSameEmptyStateBitmapRequest(emptyStateBitmapRequest)) {
            this.emptyStateBitmapRequest = emptyStateBitmapRequest;
            if (this.emptyStateBitmapRequest == null) {
                setRefCountedEmptyStateDrawable(null);
            } else if (!bitmapCache.loadBitmap(this.emptyStateBitmapRequest, this.weakEmptyStateCallback)) {
                setRefCountedEmptyStateDrawable(null);
            }

        }
    }

    public void setRefCountedBitmapDrawable(RefCountedBitmapDrawable refCountedBitmapDrawable) {
        if (this.refCountedBitmapDrawable != null) {
            this.refCountedBitmapDrawable.release();
        }
        if (this.refCountedBitmapDrawable == refCountedBitmapDrawable) {
            return;
        }
        this.refCountedBitmapDrawable = refCountedBitmapDrawable;
        invalidate();
    }

    private void setRefCountedEmptyStateDrawable(RefCountedBitmapDrawable refCountedBitmapDrawable) {
        if (this.emptyStateRefCountedDrawable != null) {
            this.emptyStateRefCountedDrawable.release();
        }
        if (this.emptyStateRefCountedDrawable == refCountedBitmapDrawable) {
            return;
        }
        this.emptyStateRefCountedDrawable = refCountedBitmapDrawable;
        invalidate();
    }

    protected Matrix getImageMatrix() {
        return this.matrix;
    }

    protected void setImageMatrix(Matrix matrix) {
        this.matrix = matrix;
    }

    public ImageView.ScaleType getScaleType() {
        return this.scaleType;
    }

    public void setScaleType(ImageView.ScaleType scaleType) {
        this.scaleType = scaleType;
    }

    /**
     * 是否是当前请求
     *
     * @param bitmapRequest
     * @return
     */
    private boolean isSameBitmapRequest(BaseRgbBitmapCache.BitmapRequest bitmapRequest) {
        if (bitmapRequest == this.bitmapRequest
                || (this.bitmapRequest != null && bitmapRequest != null && this.bitmapRequest.getKey().equals(bitmapRequest.getKey()))) {
            return true;
        }
        return false;
    }

    /**
     * 是否是当前请求
     *
     * @param bitmapRequest
     * @return
     */
    private boolean isSameEmptyStateBitmapRequest(BaseRgbBitmapCache.BitmapRequest bitmapRequest) {
        if (bitmapRequest == this.emptyStateBitmapRequest
                || (this.emptyStateBitmapRequest != null && bitmapRequest != null && this.emptyStateBitmapRequest.getKey().equals(bitmapRequest.getKey()))) {
            return true;
        }
        return false;
    }

    //==========BaseRgbBitmapCache.Callback============
    @Override
    public void onLoadComplete(BaseRgbBitmapCache.BitmapRequest bitmapRequest, RefCountedBitmapDrawable bitmapDrawable) {
        if (!isSameBitmapRequest(bitmapRequest)) {
            if (bitmapDrawable != null) {
                bitmapDrawable.release();
            }
            return;
        }

        setRefCountedBitmapDrawable(bitmapDrawable);
    }

    @Override
    public boolean isProcessCheck(BaseRgbBitmapCache.BitmapRequest bitmapRequest) {
        return isSameBitmapRequest(bitmapRequest);
    }

    /**
     * callback委托
     */
    class WeakCallback extends BaseRgbBitmapCache.RunnableCallback<RefCountedBitmapDrawable> implements BaseRgbBitmapCache.Callback {

        private WeakReference<BaseRgbBitmapCache.Callback> callbackRef;

        public WeakCallback(BaseRgbBitmapCache.Callback callback) {
            this.callbackRef = new WeakReference(callback);
        }

        @Override
        public void onLoadComplete(BaseRgbBitmapCache.BitmapRequest bitmapRequest, RefCountedBitmapDrawable refCountedBitmapDrawable) {
            BaseRgbBitmapCache.Callback callback = this.callbackRef.get();
            if (callback == null) {
                if (refCountedBitmapDrawable != null) {
                    refCountedBitmapDrawable.release();
                }
            } else {
                callback.onLoadComplete(bitmapRequest, refCountedBitmapDrawable);
            }
        }

        @Override
        public boolean isProcessCheck(BaseRgbBitmapCache.BitmapRequest bitmapRequest) {
            BaseRgbBitmapCache.Callback callback = this.callbackRef.get();
            if (callback != null && callback.isProcessCheck(bitmapRequest)) {
                return true;
            }
            return false;
        }
    }

}
