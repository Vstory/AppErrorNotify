package com.vstory.test.crashstorm;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;

/**
 * 跑在 :worker 进程的常驻服务，配合 MainActivity 做多进程崩溃测试。
 *
 * 关键点：
 *  - onBind 恒返回 null —— 刻意不与主进程建立 binder 连接。有连接时主进程一死，
 *    AMS 的 cleanup 会把本服务一起收掉，就测不到「残留」了。
 *  - 存活模式由 prefs 里的 K_STICKY 决定，两种模式的 pid 轨迹不同：
 *      STICKY     → 本进程被系统杀掉后可能被重拉，表现为新 pid
 *      NOT_STICKY → 死透，表现为 pid 消失
 *    判读「残留」前必须先看这轮用的是哪种模式。
 */
public class WorkerService extends Service {

    public static final String ACTION_START = "com.vstory.test.crashstorm.action.START";
    public static final String ACTION_CRASH = "com.vstory.test.crashstorm.action.CRASH";

    public static final String PREFS = "worker";
    public static final String K_STICKY = "sticky";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = (intent == null) ? null : intent.getAction();

        if (ACTION_CRASH.equals(action)) {
            // 抛在主线程，走与主进程崩溃完全相同的 system_server AppErrors 路径
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                throw new RuntimeException("CrashStorm: worker process (pid=" + Process.myPid() + ")");
            }, 150L);
            return START_NOT_STICKY;
        }

        boolean sticky = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(K_STICKY, true);
        return sticky ? START_STICKY : START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
