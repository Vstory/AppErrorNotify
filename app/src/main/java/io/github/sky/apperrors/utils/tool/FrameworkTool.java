
package io.github.sky.apperrors.utils.tool;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import io.github.sky.apperrors.bean.AppFiltersBean;
import io.github.sky.apperrors.bean.AppInfoBean;
import io.github.sky.apperrors.bean.enums.AppFiltersType;
import io.github.sky.apperrors.locale.LocaleFactoryKt;
import io.github.sky.apperrors.utils.factory.DialogBuilder;
import io.github.sky.apperrors.utils.factory.FunctionFactoryKt;
import io.github.sky.apperrors.wrapper.BuildConfigWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class FrameworkTool {

    
    public static void restartSystem(Context context) {
        Runnable showWhenAccessRootFail = () -> {
            DialogBuilder<?> dlg = new DialogBuilder<>(context);
            dlg.setTitle(LocaleFactoryKt.getLocale().getAccessRootFail());
            dlg.setMsg(LocaleFactoryKt.getLocale().getAccessRootFailTip());
            dlg.confirmButton(LocaleFactoryKt.getLocale().getGotIt());
            dlg.show();
        };
        DialogBuilder<?> dlg = new DialogBuilder<>(context);
        dlg.setTitle(LocaleFactoryKt.getLocale().getNotice());
        dlg.setMsg(LocaleFactoryKt.getLocale().getAreYourSureRestartSystem());
        dlg.confirmButton(LocaleFactoryKt.getLocale().getConfirm(), () -> {
            if (FunctionFactoryKt.isRootAccess()) FunctionFactoryKt.execShell("reboot", true);
            else showWhenAccessRootFail.run();
        });
        dlg.neutralButton(LocaleFactoryKt.getLocale().getFastRestart(), () -> {
            DialogBuilder<?> dlg2 = new DialogBuilder<>(context);
            dlg2.setTitle(LocaleFactoryKt.getLocale().getWarning());
            dlg2.setMsg(LocaleFactoryKt.getLocale().getFastRestartProblem());
            dlg2.confirmButton(LocaleFactoryKt.getLocale().getConfirm(), () -> {
                if (FunctionFactoryKt.isRootAccess()) FunctionFactoryKt.execShell("killall zygote", true);
                else showWhenAccessRootFail.run();
            });
            dlg2.cancelButton();
            dlg2.show();
        });
        dlg.cancelButton();
        dlg.show();
    }

    
    public static void fetchAppListData(Context context, AppFiltersBean filters, java.util.function.Consumer<ArrayList<AppInfoBean>> result) {
        List<PackageInfo> packages = FunctionFactoryKt.listOfPackages(context);
        ArrayList<AppInfoBean> list = new ArrayList<>();
        List<PackageInfo> filtered = new ArrayList<>();
        for (PackageInfo info : packages) {
            String pkg = info.packageName;
            if (pkg != null && !pkg.equals("android") && !pkg.equals(BuildConfigWrapper.APPLICATION_ID))
                filtered.add(info);
        }
        if (!filtered.isEmpty()) {
            List<PackageInfo> nameFiltered = filtered;
            if (filters.name != null && !filters.name.trim().isEmpty()) {
                nameFiltered = new ArrayList<>();
                for (PackageInfo info : filtered) {
                    String appName = FunctionFactoryKt.appNameOf(context, info.packageName);
                    if (info.packageName.contains(filters.name) || appName.contains(filters.name))
                        nameFiltered.add(info);
                }
            }
            List<PackageInfo> resultList;
            switch (filters.type) {
                case USER:
                    resultList = new ArrayList<>();
                    for (PackageInfo info : nameFiltered) if (!isSystemApp(info)) resultList.add(info);
                    break;
                case SYSTEM:
                    resultList = new ArrayList<>();
                    for (PackageInfo info : nameFiltered) if (isSystemApp(info)) resultList.add(info);
                    break;
                case ALL:
                default:
                    resultList = new ArrayList<>(nameFiltered);
                    break;
            }
            resultList.sort((a, b) -> Long.compare(b.lastUpdateTime, a.lastUpdateTime));
            for (PackageInfo info : resultList) {
                AppInfoBean bean = new AppInfoBean();
                bean.name = FunctionFactoryKt.appNameOf(context, info.packageName);
                bean.packageName = info.packageName;
                list.add(bean);
            }
        } else {
            ModuleLogger.log("W", "AppErrorsTracking", "Fetched installed packages but got empty list", null);
        }
        result.accept(list);
    }

    private static boolean isSystemApp(PackageInfo info) {
        ApplicationInfo ai = info.applicationInfo;
        return ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    
    public static void checkingActivated(java.util.function.Consumer<Boolean> result) {
        result.accept(ModuleServiceHolder.isActive());
    }

    private FrameworkTool() {}
}
