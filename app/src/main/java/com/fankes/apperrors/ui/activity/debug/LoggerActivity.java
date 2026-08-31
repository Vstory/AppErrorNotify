/*
 * AppErrorsTracking - 日志查看 Activity (Java 化)
 */
package com.fankes.apperrors.ui.activity.debug;

import android.app.Activity;
import android.content.Intent;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;

import androidx.core.view.ViewKt;

import com.fankes.apperrors.R;
import com.fankes.apperrors.databinding.ActivitiyLoggerBinding;
import com.fankes.apperrors.databinding.AdapterLoggerBinding;
import com.fankes.apperrors.databinding.DiaLoggerFilterBinding;
import com.fankes.apperrors.locale.LocaleFactoryKt;
import com.fankes.apperrors.ui.activity.base.BaseActivity;
import com.fankes.apperrors.utils.factory.BaseAdapterFactoryKt;
import com.fankes.apperrors.utils.factory.DialogBuilderFactoryKt;
import com.fankes.apperrors.utils.factory.FunctionFactoryKt;
import com.fankes.apperrors.utils.tool.ModuleLogger;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** 日志查看 Activity */
public class LoggerActivity extends BaseActivity<ActivitiyLoggerBinding> {

    /** 请求保存文件回调标识 */
    private static final int WRITE_REQUEST_CODE = 0;

    /** 回调适配器改变 */
    private Runnable onChanged;

    /** 过滤条件 */
    private final List<String> filters = new ArrayList<String>() {{
        add("D"); add("I"); add("W"); add("E");
    }};

    /** 全部的调试日志数据 */
    private final List<ModuleLogger.LogData> listData = new ArrayList<>();

    @Override
    protected void onCreate() {
        binding.titleBackIcon.setOnClickListener(v -> finish());
        binding.refreshIcon.setOnClickListener(v -> refreshData());
        binding.filterIcon.setOnClickListener(v -> {
            DialogBuilderFactoryKt.showDialog_Generics(this, DiaLoggerFilterBinding.class, false, builder -> {
                builder.setTitle(LocaleFactoryKt.getLocale().getFilterByCondition());
                DiaLoggerFilterBinding filterBinding = builder.getBinding();
                filterBinding.configCheck0.setChecked(contains("D"));
                filterBinding.configCheck1.setChecked(contains("I"));
                filterBinding.configCheck2.setChecked(contains("W"));
                filterBinding.configCheck3.setChecked(contains("E"));
                builder.confirmButton(() -> {
                    filters.clear();
                    if (filterBinding.configCheck0.isChecked()) filters.add("D");
                    if (filterBinding.configCheck1.isChecked()) filters.add("I");
                    if (filterBinding.configCheck2.isChecked()) filters.add("W");
                    if (filterBinding.configCheck3.isChecked()) filters.add("E");
                    refreshData();
                });
                builder.cancelButton();
            });
        });
        binding.exportAllIcon.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_TITLE, "app_errors_tracking_" + FunctionFactoryKt.toUtcTime(System.currentTimeMillis()) + ".log");
                startActivityForResult(intent, WRITE_REQUEST_CODE);
            } catch (Exception e) {
                FunctionFactoryKt.toast(this, "Start Android SAF failed");
            }
        });
        /** 设置列表元素和 Adapter */
        BaseAdapterFactoryKt.bindAdapter(binding.listView, creater -> {
            creater.onBindDatas(() -> listData);
            creater.onBindViews(AdapterLoggerBinding.class, (b, position) -> {
                ModuleLogger.LogData bean = listData.get(position);
                b.priorityText.setText(bean.getPriority());
                int bgRes;
                switch (bean.getPriority()) {
                    case "I": bgRes = R.drawable.bg_logger_i_round; break;
                    case "W": bgRes = R.drawable.bg_logger_w_round; break;
                    case "E": bgRes = R.drawable.bg_logger_e_round; break;
                    default: bgRes = R.drawable.bg_logger_d_round; break;
                }
                b.priorityText.setBackgroundResource(bgRes);
                b.messageText.setText(formatMsg(bean.getMsg()));
                b.timeText.setText(formatTime(bean.getTimestamp()));
                ViewKt.setVisible(b.throwableText, bean.getThrowable() != null);
                b.throwableText.setText(bean.getThrowable() != null ? bean.getThrowable() : "");
            });
        });
        onChanged = () -> ((android.widget.BaseAdapter) binding.listView.getAdapter()).notifyDataSetChanged();
        registerForContextMenu(binding.listView);
    }

    private boolean contains(String p) {
        for (String s : filters) if (s.equals(p)) return true;
        return false;
    }

    /** 更新列表数据 */
    private void refreshData() {
        listData.clear();
        List<ModuleLogger.LogData> all = ModuleLogger.allData();
        for (int i = all.size() - 1; i >= 0; i--) {
            ModuleLogger.LogData e = all.get(i);
            if (filters.contains(e.getPriority())) listData.add(e);
        }
        if (onChanged != null) onChanged.run();
        binding.listView.post(() -> binding.listView.setSelection(0));
        ViewKt.setVisible(binding.exportAllIcon, !listData.isEmpty());
        ViewKt.setVisible(binding.listView, !listData.isEmpty());
        ViewKt.setVisible(binding.listNoDataView, listData.isEmpty());
        binding.listNoDataView.setText(filters.size() < 4 ? LocaleFactoryKt.getLocale().getNoListResult() : LocaleFactoryKt.getLocale().getNoListData());
    }

    /** 格式化为本地时间格式 */
    private String formatTime(long timestamp) {
        return SimpleDateFormat.getDateTimeInstance().format(new Date(timestamp));
    }

    /** 格式化消息字符串样式 */
    private String formatMsg(String msg) {
        return msg != null ? msg.replace("--", "\n--") : "";
    }

    /** 获取完整的异常堆栈内容 */
    private String toStackTrace(Throwable t) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        t.printStackTrace(new PrintStream(baos));
        return baos.toString().trim();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        getMenuInflater().inflate(R.menu.menu_logger_action, menu);
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getMenuInfo() instanceof AdapterView.AdapterContextMenuInfo) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
            if (item.getItemId() == R.id.logger_copy) {
                ModuleLogger.LogData e = listData.get(info.position);
                String text = e.toString() + (e.getThrowable() != null ? "\n" + e.getThrowable() : "");
                FunctionFactoryKt.copyToClipboard(this, text);
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
                    java.io.OutputStream os = getContentResolver().openOutputStream(data.getData());
                    if (os != null) {
                        os.write(ModuleLogger.contents(listData).getBytes());
                        os.close();
                    }
                    FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getExportAllLogsSuccess());
                } else {
                    FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getExportAllLogsFail());
                }
            } catch (Exception e) {
                FunctionFactoryKt.toast(this, LocaleFactoryKt.getLocale().getExportAllLogsFail());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }
}
