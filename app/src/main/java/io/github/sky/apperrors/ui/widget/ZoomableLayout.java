/*
 * AppErrorsTracking - 支持双指捏合缩放 + 平移的容器 (Java 化)
 *
 * 用途：异常详情页堆栈显示区，整块代码区域作为一个"画布"，
 *       可用双指捏合整体放大/缩小，并可平移拖动查看缩放后的内容。
 * 实现：Matrix + ScaleGestureDetector(缩放) + GestureDetector(平移/双击)。
 * 说明：双击可快速放大/缩小；单指或双指拖动可平移；缩放后会限制在合理范围。
 */
package io.github.sky.apperrors.ui.widget;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;

public class ZoomableLayout extends FrameLayout {

    /** 缩放最小倍数 */
    private static final float MIN_SCALE = 0.5f;
    /** 缩放最大倍数 */
    private static final float MAX_SCALE = 4.0f;
    /** 图片边界 padding (细节) */
    private static final float BOUNDS_PADDING = 0.5f;

    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();
    private PointF last = new PointF();
    private float[] m = new float[9];

    /** 当前缩放倍数 */
    private float scale = 1f;
    private float lastScale = 1f;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGestureDetector;

    private boolean isScaling = false;

    public ZoomableLayout(Context context) {
        this(context, null);
    }

    public ZoomableLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomableLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mScaleDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                isScaling = true;
                savedMatrix.set(matrix);
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scaleFactor = detector.getScaleFactor();
                // 限制缩放范围
                float newScale = scale * scaleFactor;
                if (newScale < MIN_SCALE) newScale = MIN_SCALE;
                if (newScale > MAX_SCALE) newScale = MAX_SCALE;
                scale = newScale;

                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();
                // 以焦点为中心缩放
                matrix.set(savedMatrix);
                matrix.postScale(scaleFactor, scaleFactor, focusX, focusY);
                applyMatrix();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                isScaling = false;
            }
        });

        mGestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // 缩放状态下允许平移；也允许单指平移
                matrix.postTranslate(-distanceX, -distanceY);
                applyMatrix();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // 双击：快速放大/缩小
                if (scale > 1.5f) {
                    resetZoom();
                } else {
                    scale = 2.5f;
                    savedMatrix.set(matrix);
                    matrix.set(Matrix.IDENTITY_MATRIX);
                    matrix.postScale(2.5f, 2.5f, e.getX(), e.getY());
                    applyMatrix();
                }
                return true;
            }
        });
    }

    private void applyMatrix() {
        setScaleX(scale);
        setScaleY(scale);
        // 平移
        matrix.getValues(m);
        float tx = m[Matrix.MTRANS_X];
        float ty = m[Matrix.MTRANS_Y];
        setTranslationX(tx);
        setTranslationY(ty);
    }

    /** 重置缩放到 1.0（恢复原始大小） */
    public void resetZoom() {
        scale = 1f;
        matrix.set(Matrix.IDENTITY_MATRIX);
        setScaleX(1f);
        setScaleY(1f);
        setTranslationX(0f);
        setTranslationY(0f);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean ret;
        // 先处理缩放手势
        mScaleDetector.onTouchEvent(event);

        // 双指两点按下的平移
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
                savedMatrix.set(matrix);
                last.set(event.getX(), event.getY());
                isScaling = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (isScaling && event.getPointerCount() >= 2) {
                    // 双指平移
                    float dx = event.getX() - last.x;
                    float dy = event.getY() - last.y;
                    matrix.postTranslate(dx, dy);
                    applyMatrix();
                    last.set(event.getX(), event.getY());
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                isScaling = false;
                break;
        }

        // 单指手势（平移/双击）交由 GestureDetector
        if (!isScaling) {
            ret = mGestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
        } else {
            ret = true;
        }
        return ret;
    }

    // dispatchTouchEvent override 已移除（缩放在 onTouchEvent 中处理，交给系统正常分发）
}
