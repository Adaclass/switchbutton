package com.sd.lib.switchbutton;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.sd.lib.gesture.FTouchHelper;

public abstract class BaseSwitchButton extends ViewGroup implements SwitchButton
{
    private View mViewNormal;
    private View mViewChecked;
    private View mViewThumb;
    protected final SBAttrModel mAttrModel = new SBAttrModel();

    private boolean mIsChecked;

    private OnCheckedChangeCallback mOnCheckedChangeCallback;
    private OnViewPositionChangeCallback mOnViewPositionChangeCallback;

    protected boolean mIsDebug;

    public BaseSwitchButton(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        mAttrModel.parse(context, attrs);
        mIsChecked = mAttrModel.isChecked;
        mIsDebug = mAttrModel.isDebug;

        final View normal = new View(getContext());
        normal.setBackgroundResource(mAttrModel.imageNormalResId);
        addView(normal, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mViewNormal = normal;

        final View checked = new View(getContext());
        checked.setBackgroundResource(mAttrModel.imageCheckedResId);
        addView(checked, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mViewChecked = checked;

        final View thumb = new SBThumbView(getContext());
        thumb.setBackgroundResource(mAttrModel.imageThumbResId);
        addView(thumb, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mViewThumb = thumb;
    }

    public void setDebug(boolean debug)
    {
        mIsDebug = debug;
    }

    protected final String getDebugTag()
    {
        return getClass().getSimpleName();
    }

    @Override
    public void onViewAdded(View child)
    {
        super.onViewAdded(child);
        if (getChildCount() > 3)
            throw new IllegalArgumentException("can not add view to SwitchButton");
    }

    @Override
    public void onViewRemoved(View child)
    {
        super.onViewRemoved(child);
        if (getChildCount() < 3)
            throw new IllegalArgumentException("can not remove view from SwitchButton");
    }

    /**
     * 返回normal状态下手柄view的left值
     *
     * @return
     */
    protected final int getLeftNormal()
    {
        return mAttrModel.marginLeft;
    }

    /**
     * 返回checked状态下手柄view的left值
     *
     * @return
     */
    protected final int getLeftChecked()
    {
        return getMeasuredWidth() - mViewThumb.getMeasuredWidth() - mAttrModel.marginRight;
    }

    /**
     * 返回手柄view可以移动的宽度大小
     *
     * @return
     */
    protected final int getAvailableWidth()
    {
        return getLeftChecked() - getLeftNormal();
    }

    /**
     * 返回手柄view滚动的距离
     *
     * @return
     */
    private int getScrollDistance()
    {
        return mViewThumb.getLeft() - getLeftNormal();
    }

    /**
     * view是否处于空闲状态（静止且未被拖动状态）
     *
     * @return
     */
    protected abstract boolean isViewIdle();

    /**
     * 停止滑动动画
     */
    protected abstract void abortAnimation();

    /**
     * 执行滑动逻辑
     *
     * @param startLeft
     * @param endLeft
     * @return
     */
    protected abstract boolean onSmoothSlide(int startLeft, int endLeft);

    /**
     * 根据状态改变view
     *
     * @param anim
     */
    protected final void updateViewByState(boolean anim)
    {
        if (mIsDebug)
            Log.i(getDebugTag(), "----------updateViewByState anim:" + anim);

        abortAnimation();

        boolean isScrollerStarted = false;

        final int startLeft = mViewThumb.getLeft();
        final int endLeft = mIsChecked ? getLeftChecked() : getLeftNormal();

        if (startLeft != endLeft)
        {
            if (mIsDebug)
                Log.i(getDebugTag(), "updateViewByState:" + startLeft + " -> " + endLeft);

            if (anim)
            {
                isScrollerStarted = onSmoothSlide(startLeft, endLeft);
            } else
            {
                layoutInternal();
            }
        }

        if (isScrollerStarted)
        {
            // 触发滚动成功，不需要立即更新view的可见状态，动画结束后更新
            postInvalidateOnAnimation();
        } else
        {
            // 立即更新view的可见状态
            dealViewIdle();
        }

        if (mViewThumb.isSelected() != mIsChecked)
            mViewThumb.setSelected(mIsChecked);
    }

    protected final void dealViewIdle()
    {
        if (mIsDebug)
            Log.i(getDebugTag(), "dealViewIdle:" + mIsChecked);

        if (mIsChecked)
        {
            showCheckedView(true);
            showNormalView(false);
        } else
        {
            showCheckedView(false);
            showNormalView(true);
        }
    }

    private void showCheckedView(boolean show)
    {
        float alpha = show ? 1.0f : 0f;
        if (mViewChecked.getAlpha() != alpha)
            mViewChecked.setAlpha(alpha);
    }

    private void showNormalView(boolean show)
    {
        float alpha = show ? 1.0f : 0f;
        if (mViewNormal.getAlpha() != alpha)
            mViewNormal.setAlpha(alpha);
    }

    /**
     * 移动手柄view
     *
     * @param delta 移动量
     */
    protected final void moveView(int delta)
    {
        if (delta == 0)
            return;

        final int current = mViewThumb.getLeft();
        final int min = getLeftNormal();
        final int max = getLeftChecked();
        delta = FTouchHelper.getLegalDelta(current, min, max, delta);

        if (delta == 0)
            return;

        mViewThumb.offsetLeftAndRight(delta);
        notifyViewPositionChanged();
    }

    private void notifyViewPositionChanged()
    {
        final float percent = getScrollPercent();
        mViewChecked.setAlpha(percent);
        mViewNormal.setAlpha(1.0f - percent);

        if (mOnViewPositionChangeCallback != null)
            mOnViewPositionChangeCallback.onViewPositionChanged(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        measureChild(mViewNormal, widthMeasureSpec, heightMeasureSpec);
        measureChild(mViewChecked, widthMeasureSpec, heightMeasureSpec);

        final ViewGroup.LayoutParams lpThumb = mViewThumb.getLayoutParams();
        measureChild(mViewThumb, getChildMeasureSpec(widthMeasureSpec, mAttrModel.marginLeft + mAttrModel.marginRight, lpThumb.width),
                getChildMeasureSpec(heightMeasureSpec, mAttrModel.marginTop + mAttrModel.marginBottom, lpThumb.height));

        int width = Math.max(mViewThumb.getMeasuredWidth(), Math.max(mViewNormal.getMeasuredWidth(), mViewChecked.getMeasuredWidth()));
        int height = Math.max(mViewThumb.getMeasuredHeight(), Math.max(mViewNormal.getMeasuredHeight(), mViewChecked.getMeasuredHeight()));

        width = getMeasureSize(width, widthMeasureSpec);
        height = getMeasureSize(height, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }

    private static int getMeasureSize(int size, int measureSpec)
    {
        int result = 0;

        final int modeSpec = View.MeasureSpec.getMode(measureSpec);
        final int sizeSpec = View.MeasureSpec.getSize(measureSpec);

        switch (modeSpec)
        {
            case View.MeasureSpec.UNSPECIFIED:
                result = size;
                break;
            case View.MeasureSpec.EXACTLY:
                result = sizeSpec;
                break;
            case View.MeasureSpec.AT_MOST:
                result = Math.min(size, sizeSpec);
                break;
        }
        return result;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b)
    {
        layoutInternal();
        notifyViewPositionChanged();
    }

    private void layoutInternal()
    {
        mViewNormal.layout(0, 0, mViewNormal.getMeasuredWidth(), mViewNormal.getMeasuredHeight());
        mViewChecked.layout(0, 0, mViewChecked.getMeasuredWidth(), mViewChecked.getMeasuredHeight());

        int left = 0;
        int top = mAttrModel.marginTop;
        if (isViewIdle())
        {
            left = mIsChecked ? getLeftChecked() : getLeftNormal();
        } else
        {
            left = mViewThumb.getLeft();
        }
        mViewThumb.layout(left, top,
                left + mViewThumb.getMeasuredWidth(), top + mViewThumb.getMeasuredHeight());
    }

    //----------SwitchButton implements start----------

    @Override
    public boolean isChecked()
    {
        return mIsChecked;
    }

    @Override
    public boolean setChecked(boolean checked, boolean anim, boolean notifyCallback)
    {
        boolean changed = false;

        if (mIsChecked != checked)
        {
            mIsChecked = checked;
            changed = true;

            if (mIsDebug)
                Log.e(getDebugTag(), "setChecked:" + checked);

            updateViewByState(anim);

            if (notifyCallback)
            {
                if (mOnCheckedChangeCallback != null)
                    mOnCheckedChangeCallback.onCheckedChanged(mIsChecked, this);
            }
        }

        if (!anim && !changed)
            updateViewByState(false);

        return changed;
    }

    @Override
    public void toggleChecked(boolean anim, boolean notifyCallback)
    {
        setChecked(!mIsChecked, anim, notifyCallback);
    }

    @Override
    public void setOnCheckedChangeCallback(OnCheckedChangeCallback onCheckedChangeCallback)
    {
        mOnCheckedChangeCallback = onCheckedChangeCallback;
    }

    @Override
    public void setOnViewPositionChangeCallback(OnViewPositionChangeCallback onViewPositionChangeCallback)
    {
        mOnViewPositionChangeCallback = onViewPositionChangeCallback;
    }

    @Override
    public float getScrollPercent()
    {
        return getScrollDistance() / (float) getAvailableWidth();
    }

    @Override
    public View getViewNormal()
    {
        return mViewNormal;
    }

    @Override
    public View getViewChecked()
    {
        return mViewChecked;
    }

    @Override
    public View getViewThumb()
    {
        return mViewThumb;
    }

    //----------SwitchButton implements end----------
}