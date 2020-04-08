# Android NestedScrolling 机制-基础篇
其实NestedScrolling对于现在的Android开发已经是一个很常见的交互效果，当我们需要实现一些好看却又比较复杂的滑动变换时，基本上就需要借助NestedScrolling机制。
先看下一个比较常见的示例效果
![CoordinatorLayout效果.gif](https://upload-images.jianshu.io/upload_images/1731043-8f41ee41e1dc484a.gif?imageMogr2/auto-orient/strip)

 这套效果是利用官方提供的CoordinatorLayout实现的，当然CoordinatorLayout就是NestedScrolling机制典型的官方应用例子。
## 一 对比
这里我会先介绍下基础的概念和机制实现，后续结合对整个机制的理解实现一个简单模仿NestedScrollView的Demo，以便更好的加深理解。
demo地址 [请点击](https://github.com/stayli117/MyNestedScrolling). 效果如图：
![Demo效果.gif](https://upload-images.jianshu.io/upload_images/1731043-37f5b1070c4c7a31.gif?imageMogr2/auto-orient/strip)

### 传统的View事件分发机制
传统事件分发处理主要涉及到Activity、ViewGroup和View这三个主要的类。
- 事件分发机制 对应分发方法是dispatchTouchEvent ，只要事件能够传递到当前处理的View，则此方法一定被调用。
- 事件拦截机制对应的拦截方法是onInterceptTouchEvent，拦截之后则不往下传递。（此方法只有ViewGroup拥有，此方法会在dispatchTouchEvent 中被调用。
- 事件的响应机制对应的是onTouchEvent方法。

对于传统的事件从分发，拦截到处理的一般流程：

1. 由当前的Activity接收系统的Touch事件回调，调用dispatchTouchEvent开始分发Touch事件
2. 上层ViewGroup根据onInterceptTouchEvent判断是否要中断Touch事件
 - 中断，则拦截Touch事件，并会回调onTouchEvent，进行处理
 - 不中断，则继续调用其子View的dispatchTouchEvent继续分发Touch事件
3. 直到有子View消费掉了Touch事件，则整个过程就结束了

传统的Touch事件分发是由上向下的整个过程类似于一个单向的水流事件，中间有一环将水流拦截，则下游便不会再有水流经过。这样导致的问题，就是在一个Touch事件流中，只能有一个View或ViewGroup对当前Touch事件做出反应。原则上当Touch事件被拦截后，是无法再次交还给下层子View去处理的（除非手动干预事件的分发）。
因此我们示例中的滑动效果，我们滑动的是下面的内容区域View，但是滚动的却是外部的ViewGroup，那么就必须由上面的ViewGroup拦截Touch事件并进行处理。而示例效果确实上层的ViewGroup滑动一定程度后，又交换给了下面的子View进行滑动处理，显然这种无间断顺滑交互，按照传统的Touch事件的分发机制，是很难实现的。
由此就引出了我们今天的主角NestedScrolling机制。

### NestedScrolling机制
基础流程大致是这样的：
1. 首先需要原有的Touch事件处理先交给子View，当然父View可以拦截，拦截后本次处理便无法交给子View去处理了
2. 当子View接收到Touch事件时，会转换为NestedScrolling事件，也就是dx，dy （后续统称为NestedScrolling事件），并开始发起NestedScrolling事件分发。 
2. 子View首先将对应的NestedScrolling事件发送给父View处理，待父View处理完成后则返回对应的处理和未处理的偏移量
3. 子View根据剩余偏移量继续处理NestedScrolling事件，并再次通知父View处理剩余的偏移量
4. 父View处理完成，子view则最后发起NestedScrolling事件终结，父view进行收尾工作

从上述的流程中可以轻易的得出NestedScrolling事件，是一种由下向上发起，但是在处理过程中，会不断询问上层View的处理，整个过程是给予了上层和下层多个参与处理的机会。
不难得出NestedScroll的机制本质上是给View与View之间提供了一种关联的机制，以实现View之间协同处理原来的Touch事件，来解决传统Touch事件机制无法回溯到父View的一锤子买卖的问题。当然其中的前提条件是子View需要作为Touch事件的处理者。

## 二 实现
```
1 主要接口
- NestedScrollingParent
- NestedScrollingChild
最新的支持库中，还有
- NestedScrollingParent2
- NestedScrollingChild2
由于新增的两个类原理上不影响对NestedScrolling机制的分析，在此就不对这两个类多做描述。
```
首先了解一下NestedScrollingParent的接口方法
```
 boolean onStartNestedScroll(@NonNull View child, @NonNull View target, @ScrollAxis int axes);
 void onNestedScrollAccepted(@NonNull View child, @NonNull View target, @ScrollAxis int axes);
 void onStopNestedScroll(@NonNull View target);
 void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed,
            int dxUnconsumed, int dyUnconsumed);
 void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed);
 boolean onNestedFling(@NonNull View target, float velocityX, float velocityY, boolean consumed);
 boolean onNestedPreFling(@NonNull View target, float velocityX, float velocityY);
 int getNestedScrollAxes();
```
首先Parent中的方法基本都是on开头的响应式的方法，我们这里需要着重关注这几个方法
- onStartNestedScroll
对发起嵌套滑动的子view做出回应，这个子View不一定是直接子View，例如viewPager嵌套的RecycleView。如果此父View接受嵌套滚动操作，则需要返回true。
- onStopNestedScroll
对终止嵌套滑动的子view做出回应。
- onNestedPreScroll
这个方法会接收子view中的NestedScrolling事件的滑动距离dx，dy，并交给父View处理，其中consumed数组，即是记录父View处理的dx，dy的消耗。因此这个方法是是父View在子view滚动之前，进行NestedScrolling事件处理的恰当时机。
- onNestedScroll
这个方法包含了NestedScrolling事件的已消耗和未消耗的滑动距离，此方法中可以利用未消耗部分，继续对子View进行处理，以达到不间断处理整体滚动的逻辑。
- onNestedPreFling 
对子View的fling事件做出反应，当返回true的时候，这个fling事件的父View也会参与处理。同样也是父View参与处理fling事件的恰当时机。
- onNestedFling
这个方法会对子View响应fling事件，也是最合适的处理最后的fling收尾工作。

已经了解过处理NestedScrolling事件的核心方法后，接着了解一下NestedScrollingChild的中发起和分发接口的核心方法
```
void setNestedScrollingEnabled(boolean enabled);
boolean isNestedScrollingEnabled();
boolean startNestedScroll(@ScrollAxis int axes);
void stopNestedScroll();
boolean hasNestedScrollingParent();
boolean dispatchNestedScroll(int dxConsumed, int dyConsumed,
            int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow);
boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed,
            @Nullable int[] offsetInWindow);
boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed);
boolean dispatchNestedPreFling(float velocityX, float velocityY);
```

NestedScrollingChild中的方法相对来说简单明了，同样我们分析其中比较重要的几个方法
- startNestedScroll
选择在一个滑动方向上开启嵌套滚动，对应于NestedScrollingParent接口中的onStartNestedScroll。
- stopNestedScroll
终止嵌套滚动操作，对应于NestedScrollingParent接口中的onStopNestedScroll。
- dispatchNestedPreScroll
视图移动之前分发滑动距离，对应于ViewParent中的onNestedPreScroll
- dispatchNestedScroll
 分发ViewParent处理之后的滑动距离，对应于ViewParent中的onNestedScroll

同样的dispatchNestedFling，dispatchNestedPreFling，分别对应于ViewParent中的onNestedFling，onNestedPreFling。
以上是我们对nestedScrolling机制实现的一些核心方法，这些方法的对应关系，以及对每个方法的作用都简单梳理了一下。

## 3 示例分析
通过上述NestedScrolling的机制描述，以及具体的实现方式，具体方法的含义，这些比较抽象和概念型的理论知识后，理论总要付诸于实践。由此开始我们对NestedScrolling机制的应用，简单仿写nestedScrollView的效果。
这里只放上核心的实现，具体的可以看下demo
- 3.1 布局文件
```
<?xml version="1.0" encoding="utf-8"?>
<com.stayli.nested.view.MyNestedParent
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center_horizontal"
    android:orientation="vertical">

   ....

    <com.stayli.nested.view.MyNestedChild
        android:id="@+id/mnc"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="5dp"
        android:orientation="vertical">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:text="底部可滚动滚动组件"
            android:textColor="#f0f"
            android:textSize="20sp" />
       ...
    </com.stayli.nested.view.MyNestedChild>
</com.stayli.nested.view.MyNestedParent>

```
- 3.2 接口实现
 首先NestedParent 的实现  
```
public class MyNestedParent extends LinearLayout implements NestedScrollingParent {

    private MyNestedChild mNestedScrollChild;
     // 这里我们就需要使用辅助类
    private NestedScrollingParentHelper mNestedScrollingParentHelper;
    private int mImgHeight;

    public MyNestedParent(Context context) {
        super(context);
    }

    public MyNestedParent(Context context, AttributeSet attrs) {
        super(context, attrs);
        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
    } 
 ...
    /**
     * @param child  直接子view
     * @param target 目标View
     * @param axes   滑动方向
     * @return 判断参数target是哪一个子view以及滚动的方向，然后决定是否要配合其进行嵌套滚动
     */
    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes) {
        return target instanceof MyNestedChild && (axes & ViewCompat.SCROLL_AXIS_HORIZONTAL) != 0;
    }


    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int nestedScrollAxes) {
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, nestedScrollAxes);
    }

    @Override
    public void onStopNestedScroll(@NonNull View target) {
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
        // 无论是显示 还是 隐藏图片，其实都是在子child 滚动之前就行移动
        if (showImg(dy) || hideImg(dy)) {
            scrollBy(0, -dy);//滚动
            consumed[1] = dy;//记录消费的偏移量
        }
    }

    //后于child滚动
    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {

    }
}
```
NestedChild 实现
```
public class MyNestedChild extends LinearLayout implements NestedScrollingChild {
    private NestedScrollingChildHelper mNestedScrollingChildHelper;
    private final int[] offset = new int[2]; //偏移量
    private final int[] consumed = new int[2]; //偏移 消费
    private int lastY;
    private int showHeight;
    public MyNestedChild(Context context) {
        super(context);
    }
    public MyNestedChild(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    //初始化helper对象
    private NestedScrollingChildHelper getScrollingChildHelper() {
        if (mNestedScrollingChildHelper == null) {
            mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
            mNestedScrollingChildHelper.setNestedScrollingEnabled(true);
        }
        return mNestedScrollingChildHelper;
    }
      ...
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            //按下
            case MotionEvent.ACTION_DOWN:
                lastY = (int) event.getRawY();
                break;
            //移动
            case MotionEvent.ACTION_MOVE:
                int y = (int) (event.getRawY());
                int dy = y - lastY;
                lastY = y;
                if (startNestedScroll(ViewCompat.SCROLL_AXIS_HORIZONTAL)
                        && dispatchNestedPreScroll(0, dy, consumed, offset)) //如果找到了支持嵌套滑动的父类,父类进行了一系列的滑动
                {
                    //获取滑动距离
                    int remain = dy - consumed[1];
                    if (remain != 0) {
                        scrollBy(0, -remain);
                    }

                } else {
                    scrollBy(0, -dy);
                }
                break;
        }

        return true;
    }
     ...
    //实现一下接口
    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        getScrollingChildHelper().setNestedScrollingEnabled(enabled);
    }
 ....
    @Override
    public boolean startNestedScroll(int axes) {
        return getScrollingChildHelper().startNestedScroll(axes);
    }

    @Override
    public void stopNestedScroll() {
        getScrollingChildHelper().stopNestedScroll();
    }
  ...
    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int[] offsetInWindow) {
        return getScrollingChildHelper().dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return getScrollingChildHelper().dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return getScrollingChildHelper().dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return getScrollingChildHelper().dispatchNestedPreFling(velocityX, velocityY);
    }
}

```
- 3.3 关键流程
核心的实现就是对两个接口类的实现，我们通过demo中的日志信息来梳理一下这些关键方法的执行过程。
- 3.3.1. 首先我们先把向上滑动过程中的日志信息截取出来
```
MyNestedChild: onTouchEvent: ACTION_DOWN ---> 事件
MyNestedChild: startNestedScroll:  ---> 发起嵌套滚动
MyNestedParent: onStartNestedScroll: ----> 接收Start嵌套滚动
MyNestedParent: onNestedScrollAccepted: -------> 接收Accepted嵌套滚动
MyNestedChild: onTouchEvent: ACTION_MOVE ---> 事件 转换为 dy -2
MyNestedChild: dispatchNestedPreScroll:  ---> 分发Pre嵌套滚动
MyNestedParent: onNestedPreScroll: 接收Pre嵌套滚动
MyNestedChild: dispatchNestedScroll:  ---> 分发嵌套滚动
MyNestedParent: onNestedScroll: 接收嵌套滚动
MyNestedChild: stopNestedScroll:  ---> 终止嵌套滚动
MyNestedParent: onStopNestedScroll: -------> 终止嵌套滚动
```
基础流程中我们说了需要将Touch事件处理先交给子View处理，并且转换为NestedScrolling所处理的事件，也就是dx，dy。
通过对demo中的日志分析我们不难得出这些关键方法的执行过程，也是NestedScrolling机制的核心工作流程，这里我们轻易的就能看出，方法之间的执行关系。
表格如下：
![方法关系对应图.png](https://upload-images.jianshu.io/upload_images/1731043-3c03e5e7d143964d.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
由此我们可以得到这些核心方法的执行过程：
1. child的startNestedScroll()来发起嵌套滑动流程。parent的onStartNestedScroll()得到响应，若返回true，便是真正开启嵌套滑动，此时OnNestScrollAccepted会被调用。
2. child滚动前，会先调用dispatchNestedPreScroll 进行滑动距离的分发， 此时parent的OnNestedPreScroll()得到响应，开启parent对滑动事件的处理，便可以在此优先处理滚动。
3. child 继续调用 dispatchNestedScroll()，parent的OnNestedScroll()得到响应，开启对子View的滚动处理。
4. 最后child 调用stopNestedScroll 发起终结操作，parent的onStopNestedScroll()做收尾工作。

## 4 核心方法时序
 这里网上有一张嵌套滑动开始到结束的方法调用时序图：
可以更好理解整个NestedScrol事件的分发与处理。
![方法时序图.png](https://upload-images.jianshu.io/upload_images/1731043-33740a1a559cc796.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
```
金色是NestedScrollingChild的方法 , 为View主动调用。
紫色是NestedScrollingParent回调的方法 , 由View的相关方法调用。
橙色是滚动事件被消费的时机
```
看完这个图之后，包括已经了解到理论知识以及对整个NestedScroll事件分发的过程，大家也都有了一定的理解了。接下来就对Android源码中对此机制的实现来进行更深入的分析以此继续加深我们的理解。

## 5 源码实现分析
上面的时序图，是三层嵌套的时序，这里为了使大家更快的弄懂嵌套滑动的机制，就以简单的两层嵌套作为实例进行分析。

这里通过对NestedScrollParent  -> RecyclerView这个常用的嵌套滑动实例进行分析 , 以便深入理解NestedScrolling事件传递的机制。
简单看下xml文件
```
<?xml version="1.0" encoding="utf-8"?>
<com.stayli.nested.view.MyNestedParent
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    <ImageView
        android:id="@+id/cat_header"
        android:layout_width="match_parent"
        android:layout_height="200dp"
        android:src="@drawable/cat" />

    <android.support.v7.widget.RecyclerView
        android:id="@+id/rv_nested"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

</com.stayli.nested.view.MyNestedParent>
```
布局文件只是简单的嵌套，NestedScrollParent继承Linearlayout，并实现NestedScrollingParent接口。
我们分析的这些API到底如何工作的呢，首先我们定义了一个支持嵌套的父View，NestedScrollParent和一个已经在系统的支持嵌套的子View，RecyclerView。
这里我们将 NestedScrollParent（NSP）是父级，RecyclerView（RV）是子级。
当我们滚动RV中的内容时，如果没有嵌套滚动，RV将立即消耗掉scroll事件，这样顶部的图片，就无法正确的移动。而我们真正想要的是上下的两个View像一个整体来进行滚动。或者可以更明确的说：
- 如果RV内容向上滚动，则RV向上滚动的行为应该是NSP整体向上滚动
- 如果RV内容向下滚动，滚动RV下降应引起NSP整体向下滚动

而我们实现这个效果的基础，便是嵌套滑动机制所提供的View与View之间的相互贯穿滚动的关联。
大家在具体翻阅源码的时候会发现
- NestedScrollingParentHelper
- NestedScrollingChildHelper
这两个辅助类内部已经将核心的方法都封装好了，只需要使用对应的调用方法即可，后面源码阅读时，不对这两个类做说明。

对于整个事件的分发啊启动，我们从发起者开始分析：
1.  从`RV`的`onTouchEvent(ACTION_DOWN)`方法被调用。
```
 @Override
    public boolean onTouchEvent(MotionEvent e) {
               ...
        final int action = MotionEventCompat.getActionMasked(e);
        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                ...
                startNestedScroll(nestedScrollAxis);
            } 
            break;

        return true;
```
2.  该`RV`调用其自己的`dispatchNestedPreScroll()`方法，该方法通知`NSP`的onNestedPreScroll() 中处理即将消耗的滑动距离。
```
 @Override
    public boolean onTouchEvent(MotionEvent e) {
       
        final int action = MotionEventCompat.getActionMasked(e);

        switch (action) {

            case MotionEvent.ACTION_MOVE: {
                final int x = (int) (e.getX(index) + 0.5f);
                final int y = (int) (e.getY(index) + 0.5f);
                int dx = mLastTouchX - x;
                int dy = mLastTouchY - y;
                if (dispatchNestedPreScroll(dx, dy, mScrollConsumed, mScrollOffset)) {
                    dx -= mScrollConsumed[0];
                    dy -= mScrollConsumed[1];
                    vtev.offsetLocation(mScrollOffset[0], mScrollOffset[1]);
                    // Updated the nested offsets
                    mNestedOffsets[0] += mScrollOffset[0];
                    mNestedOffsets[1] += mScrollOffset[1];
                }
          break;
        }
        return true;
```
3.  对应的`NSP`的`onNestedPreScroll()`方法被调用，给`NSP`一个机会，在`RV`之前对滚动事件作出反应并通知`RV`已经消耗的距离。
```
    //先于child滚动
    //前3个为输入参数，最后一个是输出参数
    @Override
    public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        if (showImg(dy) || hideImg(dy)) {//如果需要显示或隐藏图片，即需要自己(parent)滚动
            scrollBy(0, -dy);//滚动
            consumed[1] = dy;//告诉child我消费了多少
        }
    }
```
4.  在`RV`消耗滚动的剩余部分。
```
@Override
    public boolean onTouchEvent(MotionEvent e) {
       
        final int action = MotionEventCompat.getActionMasked(e);
     
            case MotionEvent.ACTION_MOVE: {

                final int x = (int) (e.getX(index) + 0.5f);
                final int y = (int) (e.getY(index) + 0.5f);
                int dx = mLastTouchX - x;
                int dy = mLastTouchY - y;

                if (mScrollState == SCROLL_STATE_DRAGGING) {
                    mLastTouchX = x - mScrollOffset[0];
                    mLastTouchY = y - mScrollOffset[1];

                    if (scrollByInternal(
                            canScrollHorizontally ? dx : 0,
                            canScrollVertically ? dy : 0,
                            vtev)) { // 消耗剩余的部分
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (mGapWorker != null && (dx != 0 || dy != 0)) {
                        mGapWorker.postFromTraversal(this, dx, dy);
                    }
                }
            } break;

        return true;
    }
```
5.  该`RV`调用其自己的`dispatchNestedScroll()`方法，该方法通知`NSP`已经消耗的滑动距离的一部分。
```
 boolean scrollByInternal(int x, int y, MotionEvent ev) {
        int unconsumedX = 0, unconsumedY = 0;
        int consumedX = 0, consumedY = 0;

        if (mAdapter != null) {
          if (y != 0) {
                consumedY = mLayout.scrollVerticallyBy(y, mRecycler, mState);
                unconsumedY = y - consumedY;
            }

        }

        if (dispatchNestedScroll(consumedX, consumedY, unconsumedX, unconsumedY, mScrollOffset)) { // 继续回溯通知父View处理未消耗的距离
            mLastTouchX -= mScrollOffset[0];
            mLastTouchY -= mScrollOffset[1];
            if (ev != null) {
                ev.offsetLocation(mScrollOffset[0], mScrollOffset[1]);
            }
            mNestedOffsets[0] += mScrollOffset[0];
            mNestedOffsets[1] += mScrollOffset[1];
        } 
        if (consumedX != 0 || consumedY != 0) {
            dispatchOnScrolled(consumedX, consumedY);
        }
        if (!awakenScrollBars()) {
            invalidate();
        }
        return consumedX != 0 || consumedY != 0;
    }
```
6.  对应的`NSP`的`onNestedScroll()`方法被调用，再次给`NSP`一个机会，消耗的是仍然没有被消耗掉剩余的滚动距离。
```
 //后于child滚动
    @Override
    public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {

    }
```
7.  最后在`RV`的`onTouchEvent(ACTION_UP)`消耗掉整个事件。
```
  @Override
    public boolean onTouchEvent(MotionEvent e) {
        final int action = MotionEventCompat.getActionMasked(e);
            case MotionEvent.ACTION_UP: {
                mVelocityTracker.addMovement(vtev);
                eventAddedToVelocityTracker = true;
                mVelocityTracker.computeCurrentVelocity(1000, mMaxFlingVelocity);
                final float xvel = canScrollHorizontally ?
                        -VelocityTrackerCompat.getXVelocity(mVelocityTracker, mScrollPointerId) : 0;
                final float yvel = canScrollVertically ?
                        -VelocityTrackerCompat.getYVelocity(mVelocityTracker, mScrollPointerId) : 0;
                if (!((xvel != 0 || yvel != 0) && fling((int) xvel, (int) yvel))) {
                    setScrollState(SCROLL_STATE_IDLE);
                }
                resetTouch(); // 处理最后
            } break;
        }
        return true;
    }

    private void resetTouch() {
        if (mVelocityTracker != null) {
            mVelocityTracker.clear();
        }
        stopNestedScroll();
        releaseGlows();
    }
```
8.  对应的`NSP`的`onStopNestedScroll()`方法被调用。
```
 @Override
    public void onStopNestedScroll(View target) { // 进行最后的收尾工作
        mNestedScrollingParentHelper.onStopNestedScroll(target); 
    }

```

嵌套Fling的处理方式与嵌套Scroll非常相似。子View检测到其onTouchEvent(ACTION_UP)方法发生变化，并通过调用其自身的dispatchNestedPreFling()和dispatchNestedFling()方法通知父级。触发对父对象onNestedPreFling()和onNestedFling()方法的调用，并使父对象有机会在孩子使用它之前和之后对Fling事件做出反应。

NestedScrolling机制由实现了NestedScrollingChild接口的子View触发 , 所以事实上 , 当子View实现了NestedScrollingChild接口时 , 默认会使用NestedScrolling机制分发事件给实现了NestedScrollingParent父View。要理解NestedScrolling , 实际上就是要理解NestedScrolling事件分发过程。