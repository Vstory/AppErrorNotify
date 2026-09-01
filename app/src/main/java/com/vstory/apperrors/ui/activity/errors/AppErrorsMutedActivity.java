
package com.vstory.apperrors.ui.activity.errors;

import androidx.core.view.ViewKt;

import com.vstory.apperrors.bean.MutedErrorsAppBean;
import com.vstory.apperrors.data.MutedErrorsData;
import com.vstory.apperrors.databinding.ActivityAppErrorsMutedBinding;
import com.vstory.apperrors.databinding.AdapterAppErrorsMutedBinding;
import com.vstory.apperrors.locale.LocaleFactoryKt;
import com.vstory.apperrors.ui.activity.base.BaseActivity;
import com.vstory.apperrors.utils.factory.BaseAdapterFactoryKt;
import com.vstory.apperrors.utils.factory.DialogBuilder;
import com.vstory.apperrors.utils.factory.FunctionFactoryKt;

import java.util.ArrayList;
import java.util.List;


public class AppErrorsMutedActivity extends BaseActivity<ActivityAppErrorsMutedBinding> {

    
    private Runnable onChanged;

    
    private final List<MutedErrorsAppBean> listData = new ArrayList<>();

    @Override
    protected void onCreate() {
        binding.titleBackIcon.setOnClickListener(v -> onBackPressed());
        binding.unmuteAllIcon.setOnClickListener(v -> {
            DialogBuilder<?> dlg = new DialogBuilder<>(this);
            dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
            dlg.setMsg(LocaleFactoryKt.getLocale().getAreYouSureUnmuteAll());
            dlg.confirmButton(() -> {
                MutedErrorsData.requestUnmuteAll(this);   
                refreshData();
            });
            dlg.cancelButton();
            dlg.show();
        });
        
        BaseAdapterFactoryKt.bindAdapter(binding.listView, creater -> {
            creater.onBindDatas(() -> listData);
            creater.onBindViews(AdapterAppErrorsMutedBinding.class, (b, position) -> {
                MutedErrorsAppBean bean = listData.get(position);
                b.appIcon.setImageDrawable(FunctionFactoryKt.appIconOf(this, bean.packageName));
                String appName = FunctionFactoryKt.appNameOf(this, bean.packageName);
                b.appNameText.setText(appName.trim().isEmpty() ? bean.packageName : appName);
                b.muteTypeText.setText(bean.type == MutedErrorsAppBean.MuteType.UNTIL_UNLOCKS
                        ? LocaleFactoryKt.getLocale().getMuteIfUnlock()
                        : LocaleFactoryKt.getLocale().getMuteIfRestart());
                b.unmuteButton.setOnClickListener(v -> {
                    MutedErrorsData.requestUnmute(this, bean);  
                    refreshData();
                });
            });
        });
        onChanged = () -> ((android.widget.BaseAdapter) binding.listView.getAdapter()).notifyDataSetChanged();
    }

    
    private void refreshData() {
        MutedErrorsData.fetchFromSystemServer(this, () -> {
            runOnUiThread(() -> {
                List<MutedErrorsAppBean> all = MutedErrorsData.fetchMutedErrorsAppsData();
                listData.clear();
                listData.addAll(all);
                if (onChanged != null) onChanged.run();
                ViewKt.setVisible(binding.unmuteAllIcon, !listData.isEmpty());
                ViewKt.setVisible(binding.listView, !listData.isEmpty());
                ViewKt.setVisible(binding.listNoDataView, listData.isEmpty());
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }
}
