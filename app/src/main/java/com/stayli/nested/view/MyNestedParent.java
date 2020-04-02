package com.stayli.nested.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.view.NestedScrollingParent;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;

/**
 * Created by  yahuigao
 * Date: 2020/3/30
 * Time: 10:47 AM
 * Description: 嵌套滑动机制父View
 */
public class MyNestedParent extends LinearLayout implements NestedScrollingParent {
    private static final String TAG = "MyNestedParent";
    private MyNestedChild mNestedScrollChild;
    private NestedScrollingParentHelper mNestedScrollingParentHelper;
    private int mImgHeight;

    public MyNestedParent(Context context) {
        super(context);
    }

    public MyNestedParent(Context context, AttributeSet attrs) {
        super(context, attrs);
        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final ImageView img = (ImageView) getChildAt(0);
        mNestedScrollChild = (MyNestedChild) getChildAt(2);
        img.post(new Runnable() {
            @Override
            public void run() {
                // 获取图片高度
                mImgHeight = img.getMeasuredHeight();
            }
        });
    }

    /**
     * @param child  直接子view
     * @param target 目标View
     * @param axes   滑动方向
     * @return 判断参数target是哪一个子view以及滚动的方向，然后决定是否要配合其进行嵌套滚动
     */
    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes) {
        Log.d(TAG, "onStartNestedScroll: ----> 接收Start嵌套滚动");
        return target instanceof MyNestedChild && (axes & ViewCompat.SCROLL_AXIS_HORIZONTAL) != 0;
    }


    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int nestedScrollAxes) {
        Log.d(TAG, "onNestedScrollAccepted: -------> 接收Accepted嵌套滚动");
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes);
    }

    @Override
    public void onStopNestedScroll(@NonNull View target) {
        Log.d(TAG, "onStopNestedScroll: -------> 终止嵌套滚动");
        mNestedScrollingParentHelper.onStopNestedScroll(target);
    }

    /**
     * 优先与child 滚动前调用
     *
     * @param target   目标View
     * @param dx       x轴偏移
     * @param dy       y轴偏移
     * @param consumed 消费量 输出
     */
    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed) {
        Log.d(TAG, "onNestedPreScroll: 接收Pre嵌套滚动");
        // 无论是显示 还是 隐藏图片，其实都是在子child 滚动之前就行移动
        if (showImg(dy) || hideImg(dy)) {
            scrollBy(0, -dy);//滚动
            consumed[1] = dy;//记录消费的偏移量
        }
    }

    //后于child滚动
    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        Log.d(TAG, "onNestedScroll: 接收嵌套滚动");
    }

    @Override
    public boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public boolean onNestedFling(@NonNull View target, float velocityX, float velocityY, boolean consumed) {
        return false;
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    //向下滚动以显示图片
    public boolean showImg(int dy) {
        if (dy > 0) {
            return getScrollY() > 0 && mNestedScrollChild.getScrollY() == 0;
        }

        return false;
    }

    //向上滚动以隐藏图片
    public boolean hideImg(int dy) {
        if (dy < 0) {
            return getScrollY() < mImgHeight;
        }
        return false;
    }

    //控制滚动范围
    @Override
    public void scrollTo(int x, int y) {
        if (y < 0) {
            y = 0;
        }
        if (y > mImgHeight) {
            y = mImgHeight;
        }

        super.scrollTo(x, y);
    }
}
