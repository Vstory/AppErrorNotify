/*
 * AppErrorsTracking - 应用配置模板 Activity (Java 化)
 */
package io.github.sky.apperrors.ui.activity.main;

import androidx.core.view.ViewKt;

import io.github.sky.apperrors.bean.AppFiltersBean;
import io.github.sky.apperrors.bean.AppInfoBean;
import io.github.sky.apperrors.bean.enums.AppFiltersType;
import io.github.sky.apperrors.data.AppErrorsConfigData;
import io.github.sky.apperrors.data.enums.AppErrorsConfigType;
import io.github.sky.apperrors.databinding.ActivityConfigBinding;
import io.github.sky.apperrors.databinding.AdapterAppInfoBinding;
import io.github.sky.apperrors.databinding.DiaAppConfigBinding;
import io.github.sky.apperrors.databinding.DiaAppsFilterBinding;
import io.github.sky.apperrors.locale.LocaleFactoryKt;
import io.github.sky.apperrors.ui.activity.base.BaseActivity;
import io.github.sky.apperrors.utils.factory.BaseAdapterFactoryKt;
import io.github.sky.apperrors.utils.factory.DialogBuilder;
import io.github.sky.apperrors.utils.factory.DialogBuilderFactoryKt;
import io.github.sky.apperrors.utils.factory.FunctionFactoryKt;
import io.github.sky.apperrors.utils.factory.ThreadPoolFactoryKt;
import io.github.sky.apperrors.utils.tool.FrameworkTool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 应用配置模板 Activity */
public class ConfigureActivity extends BaseActivity<ActivityConfigBinding> {

    /** 过滤条件 */
    private AppFiltersBean appFilters = new AppFiltersBean();

    /** 回调适配器改变 */
    private Runnable onChanged;

    /** 全部的 APP 信息 */
    private final List<AppInfoBean> listData = new ArrayList<>();

    @Override
    protected void onCreate() {
        // 打开应用配置模板界面时重新加载内存集合，确保列表显示与配置操作基于最新数据
        AppErrorsConfigData.refresh();
        binding.titleBackIcon.setOnClickListener(v -> finish());
        binding.globalIcon.setOnClickListener(v -> {
            showAppConfigDialog(LocaleFactoryKt.getLocale().getGlobalConfig(), "", false, false, type -> {
                AppErrorsConfigData.putAppShowingType(type, "");
                AppErrorsConfigData.notifyConfigChanged(this);   // 广播 → system_server 立即刷新
                if (onChanged != null) onChanged.run();
            });
        });
        binding.batchIcon.setOnClickListener(v -> {
            showAppConfigDialog(LocaleFactoryKt.getLocale().batchOperationsNumber(listData.size()), "", true, true, type -> {
                DialogBuilder<?> dlg = new DialogBuilder<>(this);
                dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
                dlg.setMsg(LocaleFactoryKt.getLocale().areYouSureApplySiteApps(listData.size()));
                dlg.confirmButton(() -> {
                    for (AppInfoBean bean : listData)
                        AppErrorsConfigData.putAppShowingType(type, bean.packageName);
                    AppErrorsConfigData.notifyConfigChanged(this);   // 广播 → system_server 立即刷新
                    if (onChanged != null) onChanged.run();
                });
                dlg.cancelButton();
                dlg.show();
            });
        });
        binding.filterIcon.setOnClickListener(v -> {
            DialogBuilderFactoryKt.showDialog_Generics(this, DiaAppsFilterBinding.class, false, builder -> {
                builder.setTitle(LocaleFactoryKt.getLocale().getFilterByCondition());
                DiaAppsFilterBinding fb = builder.getBinding();
                fb.filtersRadioUser.setChecked(appFilters.type == AppFiltersType.USER);
                fb.filtersRadioSystem.setChecked(appFilters.type == AppFiltersType.SYSTEM);
                fb.filtersRadioAll.setChecked(appFilters.type == AppFiltersType.ALL);
                fb.appFiltersEdit.requestFocus();
                fb.appFiltersEdit.invalidate();
                if (appFilters.name != null && !appFilters.name.trim().isEmpty()) {
                    fb.appFiltersEdit.setText(appFilters.name);
                    fb.appFiltersEdit.setSelection(appFilters.name.length());
                }
                builder.confirmButton(() -> {
                    setAppFiltersType(fb);
                    appFilters.name = fb.appFiltersEdit.getText().toString().trim();
                    refreshData();
                });
                builder.cancelButton();
                if (appFilters.name != null && !appFilters.name.trim().isEmpty())
                    builder.neutralButton(LocaleFactoryKt.getLocale().getClearFilters(), () -> {
                        setAppFiltersType(fb);
                        appFilters.name = "";
                        refreshData();
                    });
            });
        });
        /** 设置列表元素和 Adapter */
        BaseAdapterFactoryKt.bindAdapter(binding.listView, creater -> {
            creater.onBindDatas(() -> listData);
            creater.onBindViews(AdapterAppInfoBinding.class, (b, position) -> {
                AppInfoBean bean = listData.get(position);
                b.appIcon.setImageDrawable(bean.icon);
                b.appNameText.setText(bean.name);
                String typeText;
                if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.GLOBAL, bean.packageName))
                    typeText = LocaleFactoryKt.getLocale().getFollowGlobalConfig();
                else if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.DIALOG, bean.packageName))
                    typeText = LocaleFactoryKt.getLocale().getFollowGlobalConfig();   // 旧配置迁移后按跟随全局显示
                else if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.NOTIFY, bean.packageName))
                    typeText = LocaleFactoryKt.getLocale().getShowErrorsNotify();
                else if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.TOAST, bean.packageName))
                    typeText = LocaleFactoryKt.getLocale().getShowErrorsToast();
                else if (AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.NOTHING, bean.packageName))
                    typeText = LocaleFactoryKt.getLocale().getShowNothing();
                else typeText = "Unknown type";
                b.configTypeText.setText(typeText);
            });
        });
        onChanged = () -> ((android.widget.BaseAdapter) binding.listView.getAdapter()).notifyDataSetChanged();
        binding.listView.setOnItemClickListener((parent, view, position, id) -> {
            AppInfoBean bean = listData.get(position);
            showAppConfigDialog(bean.name, bean.packageName, false, true, type -> {
                AppErrorsConfigData.putAppShowingType(type, bean.packageName);
                AppErrorsConfigData.notifyConfigChanged(this);   // 广播 → system_server 立即刷新
                if (onChanged != null) onChanged.run();
            });
        });
        /** 模块未完全激活将显示警告 */
        if (!MainActivity.isModuleValied) {
            DialogBuilder<?> dlg = new DialogBuilder<>(this);
            dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
            dlg.setMsg(LocaleFactoryKt.getLocale().getModuleNotFullyActivatedTip());
            dlg.confirmButton(() -> FrameworkTool.restartSystem(this));
            dlg.cancelButton();
            dlg.noCancelable();
            dlg.show();
        }
        /** 开始刷新数据 */
        refreshData();
    }

    private void setAppFiltersType(DiaAppsFilterBinding fb) {
        if (fb.filtersRadioUser.isChecked()) appFilters.type = AppFiltersType.USER;
        else if (fb.filtersRadioSystem.isChecked()) appFilters.type = AppFiltersType.SYSTEM;
        else if (fb.filtersRadioAll.isChecked()) appFilters.type = AppFiltersType.ALL;
        else throw new IllegalStateException("Invalid app filters type");
    }

    /** 显示应用配置对话框 */
    private void showAppConfigDialog(String title, String packageName, boolean isNotSetDefaultValue,
                                     boolean isShowGlobalConfig, Consumer<AppErrorsConfigType> result) {
        DialogBuilderFactoryKt.showDialog_Generics(this, DiaAppConfigBinding.class, false, builder -> {
            builder.setTitle(title);
            DiaAppConfigBinding cb = builder.getBinding();
            ViewKt.setVisible(cb.configRadio0, isShowGlobalConfig);
            if (!isNotSetDefaultValue) {
                if (isShowGlobalConfig) cb.configRadio0.setChecked(AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.GLOBAL, packageName));
                cb.configRadio2.setChecked(AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.NOTIFY, packageName));
                cb.configRadio3.setChecked(AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.TOAST, packageName));
                cb.configRadio4.setChecked(AppErrorsConfigData.isAppShowingType(AppErrorsConfigType.NOTHING, packageName));
            }
            builder.confirmButton(() -> {
                AppErrorsConfigType type;
                if (cb.configRadio0.isChecked()) type = AppErrorsConfigType.GLOBAL;
                else if (cb.configRadio2.isChecked()) type = AppErrorsConfigType.NOTIFY;
                else if (cb.configRadio3.isChecked()) type = AppErrorsConfigType.TOAST;
                else if (cb.configRadio4.isChecked()) type = AppErrorsConfigType.NOTHING;
                else throw new IllegalStateException("Invalid config type");
                result.accept(type);
            });
            builder.cancelButton();
        });
    }

    /** 刷新列表数据 */
    private void refreshData() {
        ViewKt.setVisible(binding.listProgressView, true);
        ViewKt.setVisible(binding.globalIcon, false);
        ViewKt.setVisible(binding.batchIcon, false);
        ViewKt.setVisible(binding.filterIcon, false);
        ViewKt.setVisible(binding.listView, false);
        ViewKt.setVisible(binding.listNoDataView, false);
        binding.titleCountText.setText(LocaleFactoryKt.getLocale().getLoading());
        FrameworkTool.fetchAppListData(this, appFilters, result -> {
            List<AppInfoBean> tempsData = new ArrayList<>();
            ThreadPoolFactoryKt.newThread(() -> {
                try {
                    for (AppInfoBean bean : result) {
                        tempsData.add(bean);
                        bean.icon = FunctionFactoryKt.appIconOf(this, bean.packageName);
                    }
                } catch (Exception ignored) {
                }
                if (!isDestroyed()) {
                    runOnUiThread(() -> {
                        listData.clear();
                        listData.addAll(tempsData);
                        if (onChanged != null) onChanged.run();
                        binding.listView.post(() -> binding.listView.setSelection(0));
                        ViewKt.setVisible(binding.listProgressView, false);
                        ViewKt.setVisible(binding.globalIcon, true);
                        ViewKt.setVisible(binding.batchIcon, !listData.isEmpty());
                        ViewKt.setVisible(binding.filterIcon, true);
                        ViewKt.setVisible(binding.listView, !listData.isEmpty());
                        ViewKt.setVisible(binding.listNoDataView, listData.isEmpty());
                        binding.titleCountText.setText(LocaleFactoryKt.getLocale().resultCount(listData.size()));
                    });
                }
            });
        });
    }
}
