
package com.vstory.apperrors.ui.activity.debug;

import com.vstory.apperrors.data.AppErrorsConfigData;
import com.vstory.apperrors.data.ConfigData;
import com.vstory.apperrors.databinding.ActivityDebugBinding;
import com.vstory.apperrors.ui.activity.base.BaseActivity;


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
