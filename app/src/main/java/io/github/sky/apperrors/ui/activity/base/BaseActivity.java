/*
 * AppErrorsTracking - Activity 基类 (Java 化)
 */
package io.github.sky.apperrors.ui.activity.base;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.WindowCompat;
import androidx.viewbinding.ViewBinding;

import io.github.sky.apperrors.R;
import io.github.sky.apperrors.utils.factory.FunctionFactoryKt;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/** Activity 基类（泛型 VB 反射装载绑定） */
public abstract class BaseActivity<VB extends ViewBinding> extends AppCompatActivity {

    /** 获取绑定布局对象 */
    protected VB binding;

    /** 应用强制语言(attachBaseContext 阶段) — 整个 Activity 的 Resources 用目标语言 */
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(io.github.sky.apperrors.utils.tool.LanguageData.wrap(newBase));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Class<?> bindingClass = findViewBindingClass(getClass());
        try {
            Method m = bindingClass != null ? bindingClass.getMethod("inflate", LayoutInflater.class) : null;
            binding = m != null ? (VB) m.invoke(null, getLayoutInflater()) : null;
        } catch (Exception e) {
            binding = null;
        }
        if (binding == null) throw new IllegalStateException("binding failed");
        // ⚠️ 不要给整布局 root 设 setFitsSystemWindows(true)：Android 15+ 强制 edge-to-edge 下，
        //     double 消费状态栏 inset 会导致顶栏留空/滚动重叠。状态栏 inset 只由布局内 AppBarLayout 的
        //    fitsSystemWindows 消费（官方标准做法）。之前的 root.setFitsSystemWindows(true) 已移除。
        setContentView(binding.getRoot());
        /** 隐藏系统的标题栏 */
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        /** 初始化沉浸状态栏 */
        androidx.core.view.WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(FunctionFactoryKt.isNotSystemInDarkMode(this));
        insetsController.setAppearanceLightNavigationBars(FunctionFactoryKt.isNotSystemInDarkMode(this));
        @SuppressWarnings("deprecation")
        int color = ResourcesCompat.getColor(getResources(), R.color.colorThemeBackground, null);
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            getWindow().setNavigationBarDividerColor(color);
        /** 装载子类 */
        onCreate();
    }

    /** 回调 onCreate 方法 */
    protected abstract void onCreate();

    /** 解析泛型父类 VB 的绑定类（标准反射） */
    private Class<?> findViewBindingClass(Class<?> clazz) {
        Class<?> c = clazz;
        while (c != null) {
            Type genericSuper = c.getGenericSuperclass();
            if (genericSuper instanceof ParameterizedType) {
                Type arg = ((ParameterizedType) genericSuper).getActualTypeArguments()[0];
                if (arg instanceof Class<?>) return (Class<?>) arg;
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * 在旧版的 Android 系统中使用了 activity-alias 标签从启动器启动 Activity 会造成其组件名称 (完整类名) 为代理名称
     * 为了获取真实的顶层 Activity 组件名称 (完整类名) - 如果名称不正确将自动执行一次结束并重新打开当前 Activity
     */
    @SuppressWarnings("deprecation")
    public void checkingTopComponentName() {
        String topComponentName = "";
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am != null && am.getRunningTasks(9999).size() > 0)
                topComponentName = am.getRunningTasks(9999).get(0).topActivity != null
                        ? am.getRunningTasks(9999).get(0).topActivity.getClassName() : "";
        } catch (Exception ignored) {
        }
        if (!topComponentName.trim().isEmpty() && !topComponentName.equals(getClass().getName())) {
            finish();
            startActivity(new Intent(this, getClass()));
        }
    }

    /** 弹出提示并退出 */
    public void toastAndFinish(String name) {
        FunctionFactoryKt.toast(this, "Invalid " + name + ", exit");
        finish();
    }
}
