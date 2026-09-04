
package io.github.vstory.apperrors.ui.activity.debug;

import io.github.vstory.apperrors.data.AppErrorsConfigData;
import io.github.vstory.apperrors.data.ConfigData;
import io.github.vstory.apperrors.databinding.ActivityDebugBinding;
import io.github.vstory.apperrors.ui.activity.base.BaseActivity;


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
