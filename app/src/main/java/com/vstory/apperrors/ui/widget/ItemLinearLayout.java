
package com.vstory.apperrors.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;

import com.vstory.apperrors.utils.factory.FunctionFactoryKt;

import top.defaults.drawabletoolbox.DrawableBuilder;


public class ItemLinearLayout extends LinearLayout {

    public ItemLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        setGravity(Gravity.CENTER | Gravity.START);
        setBackground(new DrawableBuilder()
                .rounded()
                .cornerRadius(FunctionFactoryKt.dp(15, context))
                .ripple()
                .rippleColor(0xFFAAAAAA)
                .build());
    }
}
