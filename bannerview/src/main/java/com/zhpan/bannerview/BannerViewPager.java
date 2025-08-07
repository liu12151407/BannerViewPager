/*
Copyright 2017 zhpanvip The BannerViewPager Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package com.zhpan.bannerview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.RelativeLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.zhpan.bannerview.annotation.AIndicatorGravity;
import com.zhpan.bannerview.annotation.APageStyle;
import com.zhpan.bannerview.annotation.Visibility;
import com.zhpan.bannerview.constants.PageStyle;
import com.zhpan.bannerview.manager.BannerManager;
import com.zhpan.bannerview.manager.BannerOptions;
import com.zhpan.bannerview.provider.ReflectLayoutManager;
import com.zhpan.bannerview.provider.ViewStyleSetter;
import com.zhpan.bannerview.utils.BannerUtils;
import com.zhpan.indicator.IndicatorView;
import com.zhpan.indicator.annotation.AIndicatorSlideMode;
import com.zhpan.indicator.annotation.AIndicatorStyle;
import com.zhpan.indicator.base.IIndicator;
import com.zhpan.indicator.option.IndicatorOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.zhpan.bannerview.BaseBannerAdapter.MAX_VALUE;
import static com.zhpan.bannerview.constants.IndicatorGravity.CENTER;
import static com.zhpan.bannerview.constants.IndicatorGravity.END;
import static com.zhpan.bannerview.constants.IndicatorGravity.START;
import static com.zhpan.bannerview.manager.BannerOptions.DEFAULT_REVEAL_WIDTH;
import static com.zhpan.bannerview.transform.ScaleInTransformer.DEFAULT_MIN_SCALE;
import static com.zhpan.bannerview.utils.BannerUtils.getOriginalPosition;

/**
 * Created by zhpan on 2017/3/28.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class BannerViewPager<T> extends RelativeLayout implements LifecycleObserver {

    // 当前页面位置
    private int currentPosition;

    // 是否使用自定义指示器
    private boolean isCustomIndicator;

    // 是否正在循环播放
    private boolean isLooping;

    // 指示器视图
    private IIndicator mIndicatorView;

    // 指示器布局容器
    private RelativeLayout mIndicatorLayout;

    // ViewPager2实例，用于显示轮播内容
    private ViewPager2 mViewPager;

    // Banner管理器，用于管理Banner的各种配置和选项
    private BannerManager mBannerManager;

    // 主线程Handler，用于处理轮播相关的消息
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Banner适配器，用于提供轮播数据
    private BaseBannerAdapter<T> mBannerPagerAdapter;

    // 页面变化回调接口
    private ViewPager2.OnPageChangeCallback onPageChangeCallback;

    // 轮播任务Runnable
    private final Runnable mRunnable = this::handlePosition;

    // 圆角裁剪相关参数
    private RectF mRadiusRectF;
    private Path mRadiusPath;

    // 触摸事件起始坐标
    private int startX, startY;

    // 生命周期注册器
    private Lifecycle lifecycleRegistry;

    private final ViewPager2.OnPageChangeCallback mOnPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            super.onPageScrolled(position, positionOffset, positionOffsetPixels);
            pageScrolled(position, positionOffset, positionOffsetPixels);
        }

        @Override
        public void onPageSelected(int position) {
            super.onPageSelected(position);
            pageSelected(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            super.onPageScrollStateChanged(state);
            pageScrollStateChanged(state);
        }
    };

    public BannerViewPager(Context context) {
        this(context, null);
    }

    public BannerViewPager(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BannerViewPager(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        mBannerManager = new BannerManager();
        mBannerManager.initAttrs(context, attrs);
        initView();
    }

    private void initView() {
        inflate(getContext(), R.layout.bvp_layout, this);
        mViewPager = findViewById(R.id.vp_main);
        mIndicatorLayout = findViewById(R.id.bvp_layout_indicator);
        mViewPager.setPageTransformer(mBannerManager.getCompositePageTransformer());
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mBannerManager != null && isStopLoopWhenDetachedFromWindow()) {
            stopLoop();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mBannerManager != null && isStopLoopWhenDetachedFromWindow()) {
            startLoop();
        }
    }

    /**
     * 分发触摸事件
     * 在触摸事件发生时控制轮播的暂停和恢复：
     * 1. ACTION_DOWN事件：暂停轮播
     * 2. ACTION_UP/ACTION_CANCEL/ACTION_OUTSIDE事件：恢复轮播
     *
     * @param ev 触摸事件
     * @return 是否消费该事件
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isLooping = true;
                stopLoop();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_OUTSIDE:
                isLooping = false;
                startLoop();
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 拦截触摸事件
     * 处理BannerViewPager的触摸事件拦截逻辑：
     * 1. 如果ViewPager的用户输入被禁用或数据项小于等于1，则不拦截事件
     * 2. ACTION_DOWN事件：记录起始坐标并根据设置决定是否禁止父View拦截事件
     * 3. ACTION_MOVE事件：根据滑动方向和距离判断是否需要拦截事件
     *
     * @param ev 触摸事件
     * @return 是否拦截该事件
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean doNotNeedIntercept = !mViewPager.isUserInputEnabled() || mBannerPagerAdapter != null && mBannerPagerAdapter.getData().size() <= 1;
        if (doNotNeedIntercept) {
            return super.onInterceptTouchEvent(ev);
        }
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = (int) ev.getX();
                startY = (int) ev.getY();
                requestParentDisallowInterceptTouchEvent(!mBannerManager.getBannerOptions().isDisallowParentInterceptDownEvent());
                break;
            case MotionEvent.ACTION_MOVE:
                int endX = (int) ev.getX();
                int endY = (int) ev.getY();
                int disX = Math.abs(endX - startX);
                int disY = Math.abs(endY - startY);
                int orientation = mBannerManager.getBannerOptions().getOrientation();
                if (orientation == ViewPager2.ORIENTATION_VERTICAL) {
                    onVerticalActionMove(endY, disX, disY);
                } else if (orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
                    onHorizontalActionMove(endX, disX, disY);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                requestParentDisallowInterceptTouchEvent(false);
                break;
            case MotionEvent.ACTION_OUTSIDE:
            default:
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    /**
     * 请求父视图不要拦截触摸事件
     * 当Banner需要处理垂直滑动时，调用此方法通知父视图不要拦截触摸事件
     *
     * @param disallowIntercept 是否禁止父视图拦截触摸事件，true表示禁止，false表示允许
     */
    private void requestParentDisallowInterceptTouchEvent(boolean disallowIntercept) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }


    /**
     * 处理垂直方向上的ACTION_MOVE事件
     * 根据当前页面位置和滑动方向判断是否需要请求父视图不拦截触摸事件
     *
     * @param endY 当前触摸点的Y坐标
     * @param disX X轴方向上的滑动距离
     * @param disY Y轴方向上的滑动距离
     */
    private void onVerticalActionMove(int endY, int disX, int disY) {
        // 当垂直滑动距离大于水平滑动距离时
        if (disY > disX) {
            boolean canLoop = mBannerManager.getBannerOptions().isCanLoop();
            // 如果不允许循环播放
            if (!canLoop) {
                // 如果在第一页且向下滑动，则允许父视图拦截事件
                if (currentPosition == 0 && endY - startY > 0) {
                    requestParentDisallowInterceptTouchEvent(false);
                } else {
                    // 判断是否需要禁止父视图拦截事件
                    boolean disallowIntercept = currentPosition != getData().size() - 1 || endY - startY >= 0;
                    requestParentDisallowInterceptTouchEvent(disallowIntercept);
                }
            } else {
                // 允许循环播放时，禁止父视图拦截事件
                requestParentDisallowInterceptTouchEvent(true);
            }
        } else if (disX > disY) {
            // 当水平滑动距离大于垂直滑动距离时，允许父视图拦截事件
            requestParentDisallowInterceptTouchEvent(false);
        }
    }


    /**
     * 处理水平方向上的ACTION_MOVE事件
     * 根据当前页面位置和滑动方向判断是否需要请求父视图不拦截触摸事件
     *
     * @param endX 当前触摸点的X坐标
     * @param disX X轴方向上的滑动距离
     * @param disY Y轴方向上的滑动距离
     */
    private void onHorizontalActionMove(int endX, int disX, int disY) {
        // 当水平滑动距离大于垂直滑动距离时
        if (disX > disY) {
            boolean canLoop = mBannerManager.getBannerOptions().isCanLoop();
            // 如果不允许循环播放
            if (!canLoop) {
                // 如果在第一页且向右滑动，则允许父视图拦截事件
                if (currentPosition == 0 && endX - startX > 0) {
                    requestParentDisallowInterceptTouchEvent(false);
                } else {
                    // 判断是否需要禁止父视图拦截事件
                    requestParentDisallowInterceptTouchEvent(currentPosition != getData().size() - 1 || endX - startX >= 0);
                }
            } else {
                // 允许循环播放时，禁止父视图拦截事件
                requestParentDisallowInterceptTouchEvent(true);
            }
        } else if (disY > disX) {
            // 当垂直滑动距离大于水平滑动距离时，允许父视图拦截事件
            requestParentDisallowInterceptTouchEvent(false);
        }
    }


    /**
     * 页面滚动状态改变时的回调处理
     * 当页面滚动状态发生变化时，通知注册的页面变化回调接口
     *
     * @param state 新的滚动状态
     */
    private void pageScrollStateChanged(int state) {
//        if (mIndicatorView != null) {
//            mIndicatorView.onPageScrollStateChanged(state);
//        }
        if (onPageChangeCallback != null) {
            onPageChangeCallback.onPageScrollStateChanged(state);
        }
    }

    /**
     * 页面选中时的回调处理
     * 当页面被选中时执行以下操作：
     * 1. 计算真实的页面位置
     * 2. 如果需要重置当前项（循环模式下到达边界时），则重置当前项
     * 3. 通知注册的页面变化回调接口
     *
     * @param position 当前选中的页面位置
     */
    private void pageSelected(int position) {
        int size = mBannerPagerAdapter.getListSize();
        boolean canLoop = mBannerManager.getBannerOptions().isCanLoop();
        currentPosition = BannerUtils.getRealPosition(position, size);
        boolean needResetCurrentItem = size > 0 && canLoop && (position == 0 || position == MAX_VALUE - 1);
        if (needResetCurrentItem) {
            resetCurrentItem(currentPosition);
        }
        if (onPageChangeCallback != null) {
            onPageChangeCallback.onPageSelected(currentPosition);
        }
//        if (mIndicatorView != null) {
//            mIndicatorView.onPageSelected(currentPosition);
//        }
    }

    /**
     * 页面滚动时的回调处理
     * 当页面滚动时执行以下操作：
     * 1. 计算真实的页面位置
     * 2. 通知注册的页面变化回调接口
     *
     * @param position             当前页面位置
     * @param positionOffset       偏移量
     * @param positionOffsetPixels 偏移像素
     */
    private void pageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        int listSize = mBannerPagerAdapter.getListSize();
        boolean canLoop = mBannerManager.getBannerOptions().isCanLoop();
        int realPosition = BannerUtils.getRealPosition(position, listSize);
        if (listSize > 0) {
            if (onPageChangeCallback != null) {
                onPageChangeCallback.onPageScrolled(realPosition, positionOffset, positionOffsetPixels);
            }
//            if (mIndicatorView != null) {
//                mIndicatorView.onPageScrolled(realPosition, positionOffset, positionOffsetPixels);
//            }
        }
    }

    /**
     * 处理轮播位置更新
     * 如果适配器不为空、数据项大于1且启用了自动播放，则切换到下一页
     * 并根据设置决定是否平滑滚动
     * 最后重新安排下一次轮播任务
     */
    private void handlePosition() {
        if (mBannerPagerAdapter != null && mBannerPagerAdapter.getListSize() > 1 && isAutoPlay()) {
            mViewPager.setCurrentItem(mViewPager.getCurrentItem() + 1, mBannerManager.getBannerOptions().isAutoScrollSmoothly());
            mHandler.postDelayed(mRunnable, getInterval());
        }
    }

    /**
     * 初始化Banner数据
     * 获取适配器中的数据列表，并进行以下初始化操作：
     * 1. 设置指示器相关值
     * 2. 设置ViewPager
     * 3. 初始化圆角效果
     */
    private void initBannerData() {
        List<T> list = mBannerPagerAdapter.getData();
        if (list != null) {
            setIndicatorValues(list);
            setupViewPager(list);
            initRoundCorner();
        }
    }

    /**
     * 设置指示器相关值
     * 根据Banner配置选项设置指示器的可见性，重置指示器选项，
     * 并根据是否使用自定义指示器或需要创建新的指示器视图来初始化指示器
     *
     * @param list 数据列表，用于初始化指示器
     */
    private void setIndicatorValues(List<? extends T> list) {
        BannerOptions bannerOptions = mBannerManager.getBannerOptions();
        mIndicatorLayout.setVisibility(bannerOptions.getIndicatorVisibility());
        bannerOptions.resetIndicatorOptions();
        if (isCustomIndicator) {
            mIndicatorLayout.removeAllViews();
        } else if (mIndicatorView == null) {
            mIndicatorView = new IndicatorView(getContext());
        }
        initIndicator(bannerOptions.getIndicatorOptions(), list);
    }

    /**
     * 初始化指示器控件
     *
     * @param indicatorOptions 指示器配置选项，包含指示器的样式、颜色、大小等配置信息
     * @param list             数据列表，用于设置指示器的页面数量
     */
    private void initIndicator(IndicatorOptions indicatorOptions, List<? extends T> list) {
        // 检查指示器视图是否已添加到父布局，避免重复添加
        if (((View) mIndicatorView).getParent() == null) {
            mIndicatorLayout.removeAllViews();
            mIndicatorLayout.addView((View) mIndicatorView);
            initIndicatorSliderMargin();
            initIndicatorGravity();
        }
        mIndicatorView.setIndicatorOptions(indicatorOptions);
        indicatorOptions.setPageSize(list.size());
        mIndicatorView.notifyDataChanged();
    }


    /**
     * 初始化指示器的重力位置
     * 该方法根据Banner配置中的指示器重力属性，设置指示器视图在父布局中的对齐方式
     * 无参数
     * 无返回值
     */
    private void initIndicatorGravity() {
        LayoutParams layoutParams = (LayoutParams) ((View) mIndicatorView).getLayoutParams();
        // 根据指示器重力设置不同的布局规则
        switch (mBannerManager.getBannerOptions().getIndicatorGravity()) {
            case CENTER:
                layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
                break;
            case START:
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                break;
            case END:
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                break;
            default:
                break;
        }
    }


    /**
     * 初始化指示器边距
     * 该方法根据Banner配置中的指示器边距属性，设置指示器视图的外边距
     * 如果未设置边距，则使用默认的10dp边距
     * 无参数
     * 无返回值
     */
    private void initIndicatorSliderMargin() {
        MarginLayoutParams layoutParams = (MarginLayoutParams) ((View) mIndicatorView).getLayoutParams();
        BannerOptions.IndicatorMargin indicatorMargin = mBannerManager.getBannerOptions().getIndicatorMargin();
        // 如果未设置指示器边距，则使用默认的10dp边距
        if (indicatorMargin == null) {
            int dp10 = BannerUtils.dp2px(10);
            layoutParams.setMargins(dp10, dp10, dp10, dp10);
        } else {
            layoutParams.setMargins(indicatorMargin.getLeft(), indicatorMargin.getTop(), indicatorMargin.getRight(), indicatorMargin.getBottom());
        }
    }


    /**
     * 判断Banner在从窗口分离时是否停止轮播
     *
     * @return true表示在从窗口分离时停止轮播，false表示不停止
     */
    private boolean isStopLoopWhenDetachedFromWindow() {
        return mBannerManager.getBannerOptions().isStopLoopWhenDetachedFromWindow();
    }


    /**
     * 分发绘制事件，在绘制Banner时添加圆角裁剪效果
     *
     * @param canvas 画布对象，用于绘制Banner内容
     */
    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        float[] roundRectRadiusArray = mBannerManager.getBannerOptions().getRoundRectRadiusArray();
        // 如果圆角裁剪相关参数已设置，则进行圆角裁剪处理
        if (mRadiusRectF != null && mRadiusPath != null && roundRectRadiusArray != null) {
            mRadiusRectF.right = this.getWidth();
            mRadiusRectF.bottom = this.getHeight();
            mRadiusPath.addRoundRect(mRadiusRectF, roundRectRadiusArray, Path.Direction.CW);
            canvas.clipPath(mRadiusPath);
        }
        super.dispatchDraw(canvas);
    }


    /**
     * 初始化Banner的圆角效果
     * 根据Banner配置中的圆角半径设置Banner的圆角效果，仅在Android 5.0及以上版本生效
     * 无参数
     * 无返回值
     */
    private void initRoundCorner() {
        int roundCorner = mBannerManager.getBannerOptions().getRoundRectRadius();
        // 当圆角半径大于0且系统版本为Android 5.0及以上时，应用圆角效果
        if (roundCorner > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ViewStyleSetter.applyRoundCorner(this, roundCorner);
        }
    }


    /**
     * 设置ViewPager相关配置并初始化Banner
     * 包括设置适配器、滚动时间、循环播放、页面变化监听器等相关配置
     *
     * @param list Banner数据列表
     */
    private void setupViewPager(List<T> list) {
        // 检查适配器是否已设置
        if (mBannerPagerAdapter == null) {
            throw new NullPointerException("You must set adapter for BannerViewPager");
        }
        BannerOptions bannerOptions = mBannerManager.getBannerOptions();
        // 设置ViewPager的滚动时间
        if (bannerOptions.getScrollDuration() != 0) {
            ReflectLayoutManager.reflectLayoutManager(mViewPager, bannerOptions.getScrollDuration());
        }
        currentPosition = 0;
        mBannerPagerAdapter.setCanLoop(bannerOptions.isCanLoop());
        mViewPager.setAdapter(mBannerPagerAdapter);
        // 安全地设置循环播放
        if (isCanLoopSafely()) {
            mViewPager.setCurrentItem(getOriginalPosition(list.size()), false);
        }
        // 注册页面变化监听器
        mViewPager.unregisterOnPageChangeCallback(mOnPageChangeCallback);
        mViewPager.registerOnPageChangeCallback(mOnPageChangeCallback);
        mViewPager.setOrientation(bannerOptions.getOrientation());
        mViewPager.setOffscreenPageLimit(bannerOptions.getOffScreenPageLimit());
        initRevealWidth(bannerOptions);
        initPageStyle(bannerOptions.getPageStyle());
        startLoop();
    }


    /**
     * 初始化ViewPager的显示宽度，用于设置页面的露出宽度效果
     *
     * @param bannerOptions Banner配置选项，包含露出宽度等参数
     */
    private void initRevealWidth(BannerOptions bannerOptions) {
        int rightRevealWidth = bannerOptions.getRightRevealWidth();
        int leftRevealWidth = bannerOptions.getLeftRevealWidth();
        // 当左右露出宽度与默认值不同时，需要设置RecyclerView的padding实现露出效果
        if (leftRevealWidth != DEFAULT_REVEAL_WIDTH || rightRevealWidth != DEFAULT_REVEAL_WIDTH) {
            RecyclerView recyclerView = (RecyclerView) mViewPager.getChildAt(0);
            int orientation = bannerOptions.getOrientation();
            int padding2 = bannerOptions.getPageMargin() + rightRevealWidth;
            int padding1 = bannerOptions.getPageMargin() + leftRevealWidth;
            // 确保padding值不为负数
            if (padding1 < 0) padding1 = 0;
            if (padding2 < 0) padding2 = 0;
            // 根据方向设置不同的padding
            if (orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
                recyclerView.setPadding(padding1, 0, padding2, 0);
            } else if (orientation == ViewPager2.ORIENTATION_VERTICAL) {
                recyclerView.setPadding(0, padding1, 0, padding2);
            }
            recyclerView.setClipToPadding(false);
        }
        mBannerManager.createMarginTransformer();
    }


    private void initPageStyle(@APageStyle int pageStyle) {
        float pageScale = mBannerManager.getBannerOptions().getPageScale();
        if (pageStyle == PageStyle.MULTI_PAGE_OVERLAP) {
            mBannerManager.setMultiPageStyle(true, pageScale);
        } else if (pageStyle == PageStyle.MULTI_PAGE_SCALE) {
            mBannerManager.setMultiPageStyle(false, pageScale);
        }
    }

    private void resetCurrentItem(int item) {
        if (isCanLoopSafely()) {
            mViewPager.setCurrentItem(getOriginalPosition(mBannerPagerAdapter.getListSize()) + item, false);
        } else {
            mViewPager.setCurrentItem(item, false);
        }
    }

    private void refreshIndicator(List<? extends T> data) {
        setIndicatorValues(data);
        mBannerManager.getBannerOptions().getIndicatorOptions().setCurrentPosition(BannerUtils.getRealPosition(mViewPager.getCurrentItem(), data.size()));
        mIndicatorView.notifyDataChanged();
    }

    private static final String KEY_SUPER_STATE = "SUPER_STATE";
    private static final String KEY_CURRENT_POSITION = "CURRENT_POSITION";
    private static final String KEY_IS_CUSTOM_INDICATOR = "IS_CUSTOM_INDICATOR";

    private int getInterval() {
        return mBannerManager.getBannerOptions().getInterval();
    }

    private boolean isAutoPlay() {
        return mBannerManager.getBannerOptions().isAutoPlay();
    }

    private boolean isCanLoopSafely() {
        return mBannerManager != null && mBannerManager.getBannerOptions() != null && mBannerManager.getBannerOptions().isCanLoop() && mBannerPagerAdapter != null && mBannerPagerAdapter.getListSize() > 1;
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_SUPER_STATE, superState);
        bundle.putInt(KEY_CURRENT_POSITION, currentPosition);
        bundle.putBoolean(KEY_IS_CUSTOM_INDICATOR, isCustomIndicator);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        Bundle bundle = (Bundle) state;
        Parcelable superState = bundle.getParcelable(KEY_SUPER_STATE);
        super.onRestoreInstanceState(superState);
        currentPosition = bundle.getInt(KEY_CURRENT_POSITION);
        isCustomIndicator = bundle.getBoolean(KEY_IS_CUSTOM_INDICATOR);
        setCurrentItem(currentPosition, false);
    }

    /**
     * @return BannerViewPager data set
     * 获取BannerViewPager的数据集
     * 如果适配器不为空，则返回适配器中的数据列表
     * 否则返回一个空的列表
     */
    public List<T> getData() {
        if (mBannerPagerAdapter != null) {
            return mBannerPagerAdapter.getData();
        }
        return Collections.emptyList();
    }

    /**
     * Start loop
     * 开始轮播，只有在满足以下条件时才会真正开始轮播：
     * 1. 当前未在轮播中
     * 2. 已启用自动播放
     * 3. 适配器不为空且数据项大于1
     * 4. 视图已附加到窗口
     * 5. 生命周期状态为RESUMED或CREATED
     */
    public void startLoop() {
        if (!isLooping && isAutoPlay() && mBannerPagerAdapter != null && mBannerPagerAdapter.getListSize() > 1 && isAttachedToWindow() && (lifecycleRegistry == null || lifecycleRegistry.getCurrentState() == Lifecycle.State.RESUMED || lifecycleRegistry.getCurrentState() == Lifecycle.State.CREATED)) {
            mHandler.postDelayed(mRunnable, getInterval());
            isLooping = true;
        }
    }

    /**
     * 立即开始轮播
     */
    public void startLoopNow() {
        if (!isLooping && isAutoPlay() && mBannerPagerAdapter != null && mBannerPagerAdapter.getListSize() > 1) {
            mHandler.post(mRunnable);
            isLooping = true;
        }
    }

    /**
     * Stop loop
     * 停止轮播，移除轮播任务并更新轮播状态
     */
    public void stopLoop() {
        if (isLooping) {
            mHandler.removeCallbacks(mRunnable);
            isLooping = false;
        }
    }

    public BannerViewPager<T> setAdapter(BaseBannerAdapter<T> adapter) {
        this.mBannerPagerAdapter = adapter;
        return this;
    }

    public BaseBannerAdapter<T> getAdapter() {
        return mBannerPagerAdapter;
    }

    /**
     * 为BannerViewPager设置圆角矩形效果。
     *
     * @param radius 圆角半径
     */
    public BannerViewPager<T> setRoundCorner(@Px int radius) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBannerManager.getBannerOptions().setRoundRectRadius(radius);
        } else {
            setRoundCorner(radius, radius, radius, radius);
        }
        return this;
    }

    /**
     * 为BannerViewPager设置圆角矩形效果。
     *
     * @param topLeftRadius     左上圆角半径
     * @param topRightRadius    右上圆角半径
     * @param bottomLeftRadius  左下圆角半径
     * @param bottomRightRadius 右下圆角半径
     */
    public BannerViewPager<T> setRoundCorner(@Px int topLeftRadius, @Px int topRightRadius, int bottomLeftRadius, int bottomRightRadius) {
        mRadiusRectF = new RectF();
        mRadiusPath = new Path();
        mBannerManager.getBannerOptions().setRoundRectRadius(topLeftRadius, topRightRadius, bottomLeftRadius, bottomRightRadius);
        return this;
    }

    /**
     * 启用/禁用自动播放
     *
     * @param autoPlay 是否启用自动播放
     */
    public BannerViewPager<T> setAutoPlay(boolean autoPlay) {
        mBannerManager.getBannerOptions().setAutoPlay(autoPlay);
        if (isAutoPlay()) {
            mBannerManager.getBannerOptions().setCanLoop(true);
        }
        return this;
    }

    /**
     * 启用/禁用循环播放
     *
     * @param canLoop 是否可以循环播放
     */
    public BannerViewPager<T> setCanLoop(boolean canLoop) {
        mBannerManager.getBannerOptions().setCanLoop(canLoop);
        if (!canLoop) {
            mBannerManager.getBannerOptions().setAutoPlay(false);
        }
        return this;
    }

    /**
     * 设置循环间隔
     *
     * @param interval 循环间隔，单位是毫秒。
     */
    public BannerViewPager<T> setInterval(int interval) {
        mBannerManager.getBannerOptions().setInterval(interval);
        return this;
    }

    /**
     * @param transformer 页面变换器，将修改每个页面的动画属性
     */
    public BannerViewPager<T> setPageTransformer(@Nullable ViewPager2.PageTransformer transformer) {
        if (transformer != null) {
            mViewPager.setPageTransformer(transformer);
        }
        return this;
    }

    /**
     * @param transformer 页面变换器，将修改每个页面的动画属性
     */
    public BannerViewPager<T> addPageTransformer(@Nullable ViewPager2.PageTransformer transformer) {
        if (transformer != null) {
            mBannerManager.addTransformer(transformer);
        }
        return this;
    }

    public void removeTransformer(@Nullable ViewPager2.PageTransformer transformer) {
        if (transformer != null) {
            mBannerManager.removeTransformer(transformer);
        }
    }

    public void removeDefaultPageTransformer() {
        mBannerManager.removeDefaultPageTransformer();
    }

    public void removeMarginPageTransformer() {
        mBannerManager.removeMarginPageTransformer();
    }

    /**
     * 设置页面间距
     *
     * @param pageMargin 页面间距
     */
    public BannerViewPager<T> setPageMargin(@Px int pageMargin) {
        mBannerManager.setPageMargin(pageMargin);
        return this;
    }

    /**
     * 设置项目点击监听器
     *
     * @param onPageClickListener 项目点击监听器
     */
    public BannerViewPager<T> setOnPageClickListener(OnPageClickListener onPageClickListener) {
        setOnPageClickListener(onPageClickListener, false);
        return this;
    }

    public BannerViewPager<T> setOnPageClickListener(OnPageClickListener onPageClickListener, boolean scrollToThisItem) {
        if (mBannerPagerAdapter != null) {
            mBannerPagerAdapter.setPageClickListener((clickedView, realPosition, adapterPosition) -> {
                onPageClickListener.onPageClick(clickedView, realPosition);
                if (scrollToThisItem) {
                    mViewPager.setCurrentItem(adapterPosition);
                }
            });
        }
        return this;
    }

    /**
     * 设置页面滚动持续时间
     *
     * @param scrollDuration 页面滚动持续时间
     */
    public BannerViewPager<T> setScrollDuration(int scrollDuration) {
        mBannerManager.getBannerOptions().setScrollDuration(scrollDuration);
        return this;
    }

    /**
     * 设置指示器颜色
     *
     * @param checkedColor 指示器选中颜色
     * @param normalColor  指示器未选中颜色
     */
    public BannerViewPager<T> setIndicatorSliderColor(@ColorInt int normalColor, @ColorInt int checkedColor) {
        mBannerManager.getBannerOptions().setIndicatorSliderColor(normalColor, checkedColor);
        return this;
    }

    /**
     * 设置指示器圆点半径
     * <p>
     * 如果指示器样式是 {@link com.zhpan.indicator.enums.IndicatorStyle#DASH}
     * 或 {@link com.zhpan.indicator.enums.IndicatorStyle#ROUND_RECT}
     * 指示器短线条宽度=2*半径
     *
     * @param radius 指示器圆点半径
     */
    public BannerViewPager<T> setIndicatorSliderRadius(@Px int radius) {
        setIndicatorSliderRadius(radius, radius);
        return this;
    }

    /**
     * 设置指示器圆点半径
     *
     * @param normalRadius  未选中圆点半径
     * @param checkedRadius 选中圆点半径
     */
    public BannerViewPager<T> setIndicatorSliderRadius(@Px int normalRadius, @Px int checkedRadius) {
        mBannerManager.getBannerOptions().setIndicatorSliderWidth(normalRadius * 2, checkedRadius * 2);
        return this;
    }

    public BannerViewPager<T> setIndicatorSliderWidth(@Px int indicatorWidth) {
        setIndicatorSliderWidth(indicatorWidth, indicatorWidth);
        return this;
    }

    /**
     * 设置指示器短线条宽度，如果指示器样式是
     * {@link com.zhpan.indicator.enums.IndicatorStyle#CIRCLE},
     * 指示器圆半径是indicatorWidth/2.
     *
     * @param normalWidth 如果指示器样式是
     *                    {@link com.zhpan.indicator.enums.IndicatorStyle#DASH}
     *                    参数表示未选中的短线条宽度
     *                    如果指示器样式是 {@link com.zhpan.indicator.enums.IndicatorStyle#ROUND_RECT} 表示
     *                    未选中的圆角矩形宽度
     *                    如果指示器样式是 {@link com.zhpan.indicator.enums.IndicatorStyle#CIRCLE } 表示
     *                    未选中的圆形直径
     * @param checkWidth  如果指示器样式是
     *                    {@link com.zhpan.indicator.enums.IndicatorStyle#DASH}
     *                    参数表示选中的短线条宽度
     *                    如果指示器样式是 {@link com.zhpan.indicator.enums.IndicatorStyle#ROUND_RECT}
     *                    参数表示选中的圆角矩形宽度
     *                    如果指示器样式是 {@link com.zhpan.indicator.enums.IndicatorStyle#CIRCLE } 表示
     *                    选中的圆形直径
     */
    public BannerViewPager<T> setIndicatorSliderWidth(@Px int normalWidth, @Px int checkWidth) {
        mBannerManager.getBannerOptions().setIndicatorSliderWidth(normalWidth, checkWidth);
        return this;
    }

    public BannerViewPager<T> setIndicatorHeight(@Px int indicatorHeight) {
        mBannerManager.getBannerOptions().setIndicatorHeight(indicatorHeight);
        return this;
    }

    /**
     * 设置指示器短线条/圆点的间距
     *
     * @param indicatorGap 指示器间距
     */
    public BannerViewPager<T> setIndicatorSliderGap(@Px int indicatorGap) {
        mBannerManager.getBannerOptions().setIndicatorGap(indicatorGap);
        return this;
    }

    /**
     * 设置指示器视图的可见性状态
     *
     * @param visibility 可见性状态，可选值为 {@link View#VISIBLE}, {@link View#INVISIBLE}, 或 {@link View#GONE}.
     */
    public BannerViewPager<T> setIndicatorVisibility(@Visibility int visibility) {
        mBannerManager.getBannerOptions().setIndicatorVisibility(visibility);
        mIndicatorLayout.setVisibility(visibility);
        return this;
    }

    /**
     * 设置BannerViewPager中指示器的对齐方式
     *
     * @param gravity 指示器对齐方式
     *                {@link com.zhpan.bannerview.constants.IndicatorGravity#CENTER}
     *                {@link com.zhpan.bannerview.constants.IndicatorGravity#START}
     *                {@link com.zhpan.bannerview.constants.IndicatorGravity#END}
     */
    public BannerViewPager<T> setIndicatorGravity(@AIndicatorGravity int gravity) {
        mBannerManager.getBannerOptions().setIndicatorGravity(gravity);
        return this;
    }

    /**
     * 设置指示器滑动模式，默认值是
     * {@link com.zhpan.indicator.enums.IndicatorSlideMode#NORMAL}
     *
     * @param slideMode 指示器滑动模式
     * @see com.zhpan.indicator.enums.IndicatorSlideMode#NORMAL
     * @see com.zhpan.indicator.enums.IndicatorSlideMode#SMOOTH
     */
    public BannerViewPager<T> setIndicatorSlideMode(@AIndicatorSlideMode int slideMode) {
        mBannerManager.getBannerOptions().setIndicatorSlideMode(slideMode);
        return this;
    }

    /**
     * 设置自定义指示器
     * 自定义指示器视图必须继承BaseIndicator或实现IIndicator接口
     *
     * @param customIndicator 自定义指示器视图
     */
    public BannerViewPager<T> setIndicatorView(IIndicator customIndicator) {
        if (customIndicator instanceof View) {
            isCustomIndicator = true;
            mIndicatorView = customIndicator;
        }
        return this;
    }

    /**
     * 设置指示器样式
     *
     * @param indicatorStyle 指示器样式
     * @see com.zhpan.indicator.enums.IndicatorStyle#CIRCLE
     * @see com.zhpan.indicator.enums.IndicatorStyle#DASH
     * @see com.zhpan.indicator.enums.IndicatorStyle#ROUND_RECT
     */
    public BannerViewPager<T> setIndicatorStyle(@AIndicatorStyle int indicatorStyle) {
        mBannerManager.getBannerOptions().setIndicatorStyle(indicatorStyle);
        return this;
    }

    /**
     * 使用数据创建BannerViewPager
     * 如果在创建BannerViewPager时已经获取到数据，可以调用此方法
     */
    public void create(List<T> data) {
        if (mBannerPagerAdapter == null) {
            throw new NullPointerException("You must set adapter for BannerViewPager");
        }
        mBannerPagerAdapter.setData(data);
        initBannerData();
    }

    /**
     * 创建无数据的BannerViewPager
     * 如果在创建BannerViewPager时没有数据（例如，数据来自远程服务器），可以调用此方法
     * 然后，当成功获取数据时，只需调用 {@link #refreshData(List)} 方法刷新即可
     */
    public void create() {
        create(new ArrayList<>());
    }

    /**
     * 设置ViewPager2的方向
     *
     * @param orientation {@link ViewPager2#ORIENTATION_HORIZONTAL} 或
     *                    {@link ViewPager2#ORIENTATION_VERTICAL}
     */
    public BannerViewPager<T> setOrientation(@ViewPager2.Orientation int orientation) {
        mBannerManager.getBannerOptions().setOrientation(orientation);
        return this;
    }

    public void addItemDecoration(@NonNull RecyclerView.ItemDecoration decor, int index) {
        if (isCanLoopSafely()) {
            int pageSize = mBannerPagerAdapter.getListSize();
            int currentItem = mViewPager.getCurrentItem();
            boolean canLoop = mBannerManager.getBannerOptions().isCanLoop();
            int realPosition = BannerUtils.getRealPosition(currentItem, pageSize);
            if (currentItem != index) {
                if (index == 0 && realPosition == pageSize - 1) {
                    mViewPager.addItemDecoration(decor, currentItem + 1);
                } else if (realPosition == 0 && index == pageSize - 1) {
                    mViewPager.addItemDecoration(decor, currentItem - 1);
                } else {
                    mViewPager.addItemDecoration(decor, currentItem + (index - realPosition));
                }
            }
        } else {
            mViewPager.addItemDecoration(decor, index);
        }
    }

    public void addItemDecoration(@NonNull RecyclerView.ItemDecoration decor) {
        mViewPager.addItemDecoration(decor);
    }

    /**
     * 刷新数据
     * 确认已调用 {@link #create()} 或 {@link #create(List)} 方法，
     * 否则数据将不会显示
     * 修复 #209 如果BVP没有附加到Window上时刷新ViewPager2会导致
     * ViewPager2的currentItem被重置为0，从而导致BVP项目快速滚动问题
     * 为避免此问题，只能在已附加到Window上时刷新数据
     */
    public void refreshData(List<? extends T> list) {
        post(() -> {
            if (isAttachedToWindow() && list != null && mBannerPagerAdapter != null) {
                stopLoop();
                mBannerPagerAdapter.setData(list);
                resetCurrentItem(getCurrentItem());
                refreshIndicator(list);
                startLoop();
            }
        });
    }

    public void addData(List<? extends T> list) {
        if (isAttachedToWindow() && list != null && mBannerPagerAdapter != null) {
            List<T> data = mBannerPagerAdapter.getData();
            int initSize = data.size();
            data.addAll(list);
            mBannerPagerAdapter.notifyItemRangeChanged(initSize, list.size());
            resetCurrentItem(getCurrentItem());
            refreshIndicator(data);
        }
    }

    /**
     * Removes the item at the specified position in this list.
     *
     * @param index the index of the item to be removed
     */
    public void removeItem(int index) {
        List<T> data = mBannerPagerAdapter.getData();
        if (isAttachedToWindow() && index >= 0 && index < data.size()) {
            data.remove(index);
            mBannerPagerAdapter.notifyItemRemoved(index);
            resetCurrentItem(getCurrentItem());
            refreshIndicator(data);
        }
    }

    /**
     * 在此列表中的指定位置插入指定元素
     *
     * @param index 要插入指定元素的索引
     * @param item  要插入的元素
     */
    public void insertItem(int index, T item) {
        List<T> data = mBannerPagerAdapter.getData();
        if (isAttachedToWindow() && index >= 0 && index <= data.size()) {
            data.add(index, item);
            mBannerPagerAdapter.notifyItemInserted(index);
            resetCurrentItem(getCurrentItem());
            refreshIndicator(data);
        }
    }

    public void previousPage() {
        setCurrentItem(getCurrentItem() - 1);
    }

    public void nextPage() {
        setCurrentItem(getCurrentItem() + 1);
    }

    /**
     * @return 当前选中的页面位置。
     */
    public int getCurrentItem() {
        return currentPosition;
    }

    /**
     * 设置当前选中的页面。如果ViewPager已经完成了与当前适配器的第一次布局，
     * 则当前项目和指定项目之间将有平滑的动画过渡。
     *
     * @param item 要选择的项目索引
     */
    public void setCurrentItem(int item) {
        setCurrentItem(item, true);
    }

    /**
     * 设置当前选中的页面。
     *
     * @param item         要选择的项目索引
     * @param smoothScroll true表示平滑滚动到新项目，false表示立即过渡
     */
    public void setCurrentItem(int item, boolean smoothScroll) {
        if (isCanLoopSafely()) {
            stopLoop();
            int currentItem = mViewPager.getCurrentItem();
            int realPosition = BannerUtils.getRealPosition(currentItem, mBannerPagerAdapter.getListSize());
            mViewPager.setCurrentItem(currentItem + (item - realPosition), smoothScroll);
            startLoop();
        } else {
            mViewPager.setCurrentItem(item, smoothScroll);
        }
    }

    /**
     * 为 {@link ViewPager2} 设置默认的页面变换器
     * 可选参数:
     * {@link PageStyle#MULTI_PAGE_OVERLAP}
     * {@link PageStyle#MULTI_PAGE_SCALE}
     * {@link PageStyle#NORMAL}
     */
    public BannerViewPager<T> setPageStyle(@APageStyle int pageStyle) {
        return setPageStyle(pageStyle, DEFAULT_MIN_SCALE);
    }

    public BannerViewPager<T> setPageStyle(@APageStyle int pageStyle, float pageScale) {
        mBannerManager.getBannerOptions().setPageStyle(pageStyle);
        mBannerManager.getBannerOptions().setPageScale(pageScale);
        return this;
    }

    /**
     * @param revealWidth 在多页模式下，左右两侧项目的暴露宽度
     */
    public BannerViewPager<T> setRevealWidth(@Px int revealWidth) {
        setRevealWidth(revealWidth, revealWidth);
        return this;
    }

    /**
     * 此方法适用于多页模式 {@link #setPageStyle(int)}
     *
     * @param leftRevealWidth  左侧暴露宽度
     * @param rightRevealWidth 右侧暴露宽度
     */
    public BannerViewPager<T> setRevealWidth(@Px int leftRevealWidth, @Px int rightRevealWidth) {
        mBannerManager.getBannerOptions().setRightRevealWidth(rightRevealWidth);
        mBannerManager.getBannerOptions().setLeftRevealWidth(leftRevealWidth);
        return this;
    }

    /**
     * 建议使用默认的offScreenPageLimit。
     */
    public BannerViewPager<T> setOffScreenPageLimit(int offScreenPageLimit) {
        mBannerManager.getBannerOptions().setOffScreenPageLimit(offScreenPageLimit);
        return this;
    }

    public BannerViewPager<T> setIndicatorMargin(@Px int left, @Px int top, @Px int right, @Px int bottom) {
        mBannerManager.getBannerOptions().setIndicatorMargin(left, top, right, bottom);
        return this;
    }

    /**
     * 启用或禁用用户发起的滚动
     */
    public BannerViewPager<T> setUserInputEnabled(boolean userInputEnabled) {
        mBannerManager.getBannerOptions().setUserInputEnabled(userInputEnabled);
        mViewPager.setUserInputEnabled(userInputEnabled);
        return this;
    }

    public interface OnPageClickListener {
        void onPageClick(View clickedView, int position);
    }

    public BannerViewPager<T> registerOnPageChangeCallback(ViewPager2.OnPageChangeCallback onPageChangeCallback) {
        this.onPageChangeCallback = onPageChangeCallback;
        return this;
    }

    /**
     * @deprecated 使用 {@link #registerLifecycleObserver(Lifecycle)} 代替。
     */
    @Deprecated
    public BannerViewPager<T> setLifecycleRegistry(Lifecycle lifecycleRegistry) {
        registerLifecycleObserver(lifecycleRegistry);
        return this;
    }

    public BannerViewPager<T> registerLifecycleObserver(Lifecycle lifecycleRegistry) {
        lifecycleRegistry.addObserver(this);
        this.lifecycleRegistry = lifecycleRegistry;
        return this;
    }

    public BannerViewPager<T> removeLifecycleObserver(Lifecycle lifecycleRegistry) {
        lifecycleRegistry.removeObserver(this);
        return this;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    public void onPause() {
        stopLoop();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    public void onResume() {
        startLoop();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void onDestroy() {
        stopLoop();
    }

    /**
     * 设置是否允许在BVP的{@link MotionEvent#ACTION_DOWN}事件中禁止父View对事件的拦截，该方法
     * 用于解决CoordinatorLayout+CollapsingToolbarLayout在嵌套BVP时引起的滑动冲突问题。
     * <p>
     * BVP在处理ViewPager2嵌套滑动冲突时，在{@link #onInterceptTouchEvent(MotionEvent)}
     * 方法的{@link MotionEvent#ACTION_DOWN}事件中禁止了BVP的父View对触摸事件的拦截，
     * 导致CollapsingToolbarLayout的布局无法获取{@link MotionEvent#ACTION_DOWN}事件，
     * 致使CollapsingToolbarLayout无法处理down事件后的一系列事件而无法滑动。
     * 对于这种情况可以调用该方法不允许在BVP在{@link MotionEvent#ACTION_DOWN}事件中禁止父View的事件拦截。
     * </p>
     * 调用该方法将disallowIntercept设置为true后虽然解决了滑动冲突，但也会造成一定的不良影响，即如果BVP设置
     * 水平滑动，同时BVP外部也是可以水平滑动的ViewPager，则存在较小概率的滑动冲突，即滑动BVP的同时可能会触发
     * 外部ViewPager的滑动。但这一问题到目前为止似乎没有好的解决方案。
     *
     * @param disallowParentInterceptDownEvent 是否允许BVP在{@link MotionEvent#ACTION_DOWN}事件中禁止父View拦截事件，默认值为false
     *                                         true 不允许BVP在{@link MotionEvent#ACTION_DOWN}时间中禁止父View的时间拦截，
     *                                         设置disallowIntercept为true可以解决CoordinatorLayout+CollapsingToolbarLayout的滑动冲突
     *                                         false 允许BVP在{@link MotionEvent#ACTION_DOWN}时间中禁止父View的时间拦截，
     */

    public BannerViewPager<T> disallowParentInterceptDownEvent(boolean disallowParentInterceptDownEvent) {
        mBannerManager.getBannerOptions().setDisallowParentInterceptDownEvent(disallowParentInterceptDownEvent);
        return this;
    }

    /**
     * 设置从右到左模式。
     *
     * @param rtlMode true:从右到左模式,
     *                false:从右到左模式。
     */
    public BannerViewPager<T> setRTLMode(boolean rtlMode) {
        mViewPager.setLayoutDirection(rtlMode ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        mBannerManager.getBannerOptions().setRtl(rtlMode);
        return this;
    }

    /**
     * @param stopLoopWhenDetachedFromWindow 当BVP滑动出屏幕的时候是否要停止轮播，
     *                                       <p>
     *                                       true:滑动出屏幕停止自动轮播，false:滑动出屏幕继续自动轮播。默认值为true
     */
    public BannerViewPager<T> stopLoopWhenDetachedFromWindow(boolean stopLoopWhenDetachedFromWindow) {
        mBannerManager.getBannerOptions().setStopLoopWhenDetachedFromWindow(stopLoopWhenDetachedFromWindow);
        return this;
    }

    /**
     * @param showIndicatorWhenOneItem 只有一个item时是否显示指示器，
     *                                 true：显示，false：不显示，默认值false
     */
    public BannerViewPager<T> showIndicatorWhenOneItem(boolean showIndicatorWhenOneItem) {
        mBannerManager.getBannerOptions().showIndicatorWhenOneItem(showIndicatorWhenOneItem);
        return this;
    }

    /**
     * @param autoScrollSmoothly 是否自动播放滚动平滑。
     */
    public BannerViewPager<T> setAutoPlaySmoothly(boolean autoScrollSmoothly) {
        mBannerManager.getBannerOptions().setAutoScrollSmoothly(autoScrollSmoothly);
        return this;
    }

    /**
     * @deprecated 使用 {@link BannerViewPager#disallowParentInterceptDownEvent(boolean)} 代替。
     */
    @Deprecated
    public BannerViewPager<T> disallowInterceptTouchEvent(boolean disallowIntercept) {
        mBannerManager.getBannerOptions().setDisallowParentInterceptDownEvent(disallowIntercept);
        return this;
    }

    /**
     * 为BannerViewPager设置圆角矩形效果。
     *
     * @param radius 圆角半径
     * @deprecated 使用 {@link #setRoundCorner(int)} 代替。
     */
    @Deprecated
    public BannerViewPager<T> setRoundRect(@Px int radius) {
        return setRoundCorner(radius);
    }

    /**
     * 为BannerViewPager设置圆角矩形效果。
     *
     * @param topLeftRadius     左上圆角半径
     * @param topRightRadius    右上圆角半径
     * @param bottomLeftRadius  左下圆角半径
     * @param bottomRightRadius 右下圆角半径
     * @deprecated 使用 {@link #setRoundCorner(int, int, int, int)} 代替。
     */
    @Deprecated
    public BannerViewPager<T> setRoundRect(@Px int topLeftRadius, @Px int topRightRadius, int bottomLeftRadius, int bottomRightRadius) {
        return setRoundCorner(topLeftRadius, topRightRadius, bottomLeftRadius, bottomRightRadius);
    }
}
