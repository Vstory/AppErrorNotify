package io.github.vstory.apperrors.ui.activity.main;

import androidx.core.view.ViewKt;

import io.github.vstory.apperrors.bean.AppFiltersBean;
import io.github.vstory.apperrors.bean.AppInfoBean;
import io.github.vstory.apperrors.bean.enums.AppFiltersType;
import io.github.vstory.apperrors.data.AppErrorsConfigData;
import io.github.vstory.apperrors.data.enums.AppErrorsConfigType;
import io.github.vstory.apperrors.databinding.ActivityConfigBinding;
import io.github.vstory.apperrors.databinding.AdapterAppInfoBinding;
import io.github.vstory.apperrors.databinding.DiaAppConfigBinding;
import io.github.vstory.apperrors.databinding.DiaAppsFilterBinding;
import io.github.vstory.apperrors.locale.LocaleFactoryKt;
import io.github.vstory.apperrors.ui.activity.base.BaseActivity;
import io.github.vstory.apperrors.utils.factory.BaseAdapterFactoryKt;
import io.github.vstory.apperrors.utils.factory.DialogBuilder;
import io.github.vstory.apperrors.utils.factory.DialogBuilderFactoryKt;
import io.github.vstory.apperrors.utils.factory.FunctionFactoryKt;
import io.github.vstory.apperrors.utils.factory.ThreadPoolFactoryKt;
import io.github.vstory.apperrors.utils.tool.FrameworkTool;
import io.github.vstory.apperrors.wrapper.BuildConfigWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConfigureActivity extends BaseActivity<ActivityConfigBinding> {

    private AppFiltersBean appFilters = new AppFiltersBean();

    private boolean listBlockedShown = false;

    private Runnable onChanged;

    private final List<AppInfoBean> listData = new ArrayList<>();

    @Override
    protected void onCreate() {
        AppErrorsConfigData.refresh();
        binding.titleBackIcon.setOnClickListener(v -> finish());
        binding.listPermissionButton.setOnClickListener(v ->
                FunctionFactoryKt.openSelfSetting(this, BuildConfigWrapper.APPLICATION_ID));
        binding.globalIcon.setOnClickListener(v -> {
            showAppConfigDialog(LocaleFactoryKt.getLocale().getGlobalConfig(), "", false, false, type -> {
                AppErrorsConfigData.putAppShowingType(type, "");
                AppErrorsConfigData.notifyConfigChanged(this);
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
                    AppErrorsConfigData.notifyConfigChanged(this);
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
                    typeText = LocaleFactoryKt.getLocale().getFollowGlobalConfig();
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
                AppErrorsConfigData.notifyConfigChanged(this);
                if (onChanged != null) onChanged.run();
            });
        });
        if (!MainActivity.isModuleValied) {
            DialogBuilder<?> dlg = new DialogBuilder<>(this);
            dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
            dlg.setMsg(LocaleFactoryKt.getLocale().getModuleNotFullyActivatedTip());
            dlg.confirmButton(() -> FrameworkTool.restartSystem(this));
            dlg.cancelButton();
            dlg.noCancelable();
            dlg.show();
        }
        refreshData();
    }

    private void setAppFiltersType(DiaAppsFilterBinding fb) {
        if (fb.filtersRadioUser.isChecked()) appFilters.type = AppFiltersType.USER;
        else if (fb.filtersRadioSystem.isChecked()) appFilters.type = AppFiltersType.SYSTEM;
        else if (fb.filtersRadioAll.isChecked()) appFilters.type = AppFiltersType.ALL;
        else throw new IllegalStateException("Invalid app filters type");
    }

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

    @Override
    protected void onResume() {
        super.onResume();
        // 仅当上次是「权限被拒」时空列表时才重刷：用户去开启权限后回来，列表应自动出现
        if (listBlockedShown) refreshData();
    }

    private void refreshData() {
        ViewKt.setVisible(binding.listProgressView, true);
        ViewKt.setVisible(binding.globalIcon, false);
        ViewKt.setVisible(binding.batchIcon, false);
        ViewKt.setVisible(binding.filterIcon, false);
        ViewKt.setVisible(binding.listView, false);
        ViewKt.setVisible(binding.listNoDataView, false);
        ViewKt.setVisible(binding.listPermissionView, false);
        binding.titleCountText.setText(LocaleFactoryKt.getLocale().getLoading());
        FrameworkTool.fetchAppListData(this, appFilters, result -> {
            final boolean listBlocked = result == null;
            List<AppInfoBean> tempsData = new ArrayList<>();
            ThreadPoolFactoryKt.newThread(() -> {
                try {
                    if (result != null) {
                        for (AppInfoBean bean : result) {
                            tempsData.add(bean);
                            bean.icon = FunctionFactoryKt.appIconOf(this, bean.packageName);
                        }
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
                        ViewKt.setVisible(binding.filterIcon, !listBlocked);
                        ViewKt.setVisible(binding.listView, !listData.isEmpty());
                        final boolean listEmpty = listData.isEmpty();
                        ViewKt.setVisible(binding.listPermissionView, listEmpty && listBlocked);
                        ViewKt.setVisible(binding.listNoDataView, listEmpty && !listBlocked);
                        listBlockedShown = listBlocked;
                        if (listBlocked) {
                            binding.titleCountText.setText("");
                        } else {
                            binding.listNoDataView.setText(LocaleFactoryKt.getLocale().getNoListResult());
                            binding.titleCountText.setText(LocaleFactoryKt.getLocale().resultCount(listData.size()));
                        }
                    });
                }
            });
        });
    }
}
