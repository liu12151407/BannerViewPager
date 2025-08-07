package com.example.zhpan.banner.fragment

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioGroup
import com.blankj.utilcode.util.ToastUtils
import com.example.zhpan.banner.R
import com.example.zhpan.banner.R.color
import com.example.zhpan.banner.R.dimen
import com.example.zhpan.banner.adapter.ViewBindingSampleAdapter
import com.zhpan.bannerview.BannerViewPager
import com.zhpan.bannerview.annotation.APageStyle
import com.zhpan.bannerview.constants.PageStyle
import com.zhpan.bannerview.utils.BannerUtils
import com.zhpan.indicator.enums.IndicatorSlideMode

/**
 * 页面Fragment类，用于展示BannerViewPager的不同页面样式
 * Created by zhpan on 2018/7/24.
 */
class PageFragment : BaseFragment() {
    // BannerViewPager实例，用于展示轮播图
    private lateinit var mViewPager: BannerViewPager<Int>

    // 页面样式选择的RadioGroup
    private lateinit var mRadioGroupPageStyle: RadioGroup

    // 更多样式选择的RadioGroup
    private lateinit var mRadioGroupMoreStyle: RadioGroup

    // 返回布局资源ID
    override val layout: Int
        get() = R.layout.fragment_find

    // 初始化标题（空实现）
    override fun initTitle() {}

    /**
     * 初始化视图组件
     * @param savedInstanceState Bundle对象，包含fragment之前保存的状态
     * @param view View对象，fragment的根视图
     */
    override fun initView(savedInstanceState: Bundle?, view: View) {
        // 通过findViewById获取BannerViewPager实例
        mViewPager = view.findViewById(R.id.banner_view)
        // 获取页面样式选择的RadioGroup
        mRadioGroupPageStyle = view.findViewById(R.id.rg_page_style)
        // 获取更多样式选择的RadioGroup
        mRadioGroupMoreStyle = view.findViewById(R.id.rg_more_page_style)

        // 设置下一页按钮点击事件
        view.findViewById<Button>(R.id.btn_next).setOnClickListener {
            mViewPager.nextPage()
            itemClick(mViewPager.currentItem)
        }

        // 设置上一页按钮点击事件
        view.findViewById<Button>(R.id.btn_pre).setOnClickListener {
            mViewPager.previousPage()
            itemClick(mViewPager.currentItem)
        }

        // 初始化BannerViewPager
        initBVP()
        // 初始化RadioGroup选择事件
        initRadioGroup()

        // 默认选中第一个样式选项
        view.findViewById<View>(R.id.rb_multi_page_overlap).performClick()
    }

    /**
     * 初始化BannerViewPager配置
     */
    private fun initBVP() {
        mViewPager.apply {
            // 注册生命周期观察者
            registerLifecycleObserver(lifecycle)
            // 设置适配器
            adapter = ViewBindingSampleAdapter(resources.getDimensionPixelOffset(dimen.dp_8))
            // 设置指示器滑动模式
            setIndicatorSlideMode(IndicatorSlideMode.SCALE)
            // 设置指示器滑块颜色
            setIndicatorSliderColor(
                getColor(color.red_normal_color),
                getColor(color.red_checked_color)
            )
            // 设置指示器滑块半径
            setIndicatorSliderRadius(
                resources.getDimensionPixelOffset(dimen.dp_4),
                resources.getDimensionPixelOffset(dimen.dp_5)
            )
            // 设置页面点击事件监听器
            setOnPageClickListener({ _: View, position: Int -> itemClick(position) }, true)
            // 设置轮播间隔时间
            setInterval(5000)
        }
    }

    /**
     * 初始化RadioGroup选择事件监听
     */
    private fun initRadioGroup() {
        // 设置页面样式选择监听
        mRadioGroupPageStyle.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            when (checkedId) {
                R.id.rb_multi_page_overlap ->
                    setupBanner(
                        PageStyle.MULTI_PAGE_OVERLAP,
                        resources.getDimensionPixelOffset(dimen.dp_10)
                    )

                R.id.rb_multi_page_overlap1 ->
                    setupBanner(
                        PageStyle.MULTI_PAGE_OVERLAP,
                        resources.getDimensionPixelOffset(dimen.dp_100)
                    )

                R.id.rb_multi_page_scale ->
                    setupBanner(
                        PageStyle.MULTI_PAGE_SCALE,
                        resources.getDimensionPixelOffset(dimen.dp_10)
                    )

                R.id.rb_multi_scale_page2 ->
                    setupBanner(
                        PageStyle.MULTI_PAGE_SCALE,
                        resources.getDimensionPixelOffset(dimen.dp_120)
                    )

                R.id.rb_multi_scale_page3 -> {
                    setupBanner(
                        PageStyle.MULTI_PAGE_SCALE,
                        resources.getDimensionPixelOffset(dimen.dp_0),
                        resources.getDimensionPixelOffset(dimen.dp_200)
                    )
                }
            }
        }

        // 设置更多样式选择监听
        mRadioGroupMoreStyle.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            when (checkedId) {
                R.id.rb_multi_page3 ->
                    setupMultiPageBanner()

                R.id.rb_multi_page4 ->
                    setupRightPageReveal()

                R.id.rb_netease_music_style ->
                    setNetEaseMusicStyle()

                R.id.rb_qq_music_style ->
                    setQQMusicStyle()
            }
        }
    }

    /**
     * 不同页面样式可以通过使用[BannerViewPager.setPageStyle]和[BannerViewPager.setRevealWidth]实现
     *
     * @param pageStyle 可选参数[PageStyle.MULTI_PAGE_SCALE]和[PageStyle.MULTI_PAGE_OVERLAP]
     * @param revealWidth 在多页面模式下，左右两侧项目的暴露宽度
     */
    private fun setupBanner(@APageStyle pageStyle: Int, revealWidth: Int) {
        setupBanner(pageStyle, revealWidth, revealWidth)
    }

    /**
     * 设置Banner样式
     * @param pageStyle 页面样式
     * @param leftRevealWidth 左侧暴露宽度
     * @param rightRevealWidth 右侧暴露宽度
     */
    private fun setupBanner(
        @APageStyle pageStyle: Int,
        leftRevealWidth: Int,
        rightRevealWidth: Int
    ) {
        mViewPager
            .setPageMargin(resources.getDimensionPixelOffset(dimen.dp_15))
            .setScrollDuration(800)
            .setRevealWidth(leftRevealWidth, rightRevealWidth)
            .setPageStyle(pageStyle, 0.85f)
            .create(getPicList(4))
    }

    /**
     * 多页面样式1
     */
    private fun setupMultiPageBanner() {
        mViewPager
            .setPageMargin(resources.getDimensionPixelOffset(dimen.dp_10))
            .setRevealWidth(resources.getDimensionPixelOffset(dimen.dp_10))
            .create(getPicList(4))
        mViewPager.removeDefaultPageTransformer()
    }

    /**
     * 多页面样式2
     */
    private fun setupRightPageReveal() {
        mViewPager
            .setPageMargin(resources.getDimensionPixelOffset(dimen.dp_10))
            .setRevealWidth(0, resources.getDimensionPixelOffset(dimen.dp_30))
            .create(getPicList(4))
        mViewPager.removeDefaultPageTransformer()
    }

    /**
     * 网易云音乐Banner样式
     */
    private fun setNetEaseMusicStyle() {
        mViewPager
            .setPageMargin(resources.getDimensionPixelOffset(dimen.dp_20))
            .setRevealWidth(resources.getDimensionPixelOffset(dimen.dp_m_10))
            .setIndicatorSliderColor(
                getColor(color.red_normal_color),
                getColor(color.red_checked_color)
            )
            .setOnPageClickListener { view: View?, position: Int ->
                ToastUtils.showShort(
                    "position:$position"
                )
            }
            .setInterval(5000).create(getPicList(4))
        mViewPager.removeDefaultPageTransformer()
    }

    /**
     * QQ音乐Banner样式
     */
    private fun setQQMusicStyle() {
        mViewPager
            .setPageMargin(resources.getDimensionPixelOffset(dimen.dp_15))
            .setRevealWidth(BannerUtils.dp2px(0f))
            .setIndicatorSliderColor(
                getColor(color.red_normal_color),
                getColor(color.red_checked_color)
            )
            .setOnPageClickListener { _: View?, position: Int ->
                ToastUtils.showShort(
                    "position:$position"
                )
            }
            .setInterval(5000).create(getPicList(4))
        mViewPager.removeDefaultPageTransformer()
    }

    /**
     * 处理Banner项目点击事件
     * @param position 点击项目的索引位置
     */
    private fun itemClick(position: Int) {
        ToastUtils.showShort("position:$position")
    }

    /**
     * 伴生对象，用于创建PageFragment实例
     */
    companion object {
        /**
         * PageFragment实例
         */
        val instance: PageFragment
            get() = PageFragment()
    }
}