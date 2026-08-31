/*
 * AppErrorsTracking - Material 风格开关 (Java 化)
 */
package io.github.sky.apperrors.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.appcompat.widget.SwitchCompat;

import io.github.sky.apperrors.utils.factory.FunctionFactoryKt;

import top.defaults.drawabletoolbox.DrawableBuilder;

/** Material 风格开关 */
public class MaterialSwitch extends SwitchCompat {

    public MaterialSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        setTrackDrawable(new DrawableBuilder()
                .rectangle()
                .rounded()
                .solidColor(0xFF656565)
                .height(FunctionFactoryKt.dp(20, context))
                .cornerRadius(FunctionFactoryKt.dp(15, context))
                .build());
        setThumbDrawable(new DrawableBuilder()
                .rectangle()
                .rounded()
                .solidColor(Color.WHITE)
                .size(FunctionFactoryKt.dp(20, context), FunctionFactoryKt.dp(20, context))
                .cornerRadius(FunctionFactoryKt.dp(20, context))
                .strokeWidth(FunctionFactoryKt.dp(8, context))
                .strokeColor(Color.TRANSPARENT)
                .build());
        int thumbColor = FunctionFactoryKt.isSystemInDarkMode(context) ? 0xFF7C7C7C : 0xFFCCCCCC;
        setTrackTintList(toColors(0xFF656565, thumbColor, thumbColor));
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
    }

    private ColorStateList toColors(int selected, int pressed, int normal) {
        int[] colors = new int[]{selected, pressed, normal};
        int[][] states = new int[3][];
        states[0] = new int[]{android.R.attr.state_checked};
        states[1] = new int[]{android.R.attr.state_pressed};
        states[2] = new int[]{};
        return new ColorStateList(states, colors);
    }
}
