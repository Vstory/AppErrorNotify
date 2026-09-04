
package io.github.vstory.apperrors.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.appcompat.widget.SwitchCompat;

import io.github.vstory.apperrors.utils.factory.FunctionFactoryKt;

import top.defaults.drawabletoolbox.DrawableBuilder;


public class MaterialSwitch extends SwitchCompat {

    
    private static final int COLOR_ON_LIGHT = 0xFF1E7A5C;
    
    private static final int COLOR_ON_DARK = 0xFF3DDC97;
    
    private static final int COLOR_OFF_LIGHT = 0xFFCCCCCC;
    
    private static final int COLOR_OFF_DARK = 0xFF7C7C7C;

    public MaterialSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        boolean dark = FunctionFactoryKt.isSystemInDarkMode(context);
        int onColor = dark ? COLOR_ON_DARK : COLOR_ON_LIGHT;
        int offColor = dark ? COLOR_OFF_DARK : COLOR_OFF_LIGHT;
        setTrackDrawable(new DrawableBuilder()
                .rectangle()
                .rounded()
                .solidColor(offColor)
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
        
        setTrackTintList(toColors(onColor, offColor, offColor));
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
