
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


public abstract class BaseActivity<VB extends ViewBinding> extends AppCompatActivity {

    
    protected VB binding;

    
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
        
        
        
        setContentView(binding.getRoot());
        
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        
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
        
        onCreate();
    }

    
    protected abstract void onCreate();

    
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

    
    public void toastAndFinish(String name) {
        FunctionFactoryKt.toast(this, "Invalid " + name + ", exit");
        finish();
    }
}
