
package io.github.vstory.apperrors.ui.activity.errors;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import androidx.core.view.ViewKt;

import io.github.vstory.apperrors.R;
import io.github.vstory.apperrors.bean.AppErrorsInfoBean;
import io.github.vstory.apperrors.data.AppErrorsRecordData;
import io.github.vstory.apperrors.databinding.ActivityAppErrorsRecordBinding;
import io.github.vstory.apperrors.databinding.AdapterAppErrorsRecordBinding;
import io.github.vstory.apperrors.databinding.DiaAppErrorsStatisticsBinding;
import io.github.vstory.apperrors.locale.LocaleFactoryKt;
import io.github.vstory.apperrors.ui.activity.base.BaseActivity;
import io.github.vstory.apperrors.utils.factory.BaseAdapterFactoryKt;
import io.github.vstory.apperrors.utils.factory.DialogBuilder;
import io.github.vstory.apperrors.utils.factory.DialogBuilderFactoryKt;
import io.github.vstory.apperrors.utils.factory.FunctionFactoryKt;
import io.github.vstory.apperrors.utils.factory.ThreadPoolFactoryKt;
import io.github.vstory.apperrors.utils.tool.StackTraceShareHelper;
import io.github.vstory.apperrors.utils.tool.ZipFileTool;
import io.github.vstory.apperrors.wrapper.BuildConfigWrapper;

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

    
    private final List<Object> displayItems = new ArrayList<>();

    
    private final Map<String, Boolean> expandedGroups = new HashMap<>();

    
    private String filterPackage = "";

    
    private static class CrashGroupItem {
        final String signature;
        final AppErrorsInfoBean latest;      
        final List<AppErrorsInfoBean> members; 
        boolean expanded;

        CrashGroupItem(String signature, AppErrorsInfoBean latest, List<AppErrorsInfoBean> members, boolean expanded) {
            this.signature = signature;
            this.latest = latest;
            this.members = members;
            this.expanded = expanded;
        }
    }

    @Override
    protected void onCreate() {
        binding.titleBackIcon.setOnClickListener(v -> onBackPressed());
        binding.appErrorSisIcon.setOnClickListener(v -> {
            DialogBuilder<?> dlg = new DialogBuilder<>(this);
            dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
            dlg.setProgressContent(LocaleFactoryKt.getLocale().getGeneratingStatistics());
            dlg.noCancelable();
            
            
            AppErrorsRecordData.fetchAppTotalFromSystemServer(this, total -> {
                ThreadPoolFactoryKt.newThread(() -> {
                    int totalApps = Math.max(total, 0);   
                    Map<String, Integer> countByPkg = new HashMap<>();
                    for (AppErrorsInfoBean bean : listData) {
                        countByPkg.put(bean.packageName, countByPkg.getOrDefault(bean.packageName, 0) + 1);
                    }
                    List<Map.Entry<String, Integer>> errorsApps = new ArrayList<>(countByPkg.entrySet());
                    errorsApps.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                    String mostAppPackageName = errorsApps.isEmpty() ? "" : errorsApps.get(0).getKey();
                    int mostAppCount = errorsApps.isEmpty() ? 0 : errorsApps.get(0).getValue();
                    Map<String, Integer> countByType = new HashMap<>();
                    for (AppErrorsInfoBean bean : listData) {
                        countByType.put(bean.exceptionClassName, countByType.getOrDefault(bean.exceptionClassName, 0) + 1);
                    }
                    List<Map.Entry<String, Integer>> typeList = new ArrayList<>(countByType.entrySet());
                    typeList.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                    String mostErrorsType = typeList.isEmpty() ? "" : simpleThwName(typeList.get(0).getKey());
                    int mostTypeCount = typeList.isEmpty() ? 0 : typeList.get(0).getValue();
                    float ppt = totalApps > 0 ? ((float) errorsApps.size() * 100f) / (float) totalApps : 0f;
                    String pptCount = FunctionFactoryKt.decimal(ppt, 2);
                    runOnUiThread(() -> {
                        dlg.cancel();
                        DialogBuilderFactoryKt.showDialog_Generics(this, DiaAppErrorsStatisticsBinding.class, false, builder -> {
                            builder.setTitle(LocaleFactoryKt.getLocale().getAppErrorsStatisticsReport());
                            DiaAppErrorsStatisticsBinding sb = builder.getBinding();
                            sb.tvCrashCount.setText(String.valueOf(listData.size()));
                            sb.tvTotalApps.setText(String.valueOf(totalApps));
                            sb.tvInvolvedApps.setText(String.valueOf(errorsApps.size()));
                            sb.tvCrashRatio.setText(pptCount + "%");
                            sb.imgTopAppIcon.setImageDrawable(FunctionFactoryKt.appIconOf(this, mostAppPackageName));
                            String appName = FunctionFactoryKt.appNameOf(this, mostAppPackageName);
                            sb.tvTopAppName.setText(appName.trim().isEmpty() ? mostAppPackageName : appName);
                            sb.tvTopAppCount.setText(LocaleFactoryKt.getLocale().getTimesUnit(mostAppCount));
                            sb.tvTopTypeName.setText(mostErrorsType);
                            sb.tvTopTypeCount.setText(LocaleFactoryKt.getLocale().getTimesUnit(mostTypeCount));
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
            creater.onBindDatas(() -> displayItems);
            creater.onBindViews(AdapterAppErrorsRecordBinding.class, (b, position) -> {
                Object item = displayItems.get(position);
                if (item instanceof CrashGroupItem) {
                    CrashGroupItem group = (CrashGroupItem) item;
                    AppErrorsInfoBean latest = group.latest;
                    b.appIcon.setImageDrawable(FunctionFactoryKt.appIconOf(this, latest.packageName));
                    String appName = FunctionFactoryKt.appNameOf(this, latest.packageName);
                    b.appNameText.setText(appName.trim().isEmpty() ? latest.packageName : appName);
                    ViewKt.setVisible(b.appUserIdText, latest.userId > 0);
                    b.appUserIdText.setText(LocaleFactoryKt.getLocale().userId(latest.userId));
                    b.errorsTimeText.setText(latest.getCrossTime());
                    
                    b.errorTypeIcon.setImageResource(group.expanded ? R.drawable.ic_expand_more : R.drawable.ic_chevron_right);
                    String groupType = latest.isNativeCrash ? "Native crash" : simpleThwName(latest.exceptionClassName);
                    b.errorTypeText.setText(groupType + " ×" + group.members.size());
                    b.errorMsgText.setText(LocaleFactoryKt.getLocale()
                            .getRecordGroupTipCollapsed(group.members.size()));
                    if (group.expanded)
                        b.errorMsgText.setText(LocaleFactoryKt.getLocale()
                                .getRecordGroupTipExpanded(group.members.size()));
                } else {
                    AppErrorsInfoBean bean = (AppErrorsInfoBean) item;
                    b.appIcon.setImageDrawable(FunctionFactoryKt.appIconOf(this, bean.packageName));
                    String appName = FunctionFactoryKt.appNameOf(this, bean.packageName);
                    b.appNameText.setText(appName.trim().isEmpty() ? bean.packageName : appName);
                    ViewKt.setVisible(b.appUserIdText, bean.userId > 0);
                    b.appUserIdText.setText(LocaleFactoryKt.getLocale().userId(bean.userId));
                    b.errorsTimeText.setText(bean.getCrossTime());
                    b.errorTypeIcon.setImageResource(bean.isNativeCrash ? R.drawable.ic_cpp : R.drawable.ic_java);
                    b.errorTypeText.setText(bean.isNativeCrash ? "Native crash" : simpleThwName(bean.exceptionClassName));
                    b.errorMsgText.setText(bean.exceptionMessage);
                }
            });
        });
        onChanged = () -> ((android.widget.BaseAdapter) binding.listView.getAdapter()).notifyDataSetChanged();
        registerForContextMenu(binding.listView);
        binding.listView.setOnItemClickListener((parent, view, position, id) -> {
            Object item = displayItems.get(position);
            if (item instanceof CrashGroupItem) toggleGroup((CrashGroupItem) item);
            else AppErrorsDetailActivity.Companion.start(this, (AppErrorsInfoBean) item);
        });
        binding.filterBarView.setOnClickListener(v -> {
            filterPackage = "";
            buildDisplay();
        });
    }

    
    private void refreshData() {
        AppErrorsRecordData.fetchFromSystemServer(this, new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context ctx, Intent intent) {
                final List<AppErrorsInfoBean> all = AppErrorsRecordData.allData;
                
                
                final boolean timedOut = intent != null
                        && "io.github.vstory.apperrors.action.ERRORS_TIMEOUT".equals(intent.getAction());
                runOnUiThread(() -> {
                    ViewKt.setVisible(binding.listProgressView, false);
                    listData.clear();
                    listData.addAll(all);
                    
                    ViewKt.setVisible(binding.appErrorSisIcon, listData.size() >= 5);
                    ViewKt.setVisible(binding.clearAllIcon, !listData.isEmpty());
                    ViewKt.setVisible(binding.exportAllIcon, !listData.isEmpty());
                    
                    buildDisplay();
                    if (timedOut) {
                        FunctionFactoryKt.toast(AppErrorsRecordActivity.this,
                                getString(R.string.system_service_not_ready_tip));
                    }
                });
            }
        });
    }

    
    private void buildDisplay() {
        displayItems.clear();
        
        java.util.List<AppErrorsInfoBean> visible = new ArrayList<>();
        for (AppErrorsInfoBean b : listData) {
            if (filterPackage.isEmpty() || filterPackage.equals(b.packageName)) visible.add(b);
        }
        
        java.util.Set<String> consumed = new java.util.HashSet<>();
        for (int i = 0; i < visible.size(); i++) {
            AppErrorsInfoBean b = visible.get(i);
            String sig = crashSignature(b);
            if (sig == null || consumed.contains(sig)) {
                
                if (sig == null) displayItems.add(b);
                continue;
            }
            java.util.List<AppErrorsInfoBean> members = new ArrayList<>();
            for (int j = i; j < visible.size(); j++) {
                AppErrorsInfoBean c = visible.get(j);
                if (sig.equals(crashSignature(c))) members.add(c);
            }
            consumed.add(sig);
            if (members.size() == 1) { displayItems.add(b); continue; } 
            boolean expanded = Boolean.TRUE.equals(expandedGroups.get(sig));
            displayItems.add(new CrashGroupItem(sig, members.get(0), members, expanded));
            if (expanded) displayItems.addAll(members); 
        }
        
        int visibleCount = 0;
        for (Object o : displayItems) visibleCount += (o instanceof CrashGroupItem)
                ? ((CrashGroupItem) o).members.size() : 1;
        binding.titleCountText.setText(LocaleFactoryKt.getLocale().recordCount(visibleCount));
        ViewKt.setVisible(binding.listView, !displayItems.isEmpty());
        ViewKt.setVisible(binding.listNoDataView, displayItems.isEmpty());
        boolean filtering = !filterPackage.isEmpty();
        ViewKt.setVisible(binding.filterBarView, filtering);
        if (filtering) {
            String appName = FunctionFactoryKt.appNameOf(this, filterPackage);
            binding.filterBarView.setText(LocaleFactoryKt.getLocale().getRecordFilterBar(
                    appName.trim().isEmpty() ? filterPackage : appName));
        }
        if (onChanged != null) onChanged.run();
    }

    
    private void toggleGroup(CrashGroupItem group) {
        expandedGroups.put(group.signature, !group.expanded);
        buildDisplay();
    }

    
    private String crashSignature(AppErrorsInfoBean b) {
        if (b == null) return null;
        if (b.isNativeCrash) {
            String msg = b.exceptionMessage == null ? "" : b.exceptionMessage.trim();
            if (msg.isEmpty()) return null;
            String norm = msg.replaceAll("(?i)0x[0-9a-f]+", "0x?").replaceAll("\\s+", " ").trim();
            if (norm.length() > 120) norm = norm.substring(0, 120);
            return b.packageName + "|N|" + norm;
        }
        String cls = b.exceptionClassName == null ? "" : b.exceptionClassName.trim();
        if (cls.isEmpty()) return null;
        String file = b.throwFileName == null ? "" : b.throwFileName;
        String method = b.throwMethodName == null ? "" : b.throwMethodName;
        return b.packageName + "|J|" + cls + "|" + file + "|" + method;
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
            if (info.position < 0 || info.position >= displayItems.size()) return super.onContextItemSelected(item);
            Object obj = displayItems.get(info.position);
            AppErrorsInfoBean bean = obj instanceof CrashGroupItem ? ((CrashGroupItem) obj).latest : (AppErrorsInfoBean) obj;
            if (item.getItemId() == R.id.aerrors_view_detail) {
                
                if (obj instanceof CrashGroupItem) toggleGroup((CrashGroupItem) obj);
                else AppErrorsDetailActivity.Companion.start(this, bean);
            } else if (item.getItemId() == R.id.aerrors_app_info) {
                FunctionFactoryKt.openSelfSetting(this, bean.packageName);
            } else if (item.getItemId() == R.id.aerrors_filter_by_app) {
                filterPackage = bean.packageName;
                buildDisplay();
            } else if (item.getItemId() == R.id.aerrors_remove_record) {
                if (obj instanceof CrashGroupItem) {
                    
                    FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getRecordGroupDeleteHint());
                } else {
                    DialogBuilder<?> dlg = new DialogBuilder<>(this);
                    dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
                    dlg.setMsg(LocaleFactoryKt.getLocale().getAreYouSureRemoveRecord());
                    dlg.confirmButton(() -> {
                        AppErrorsRecordData.requestRemove(this, bean);
                        refreshData();
                    });
                    dlg.cancelButton();
                    dlg.show();
                }
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
