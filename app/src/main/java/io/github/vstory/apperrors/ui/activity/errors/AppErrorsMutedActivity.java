package io.github.vstory.apperrors.ui.activity.errors;

import androidx.core.view.ViewKt;

import io.github.vstory.apperrors.bean.MutedErrorsAppBean;
import io.github.vstory.apperrors.data.MutedErrorsData;
import io.github.vstory.apperrors.databinding.ActivityAppErrorsMutedBinding;
import io.github.vstory.apperrors.databinding.AdapterAppErrorsMutedBinding;
import io.github.vstory.apperrors.locale.LocaleFactoryKt;
import io.github.vstory.apperrors.ui.activity.base.BaseActivity;
import io.github.vstory.apperrors.utils.factory.BaseAdapterFactoryKt;
import io.github.vstory.apperrors.utils.factory.DialogBuilder;
import io.github.vstory.apperrors.utils.factory.FunctionFactoryKt;

import java.util.ArrayList;
import java.util.List;

public class AppErrorsMutedActivity extends BaseActivity<ActivityAppErrorsMutedBinding> {

    private Runnable onChanged;

    private boolean fetching = false;
    private int fetchSeq = 0;

    /** 无回传时的兜底等待上限。MutedErrorsData.fetchFromSystemServer 只兜异常、没有超时：
     *  模块未激活时广播无人应答、回调永不触发，不加这层转圈不会停 */
    private static final long FETCH_TIMEOUT_MS = 1500L;

    /** 转圈显示延迟：回传远快于此，立即显示会闪一下 */
    private static final long PROGRESS_DELAY_MS = 200L;

    private boolean showProgress = false;

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
        final int seq = ++fetchSeq;
        fetching = true;
        showProgress = false;
        renderData();
        binding.listView.postDelayed(() -> {
            if (seq == fetchSeq) {
                fetching = false;
                showProgress = false;
                renderData();
            }
        }, FETCH_TIMEOUT_MS);
        binding.listView.postDelayed(() -> {
            if (seq == fetchSeq && fetching) {
                showProgress = true;
                renderData();
            }
        }, PROGRESS_DELAY_MS);
        MutedErrorsData.fetchFromSystemServer(this, () -> runOnUiThread(() -> {
            if (seq != fetchSeq) return;
            fetching = false;
            showProgress = false;
            List<MutedErrorsAppBean> all = MutedErrorsData.fetchMutedErrorsAppsData();
            listData.clear();
            listData.addAll(all);
            renderData();
        }));
    }

    private void renderData() {
        if (onChanged != null) onChanged.run();
        final boolean hasData = !listData.isEmpty();
        ViewKt.setVisible(binding.listProgressView, !hasData && fetching && showProgress);
        ViewKt.setVisible(binding.unmuteAllIcon, hasData);
        ViewKt.setVisible(binding.listView, hasData);
        ViewKt.setVisible(binding.listNoDataView, !hasData && !fetching);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }
}
