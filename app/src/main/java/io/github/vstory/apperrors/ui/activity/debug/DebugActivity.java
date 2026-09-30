/*
 * AppErrorsTracking - 调试日志开关设置 Activity (Java 化)
 */
package io.github.vstory.apperrors.ui.activity.debug;

import io.github.vstory.apperrors.data.AppErrorsConfigData;
import io.github.vstory.apperrors.data.ConfigData;
import io.github.vstory.apperrors.databinding.ActivityDebugBinding;
import io.github.vstory.apperrors.ui.activity.base.BaseActivity;
import io.github.vstory.apperrors.utils.tool.ModuleServiceHolder;

/**
 * 调试日志开关页面。
 *  - 「调试日志」开关：system_server 是否输出通道回传等调试日志。
 *  - 默认关闭：崩溃时只能看到 1 条「崩溃记录」日志；开启后崩溃会额外输出 [DEBUG] 详情。
 *  - 实时生效：写入 RemotePreferences（跨进程），system_server 侧每次 getBoolean 直读最新值；
 *            change 后另发广播通知 system_server 立即同步（无需重启）。
 *  注意：监听器在 setChecked 初始化完成后才注册，避免代码 setChecked 误触发保存；不依赖 isPressed，
 *        保证触摸/无障碍/键盘切换等所有场景都能保存。
 */
public class DebugActivity extends BaseActivity<ActivityDebugBinding> {

    /** 重放开关状态期间抑制保存回调（避免代码 setChecked 触发一次多余的写+广播） */
    private boolean syncing = false;

    @Override
    protected void onCreate() {
        binding.titleBackIcon.setOnClickListener(v -> finish());
        // 1. 先初始化开关状态（在此期间不能注册监听器，否则 setChecked 会误触发保存回调）
        binding.enableDebugSwitch.setChecked(ConfigData.isEnableDebug());
        // 2. 再注册监听器：此后所有变化均由用户操作触发 → 保存 + 广播通知 system_server 立即生效
        binding.enableDebugSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (syncing) return;
            ConfigData.setEnableDebug(checked);
            // 广播 → system_server 立即刷新（实时生效，无需重启）
            try { AppErrorsConfigData.notifyConfigChanged(this); } catch (Throwable t) { /* ignore */ }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ModuleServiceHolder.addServiceStateListener(serviceStateListener, true);
    }

    @Override
    protected void onPause() {
        ModuleServiceHolder.removeServiceStateListener(serviceStateListener);
        super.onPause();
    }

    /**
     * XposedService 状态监听：绑定/断开后存储源变了（remote ↔ 本地镜像），按当前权威存储重放开关状态。
     * 冷启动时进入本页可能早于 service 绑定，只读一次会显示默认值。
     */
    private final ModuleServiceHolder.ServiceStateListener serviceStateListener =
            service -> runOnUiThread(this::syncDebugSwitch);

    /** 重放调试开关状态（值不变时不触碰控件，避免多余回调） */
    private void syncDebugSwitch() {
        boolean enabled = ConfigData.isEnableDebug();
        if (binding.enableDebugSwitch.isChecked() == enabled) return;
        syncing = true;
        try { binding.enableDebugSwitch.setChecked(enabled); } finally { syncing = false; }
    }
}
