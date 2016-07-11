package com.ttwishing.library.base.util;

import android.view.View;
import android.view.ViewGroup;

/**
 * Created by kurt on 11/3/15.
 */
public class RecycleHelper {

    /**
     * 递归回收
     * @param view
     */
    public static void recycleViewTree(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof ViewGroup) {

            ViewGroup viewGroup = (ViewGroup)view;

            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                recycleViewTree(viewGroup.getChildAt(i));
            }
        }
        if (view instanceof RecyclingView) {
            ((RecyclingView) view).recycle();
            view.invalidate();
        }
    }

    public interface RecyclingView {
        void recycle();
    }
}
