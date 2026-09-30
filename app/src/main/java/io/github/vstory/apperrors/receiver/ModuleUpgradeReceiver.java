package io.github.vstory.apperrors.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import io.github.vstory.apperrors.data.ConfigData;

/** 升级后提前播种配置镜像，避免升级后首次打开读不到旧值 */
public class ModuleUpgradeReceiver extends BroadcastReceiver {

    private static final String TAG = "AppErrorNotify";

    private static final long SEED_WAIT_MS = 5000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        if (ConfigData.isMirrorSeeded() || !ConfigData.shouldAwaitFirstBind()) return;
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                boolean bound = ConfigData.awaitFirstBind(SEED_WAIT_MS);
                if (!bound) ConfigData.noteBindWaitMiss();
                Log.i(TAG, "package replaced → seed mirror: bound=" + bound
                        + ", seeded=" + ConfigData.isMirrorSeeded());
            } catch (Throwable t) {
                Log.i(TAG, "package replaced → seed mirror failed: " + t);
            } finally {
                try {
                    pending.finish();
                } catch (Throwable ignored) {
                }
            }
        }, "apperrors-upgrade-seed").start();
    }
}
