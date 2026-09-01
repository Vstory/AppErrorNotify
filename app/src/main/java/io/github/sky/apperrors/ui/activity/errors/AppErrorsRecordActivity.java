
package io.github.sky.apperrors.ui.activity.errors;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import androidx.core.view.ViewKt;

import io.github.sky.apperrors.R;
import io.github.sky.apperrors.bean.AppErrorsInfoBean;
import io.github.sky.apperrors.bean.AppFiltersBean;
import io.github.sky.apperrors.bean.enums.AppFiltersType;
import io.github.sky.apperrors.data.AppErrorsRecordData;
import io.github.sky.apperrors.databinding.ActivityAppErrorsRecordBinding;
import io.github.sky.apperrors.databinding.AdapterAppErrorsRecordBinding;
import io.github.sky.apperrors.databinding.DiaAppErrorsStatisticsBinding;
import io.github.sky.apperrors.locale.LocaleFactoryKt;
import io.github.sky.apperrors.ui.activity.base.BaseActivity;
import io.github.sky.apperrors.utils.factory.BaseAdapterFactoryKt;
import io.github.sky.apperrors.utils.factory.DialogBuilder;
import io.github.sky.apperrors.utils.factory.DialogBuilderFactoryKt;
import io.github.sky.apperrors.utils.factory.FunctionFactoryKt;
import io.github.sky.apperrors.utils.factory.ThreadPoolFactoryKt;
import io.github.sky.apperrors.utils.tool.FrameworkTool;
import io.github.sky.apperrors.utils.tool.StackTraceShareHelper;
import io.github.sky.apperrors.utils.tool.ZipFileTool;
import io.github.sky.apperrors.wrapper.BuildConfigWrapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AppErrorsRecordActivity extends BaseActivity<ActivityAppErrorsRecordBinding> {

    
    private static final int WRITE_REQUEST_CODE = 0;

    
    public static final Companion Companion = new Companion();

    public static class Companion {
        public Intent intent() {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(BuildConfigWrapper.APPLICATION_ID, AppErrorsRecordActivity.class.getName()));
            return intent;
        }
    }

    
    private String outPutFilePath = "";

    
    private Runnable onChanged;

    
    private final List<AppErrorsInfoBean> listData = new ArrayList<>();

    @Override
    protected void onCreate() {
        binding.titleBackIcon.setOnClickListener(v -> onBackPressed());
        binding.appErrorSisIcon.setOnClickListener(v -> {
            DialogBuilder<?> dlg = new DialogBuilder<>(this);
            dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
            dlg.setProgressContent(LocaleFactoryKt.getLocale().getGeneratingStatistics());
            dlg.noCancelable();
            FrameworkTool.fetchAppListData(this, new AppFiltersBean("", AppFiltersType.ALL), apps -> {
                ThreadPoolFactoryKt.newThread(() -> {
                    int totalApps = apps.size();
                    Map<String, Integer> countByPkg = new HashMap<>();
                    for (AppErrorsInfoBean bean : listData) {
                        countByPkg.put(bean.packageName, countByPkg.getOrDefault(bean.packageName, 0) + 1);
                    }
                    List<Map.Entry<String, Integer>> errorsApps = new ArrayList<>(countByPkg.entrySet());
                    errorsApps.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                    String mostAppPackageName = errorsApps.isEmpty() ? "" : errorsApps.get(0).getKey();
                    Map<String, Integer> countByType = new HashMap<>();
                    for (AppErrorsInfoBean bean : listData) {
                        countByType.put(bean.exceptionClassName, countByType.getOrDefault(bean.exceptionClassName, 0) + 1);
                    }
                    List<Map.Entry<String, Integer>> typeList = new ArrayList<>(countByType.entrySet());
                    typeList.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                    String mostErrorsType = typeList.isEmpty() ? "" : simpleThwName(typeList.get(0).getKey());
                    float ppt = totalApps > 0 ? ((float) errorsApps.size() * 100f) / (float) totalApps : 0f;
                    String pptCount = FunctionFactoryKt.decimal(ppt, 2);
                    runOnUiThread(() -> {
                        dlg.cancel();
                        DialogBuilderFactoryKt.showDialog_Generics(this, DiaAppErrorsStatisticsBinding.class, false, builder -> {
                            builder.setTitle(LocaleFactoryKt.getLocale().getAppErrorsStatistics());
                            DiaAppErrorsStatisticsBinding sb = builder.getBinding();
                            sb.totalErrorsUnitText.setText(LocaleFactoryKt.getLocale().totalErrorsUnit(listData.size()));
                            sb.totalAppsUnitText.setText(LocaleFactoryKt.getLocale().totalAppsUnit(totalApps));
                            sb.mostErrorsAppIcon.setImageDrawable(FunctionFactoryKt.appIconOf(this, mostAppPackageName));
                            String appName = FunctionFactoryKt.appNameOf(this, mostAppPackageName);
                            sb.mostErrorsAppText.setText(appName.trim().isEmpty() ? mostAppPackageName : appName);
                            sb.mostErrorsTypeText.setText(mostErrorsType);
                            sb.totalPptOfErrorsText.setText(pptCount + "%");
                            builder.confirmButton(LocaleFactoryKt.getLocale().getGotIt());
                        });
                    });
                });
            });
            dlg.show();
        });
        binding.clearAllIcon.setOnClickListener(v -> {
            DialogBuilder<?> dlg = new DialogBuilder<>(this);
            dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
            dlg.setMsg(LocaleFactoryKt.getLocale().getAreYouSureClearErrors());
            dlg.confirmButton(() -> {
                AppErrorsRecordData.requestClearAll(this);
                refreshData();
                FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getAllErrorsClearSuccess());
            });
            dlg.cancelButton();
            dlg.show();
        });
        binding.exportAllIcon.setOnClickListener(v -> {
            DialogBuilder<?> dlg = new DialogBuilder<>(this);
            dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
            dlg.setMsg(LocaleFactoryKt.getLocale().getAreYouSureExportAllErrors());
            dlg.confirmButton(this::exportAll);
            dlg.cancelButton();
            dlg.show();
        });
        
        BaseAdapterFactoryKt.bindAdapter(binding.listView, creater -> {
            creater.onBindDatas(() -> listData);
            creater.onBindViews(AdapterAppErrorsRecordBinding.class, (b, position) -> {
                AppErrorsInfoBean bean = listData.get(position);
                b.appIcon.setImageDrawable(FunctionFactoryKt.appIconOf(this, bean.packageName));
                String appName = FunctionFactoryKt.appNameOf(this, bean.packageName);
                b.appNameText.setText(appName.trim().isEmpty() ? bean.packageName : appName);
                ViewKt.setVisible(b.appUserIdText, bean.userId > 0);
                b.appUserIdText.setText(LocaleFactoryKt.getLocale().userId(bean.userId));
                b.errorsTimeText.setText(bean.getCrossTime());
                b.errorTypeIcon.setImageResource(bean.isNativeCrash ? R.drawable.ic_cpp : R.drawable.ic_java);
                b.errorTypeText.setText(bean.isNativeCrash ? "Native crash" : simpleThwName(bean.exceptionClassName));
                b.errorMsgText.setText(bean.exceptionMessage);
            });
        });
        onChanged = () -> ((android.widget.BaseAdapter) binding.listView.getAdapter()).notifyDataSetChanged();
        registerForContextMenu(binding.listView);
        binding.listView.setOnItemClickListener((parent, view, position, id) ->
                AppErrorsDetailActivity.Companion.start(this, listData.get(position)));
    }

    
    private void refreshData() {
        AppErrorsRecordData.fetchFromSystemServer(this, new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context ctx, Intent intent) {
                final List<AppErrorsInfoBean> all = AppErrorsRecordData.allData;
                runOnUiThread(() -> {
                    binding.titleCountText.setText(LocaleFactoryKt.getLocale().recordCount(all.size()));
                    ViewKt.setVisible(binding.listProgressView, false);
                    ViewKt.setVisible(binding.appErrorSisIcon, all.size() >= 5);
                    ViewKt.setVisible(binding.clearAllIcon, !all.isEmpty());
                    ViewKt.setVisible(binding.exportAllIcon, !all.isEmpty());
                    ViewKt.setVisible(binding.listView, !all.isEmpty());
                    ViewKt.setVisible(binding.listNoDataView, all.isEmpty());
                    listData.clear();
                    listData.addAll(all);
                    if (onChanged != null) onChanged.run();
                });
            }
        });
    }

    
    private void exportAll() {
        clearAllExportTemp();
        StackTraceShareHelper.showChoose(this, LocaleFactoryKt.getLocale().getExportAll(), (sDeviceBrand, sDeviceModel, sDisplay, sPackageName) -> {
            String path = getCacheDir().getAbsolutePath() + "/temp";
            new File(path).mkdirs();
            for (int index = 0; index < listData.size(); index++) {
                AppErrorsInfoBean bean = listData.get(index);
                String packageName = sPackageName ? bean.packageName : "anonymous_" + index;
                File f = new File(path + "/" + packageName + "_" + bean.getFileNameTime() + ".log");
                try {
                    java.io.FileWriter writer = new java.io.FileWriter(f);
                    writer.write(bean.stackOutputFileContent(sDeviceBrand, sDeviceModel, sDisplay, sPackageName));
                    writer.close();
                } catch (Exception ignored) {
                }
            }
            outPutFilePath = getCacheDir().getAbsolutePath() + "/temp_" + System.currentTimeMillis() + ".zip";
            ZipFileTool.zipMultiFile(path, outPutFilePath, false);
            try {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_TITLE, "AppErrorNotify_" + FunctionFactoryKt.toFileNameTime(System.currentTimeMillis()) + ".zip");
                startActivityForResult(intent, WRITE_REQUEST_CODE);
            } catch (Exception e) {
                FunctionFactoryKt.toast(this, "Start Android SAF failed");
            }
        });
    }

    
    private void clearAllExportTemp() {
        File cache = getCacheDir();
        if (cache.exists()) {
            File[] files = cache.listFiles();
            if (files != null) for (File f : files) f.delete();
        }
    }

    
    private String simpleThwName(String text) {
        if (text != null && text.contains(".")) {
            String[] parts = text.split("\\.");
            return parts[parts.length - 1];
        }
        return text != null ? text : "";
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        getMenuInflater().inflate(R.menu.menu_list_detail_action, menu);
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getMenuInfo() instanceof AdapterView.AdapterContextMenuInfo) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            if (item.getItemId() == R.id.aerrors_view_detail) {
                AppErrorsDetailActivity.Companion.start(this, listData.get(info.position));
            } else if (item.getItemId() == R.id.aerrors_app_info) {
                FunctionFactoryKt.openSelfSetting(this, listData.get(info.position).packageName);
            } else if (item.getItemId() == R.id.aerrors_remove_record) {
                DialogBuilder<?> dlg = new DialogBuilder<>(this);
                dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
                dlg.setMsg(LocaleFactoryKt.getLocale().getAreYouSureRemoveRecord());
                dlg.confirmButton(() -> {
                    AppErrorsRecordData.requestRemove(this, listData.get(info.position));
                    refreshData();
                });
                dlg.cancelButton();
                dlg.show();
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WRITE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            try {
                if (data != null && data.getData() != null) {
                    OutputStream os = getContentResolver().openOutputStream(data.getData());
                    if (os != null) {
                        byte[] bytes = readBytes(new FileInputStream(outPutFilePath));
                        os.write(bytes);
                        os.close();
                    }
                    clearAllExportTemp();
                    FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getExportAllErrorsSuccess());
                } else {
                    FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getExportAllErrorsFail());
                }
            } catch (Exception e) {
                FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getExportAllErrorsFail());
            }
        }
    }

    private byte[] readBytes(FileInputStream fis) {
        try {
            byte[] buf = new byte[fis.available()];
            fis.read(buf);
            fis.close();
            return buf;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }
}
