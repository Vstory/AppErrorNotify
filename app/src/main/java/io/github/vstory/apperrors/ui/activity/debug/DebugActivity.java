package io.github.vstory.apperrors.ui.activity.debug;

import io.github.vstory.apperrors.data.AppErrorsConfigData;
import io.github.vstory.apperrors.data.ConfigData;
import io.github.vstory.apperrors.databinding.ActivityDebugBinding;
import io.github.vstory.apperrors.ui.activity.base.BaseActivity;
import io.github.vstory.apperrors.utils.tool.ModuleServiceHolder;

public class DebugActivity extends BaseActivity<ActivityDebugBinding> {

    private boolean syncing = false;

    @Override
    protected void onCreate() {
        binding.titleBackIcon.setOnClickListener(v -> finish());
        binding.enableDebugSwitch.setChecked(ConfigData.isEnableDebug());
        binding.enableDebugSwitch.setOnCheckedChangeListener((btn, checked) -> {
            if (syncing) return;
            ConfigData.setEnableDebug(checked);
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

    private final ModuleServiceHolder.ServiceStateListener serviceStateListener =
            service -> runOnUiThread(this::syncDebugSwitch);

    private void syncDebugSwitch() {
        boolean enabled = ConfigData.isEnableDebug();
        if (binding.enableDebugSwitch.isChecked() == enabled) return;
        syncing = true;
        try { binding.enableDebugSwitch.setChecked(enabled); } finally { syncing = false; }
    }
}
