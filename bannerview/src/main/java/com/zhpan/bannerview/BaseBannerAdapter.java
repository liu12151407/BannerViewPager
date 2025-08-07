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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zhpan.bannerview.utils.BannerUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 由zhpan于2017/3/28创建。
 */
public abstract class BaseBannerAdapter<T> extends RecyclerView.Adapter<BaseViewHolder<T>> {
    protected List<T> mList = new ArrayList<>();
    private boolean isCanLoop;
    public static final int MAX_VALUE = 10000;
    private PageClickListener mPageClickListener;

    @NonNull
    @Override
    public final BaseViewHolder<T> onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(getLayoutId(viewType), parent, false);
        BaseViewHolder<T> viewHolder = createViewHolder(parent, itemView, viewType);
        itemView.setOnClickListener(clickedView -> {
            int adapterPosition = viewHolder.getAbsoluteAdapterPosition();
            if (mPageClickListener != null && adapterPosition != RecyclerView.NO_POSITION) {
                int realPosition = BannerUtils.getRealPosition(adapterPosition, getListSize());
                mPageClickListener.onPageClick(clickedView, realPosition, adapterPosition);
            }
        });
        return viewHolder;
    }

    @Override
    public final void onBindViewHolder(@NonNull BaseViewHolder<T> holder, final int position) {
        int realPosition = BannerUtils.getRealPosition(position, getListSize());
        bindData(holder, mList.get(realPosition), realPosition, getListSize());
    }

    @Override
    public final int getItemViewType(int position) {
        int realPosition = BannerUtils.getRealPosition(position, getListSize());
        return getViewType(realPosition);
    }

    @Override
    public final int getItemCount() {
        if (isCanLoop && getListSize() > 1) {
            return MAX_VALUE;
        } else {
            return getListSize();
        }
    }

    List<T> getData() {
        return mList;
    }

    void setData(List<? extends T> list) {
        if (null != list) {
            int size = mList.size();
            mList.clear();
            notifyItemRangeChanged(0, size);
            mList.addAll(list);
            notifyItemRangeChanged(0, mList.size());
        }
    }

    void setCanLoop(boolean canLoop) {
        isCanLoop = canLoop;
    }

    void setPageClickListener(PageClickListener pageClickListener) {
        mPageClickListener = pageClickListener;
    }

    int getListSize() {
        return mList.size();
    }

    protected int getViewType(int position) {
        return 0;
    }

    @SuppressWarnings("unused")
    public boolean isCanLoop() {
        return isCanLoop;
    }

    /**
     * 通常，子类不需要重写此方法，除非您想使用自定义的ViewHolder。
     * 此方法由{@link #onCreateViewHolder(ViewGroup, int)}调用以创建默认的{@link
     * BaseViewHolder}
     *
     * @param parent   新View绑定到适配器位置后将添加到的ViewGroup。
     * @param itemView 项目View。
     * @param viewType 新View的视图类型。
     * @return 扩展自{@link BaseViewHolder}的ViewHolder。
     */
    public BaseViewHolder<T> createViewHolder(@NonNull ViewGroup parent, View itemView, int viewType) {
        return new BaseViewHolder<>(itemView);
    }

    /**
     * @param holder   应该更新以表示数据集中给定位置项目内容的ViewHolder。
     * @param data     当前项目数据。
     * @param position 当前项目位置。
     * @param pageSize BVP的页面大小，等于{@link BaseBannerAdapter#getListSize()}。
     */
    protected abstract void bindData(BaseViewHolder<T> holder, T data, int position, int pageSize);

    /**
     * @param viewType 新View的视图类型。
     * @return 项目视图布局。
     */
    public abstract @LayoutRes int getLayoutId(int viewType);

    interface PageClickListener {
        void onPageClick(View clickedView, int realPosition, int adapterPosition);
    }
}
