/*
 * AppErrorsTracking - 已忽略异常列表 Activity (Java 化)
 */
package com.fankes.apperrors.ui.activity.errors;

import androidx.core.view.ViewKt;

import com.fankes.apperrors.bean.MutedErrorsAppBean;
import com.fankes.apperrors.data.MutedErrorsData;
import com.fankes.apperrors.databinding.ActivityAppErrorsMutedBinding;
import com.fankes.apperrors.databinding.AdapterAppErrorsMutedBinding;
import com.fankes.apperrors.locale.LocaleFactoryKt;
import com.fankes.apperrors.ui.activity.base.BaseActivity;
import com.fankes.apperrors.utils.factory.BaseAdapterFactoryKt;
import com.fankes.apperrors.utils.factory.DialogBuilder;
import com.fankes.apperrors.utils.factory.FunctionFactoryKt;

import java.util.ArrayList;
import java.util.List;

/** 已忽略异常列表 Activity */
public class AppErrorsMutedActivity extends BaseActivity<ActivityAppErrorsMutedBinding> {

    /** 回调适配器改变 */
    private Runnable onChanged;

    /** 全部的已忽略异常的 APP 信息 */
    private final List<MutedErrorsAppBean> listData = new ArrayList<>();

    @Override
    protected void onCreate() {
        binding.titleBackIcon.setOnClickListener(v -> onBackPressed());
        binding.unmuteAllIcon.setOnClickListener(v -> {
            DialogBuilder<?> dlg = new DialogBuilder<>(this);
            dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
            dlg.setMsg(LocaleFactoryKt.getLocale().getAreYouSureUnmuteAll());
            dlg.confirmButton(() -> {
                MutedErrorsData.requestUnmuteAll(this);   // 广播 → system_server 内存清空
                refreshData();
            });
            dlg.cancelButton();
            dlg.show();
        });
        /** 设置列表元素和 Adapter */
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
                    MutedErrorsData.requestUnmute(this, bean);  // 广播 → system_server 内存取消
                    refreshData();
                });
            });
        });
        onChanged = () -> ((android.widget.BaseAdapter) binding.listView.getAdapter()).notifyDataSetChanged();
    }

    /** 更新列表数据（经广播从 system_server 拉取权威忽略列表） */
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
