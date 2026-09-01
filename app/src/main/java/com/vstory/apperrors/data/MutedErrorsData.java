
package com.vstory.apperrors.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.vstory.apperrors.bean.MutedErrorsAppBean;
import com.vstory.apperrors.utils.factory.FunctionFactoryKt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;


public class MutedErrorsData {

    
    public static final String PREFS_GROUP = "app_errors_mute";

    
    private static final String LOCAL_PREFS_NAME = "com.vstory.apperrors_mute";

    
    private static final String KEY_UNTIL_UNLOCK = "until_unlock";
    
    private static final String KEY_UNTIL_RESTART = "until_restart";

    
    private static SharedPreferences prefs;

    
    private static Set<String> mutedErrorsIfUnlockApps = new HashSet<>();
    private static Set<String> mutedErrorsIfRestartApps = new HashSet<>();

    private static void log(String msg) {
        
        
        android.util.Log.i("AppErrorNotify", msg != null ? msg : "");
    }

    
    public static void init(SharedPreferences prefs) {
        MutedErrorsData.prefs = null;   
        mutedErrorsIfUnlockApps = new HashSet<>();
        mutedErrorsIfRestartApps = new HashSet<>();
    }

    
    public static void init(Context context) {
        prefs = context.getSharedPreferences(LOCAL_PREFS_NAME, Context.MODE_PRIVATE);
        mutedErrorsIfUnlockApps = new HashSet<>(prefs.getStringSet(KEY_UNTIL_UNLOCK, new HashSet<String>()));
        mutedErrorsIfRestartApps = new HashSet<>(prefs.getStringSet(KEY_UNTIL_RESTART, new HashSet<String>()));
    }

    
    public static void initService(io.github.libxposed.service.XposedService service) {
        prefs = service.getRemotePreferences(PREFS_GROUP);
        mutedErrorsIfUnlockApps = new HashSet<>(prefs.getStringSet(KEY_UNTIL_UNLOCK, new HashSet<String>()));
        mutedErrorsIfRestartApps = new HashSet<>(prefs.getStringSet(KEY_UNTIL_RESTART, new HashSet<String>()));
    }

    private static void persistUnlock() {
        if (prefs != null) {
            try { prefs.edit().putStringSet(KEY_UNTIL_UNLOCK, mutedErrorsIfUnlockApps).apply(); }
            catch (Throwable ignored) {  }
        }
    }
    private static void persistRestart() {
        if (prefs != null) {
            try { prefs.edit().putStringSet(KEY_UNTIL_RESTART, mutedErrorsIfRestartApps).apply(); }
            catch (Throwable ignored) {  }
        }
    }

    
    public static void mutedErrorsIfUnlock(String packageName) {
        mutedErrorsIfUnlockApps.add(packageName);
        persistUnlock();
        log("Muted \"" + packageName + "\" until unlocks");
    }

    
    public static void mutedErrorsIfRestart(String packageName) {
        mutedErrorsIfRestartApps.add(packageName);
        persistRestart();
        log("Muted \"" + packageName + "\" until restarts");
    }

    
    public static void unmuteErrorsApp(MutedErrorsAppBean bean) {
        switch (bean.type) {
            case UNTIL_UNLOCKS:
                mutedErrorsIfUnlockApps.remove(bean.packageName);
                persistUnlock();
                log("Unmuted if unlocks errors app \"" + bean.packageName + "\"");
                break;
            case UNTIL_REBOOTS:
                mutedErrorsIfRestartApps.remove(bean.packageName);
                persistRestart();
                log("Unmuted if restarts errors app \"" + bean.packageName + "\"");
                break;
        }
    }

    
    public static void unmuteAllErrorsApps() {
        mutedErrorsIfUnlockApps.clear();
        mutedErrorsIfRestartApps.clear();
        persistUnlock();
        persistRestart();
        log("Unmute all errors apps --unlocks 0 --restarts 0");
    }

    
    public static ArrayList<MutedErrorsAppBean> fetchMutedErrorsAppsData() {
        ArrayList<MutedErrorsAppBean> list = new ArrayList<>();
        if (!mutedErrorsIfUnlockApps.isEmpty())
            for (String pkg : mutedErrorsIfUnlockApps)
                list.add(new MutedErrorsAppBean(MutedErrorsAppBean.MuteType.UNTIL_UNLOCKS, pkg));
        if (!mutedErrorsIfRestartApps.isEmpty())
            for (String pkg : mutedErrorsIfRestartApps)
                list.add(new MutedErrorsAppBean(MutedErrorsAppBean.MuteType.UNTIL_REBOOTS, pkg));
        return list;
    }

    
    public static void clearIfUnlock() {
        mutedErrorsIfUnlockApps.clear();
        persistUnlock();
    }

    
    public static Set<String> getMutedErrorsIfUnlockApps() { return mutedErrorsIfUnlockApps; }
    public static Set<String> getMutedErrorsIfRestartApps() { return mutedErrorsIfRestartApps; }

    

    
    public static final String ACTION_GET_MUTED = "com.vstory.apperrors.action.GET_MUTED";
    public static final String ACTION_MUTED_RESULT = "com.vstory.apperrors.action.MUTED_RESULT";
    public static final String ACTION_MUTE_ERROR = "com.vstory.apperrors.action.MUTE_ERROR";
    public static final String ACTION_UNMUTE_ERROR = "com.vstory.apperrors.action.UNMUTE_ERROR";
    public static final String ACTION_UNMUTE_ALL = "com.vstory.apperrors.action.UNMUTE_ALL";
    public static final String EXTRA_MUTED = "muted";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_BEAN = "bean";

    
    public static void requestMute(Context context, String packageName) {
        try {
            context.sendBroadcast(new Intent(ACTION_MUTE_ERROR).putExtra(EXTRA_PACKAGE, packageName));
        } catch (Throwable ignored) {
        }
    }

    
    public static void requestUnmute(Context context, MutedErrorsAppBean bean) {
        try {
            context.sendBroadcast(new Intent(ACTION_UNMUTE_ERROR).putExtra(EXTRA_BEAN, bean));
        } catch (Throwable ignored) {
        }
    }

    
    public static void requestUnmuteAll(Context context) {
        try {
            context.sendBroadcast(new Intent(ACTION_UNMUTE_ALL));
        } catch (Throwable ignored) {
        }
    }

    
    public static void fetchFromSystemServer(final Context context, final Runnable callback) {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_MUTED_RESULT);
            final BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    try {
                        ctx.unregisterReceiver(this);
                    } catch (Throwable ignored) {
                    }
                    Object extra = intent != null ? FunctionFactoryKt.getSerializableExtraCompat(intent, EXTRA_MUTED) : null;
                    
                    
                    if (extra instanceof java.util.List) {
                        java.util.List<?> raw = (java.util.List<?>) extra;
                        mutedErrorsIfUnlockApps = new HashSet<>();
                        mutedErrorsIfRestartApps = new HashSet<>();
                        for (Object o : raw) {
                            if (o instanceof MutedErrorsAppBean) {
                                MutedErrorsAppBean bean = (MutedErrorsAppBean) o;
                                if (bean.type == MutedErrorsAppBean.MuteType.UNTIL_UNLOCKS)
                                    mutedErrorsIfUnlockApps.add(bean.packageName);
                                else
                                    mutedErrorsIfRestartApps.add(bean.packageName);
                            }
                        }
                    }
                    if (callback != null) callback.run();
                }
            };
            if (Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else
                context.registerReceiver(receiver, filter);
            context.sendBroadcast(new Intent(ACTION_GET_MUTED));
        } catch (Throwable t) {
            if (callback != null) callback.run();
        }
    }

    private MutedErrorsData() {}
}
