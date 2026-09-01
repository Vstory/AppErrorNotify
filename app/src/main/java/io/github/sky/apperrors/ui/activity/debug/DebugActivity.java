
package io.github.sky.apperrors.ui.activity.debug;

import io.github.sky.apperrors.data.AppErrorsConfigData;
import io.github.sky.apperrors.data.ConfigData;
import io.github.sky.apperrors.databinding.ActivityDebugBinding;
import io.github.sky.apperrors.ui.activity.base.BaseActivity;


public class DebugActivity extends BaseActivity<ActivityDebugBinding> {

    @Override
    protected void onCreate() {
        binding.titleBackIcon.setOnClickListener(v -> finish());
        
        binding.enableDebugSwitch.setChecked(ConfigData.isEnableDebug());
        
        binding.enableDebugSwitch.setOnCheckedChangeListener((btn, checked) -> {
            ConfigData.setEnableDebug(checked);
            
            try { AppErrorsConfigData.notifyConfigChanged(this); } catch (Throwable t) {  }
        });
    }
}
