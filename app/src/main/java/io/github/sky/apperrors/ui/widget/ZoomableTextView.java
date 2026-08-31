/*
 * AppErrorsTracking - 支持双指捏合缩放文字的 TextView (Java 化)
 *
 * 用途：异常详情页的堆栈显示区，可用双指捏合自由放大/缩小堆栈字体大小。
 * 实现：ScaleGestureDetector 监听双指捏合，动态调整 textSize。
 * 范围：以初始 textSize 为基准，允许 0.5x ~ 3x 之间缩放。
 * 说明：保留 textIsSelectable（长按可选中复制）。当检测到双指捏合时，
 *       会暂停/关闭文本选择，避免与选择手势冲突；单指事件正常透传给系统处理。
 */
package io.github.sky.apperrors.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatTextView;

public class ZoomableTextView extends AppCompatTextView {

    /** 缩放下限倍数 */
    private static final float MIN_SCALE = 0.5f;
    /** 缩放上限倍数 */
    private static final float MAX_SCALE = 3.0f;
    /** 基础字号(sp)，保存首次设置的 textSize 作为缩放基准 */
    private float baseTextSize = 12f;
    /** 当前的缩放倍数 */
    private float currentScale = 1.0f;

    private ScaleGestureDetector mScaleDetector;

    public ZoomableTextView(Context context) {
        this(context, null);
    }

    public ZoomableTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomableTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /** 记录下拉框或由布局指定的初始 textSize 作为缩放基准 */
    private void init() {
        // 读取当前 textSize (px), 转换为 sp 存储
        float sp = getTextSize() / getResources().getDisplayMetrics().scaledDensity;
        if (sp > 0) baseTextSize = sp;
        mScaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                // 开始缩放时，暂时关闭文本选择，避免与选择手势冲突
                setTextIsSelectable(false);
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                currentScale *= factor;
                // 限制缩放倍数范围
                if (currentScale < MIN_SCALE) currentScale = MIN_SCALE;
                if (currentScale > MAX_SCALE) currentScale = MAX_SCALE;
                applyScale();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                // 缩放结束，恢复文本可选
                setTextIsSelectable(true);
            }
        });
    }

    /** 根据当前缩放倍数设置 textSize */
    private void applyScale() {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, baseTextSize * currentScale);
        // 触发重排
        requestLayout();
        invalidate();
    }

    /** 重置缩放倍数到 1.0，恢复基础字号 */
    public void resetZoom() {
        currentScale = 1.0f;
        applyScale();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 先把事件交给缩放手势检测器
        mScaleDetector.onTouchEvent(event);
        // 双指捏合时消费事件，避免干扰文本选择；单指(ACTION_DOWN等)照常透传
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            return true;
        }
        // 若正在进行缩放(指针数>=2)，也消费掉，防止与选择冲突
        if (event.getPointerCount() >= 2) {
            return true;
        }
        return super.onTouchEvent(event);
    }
}
