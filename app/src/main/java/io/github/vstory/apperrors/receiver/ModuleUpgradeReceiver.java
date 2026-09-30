/*
 * AppErrorsTracking (api102 重构版) - 升级后「配置镜像播种」接收器
 */
package io.github.vstory.apperrors.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import io.github.vstory.apperrors.data.ConfigData;

/**
 * 模块升级（{@link Intent#ACTION_MY_PACKAGE_REPLACED}）后，立刻把 RemotePreferences（权威）
 * 播种进本地镜像文件。
 *
 * <p>为什么需要它：本地镜像（{@code ConfigData} 的 {@code localPrefs}）是模块 UI「service 绑定前」
 * 唯一能读到的存储。用户升级前设过的开关 / 界面语言 / 忽略行为行只存在于 remote（框架侧托管），
 * 而镜像文件在旧版本里并不存在 ⇒ 升级后**第一次**打开模块 UI 时镜像为空，首帧只能显示默认值。
 * 本接收器让系统在「升级完成」时就把模块进程拉起来（= 顺带触发框架推 binder），等绑定 + 播种
 * 完成后进程即可退出；用户随后打开 UI 时镜像已就绪，首帧即正确——不需要任何「绑上后重建界面」
 * 的事后补救。</p>
 *
 * <p>为什么非要它（而不是只靠 {@code Application} 里那次 500ms 等待）：升级后系统很忙，绑定可能
 * 迟于 500ms；而本接收器在**用户还没打开 UI 之前**就跑完了，把「等待」放到用户无感的时刻。</p>
 *
 * <p>{@code exported=false} 也能收到：{@code MY_PACKAGE_REPLACED} 由系统发出，不受 export 限制
 * （实测见 StackOverflow 同名结论）；本接收器也不接收任何外部数据，只是等一次绑定。</p>
 */
public class ModuleUpgradeReceiver extends BroadcastReceiver {

    private static final String TAG = "AppErrorNotify";

    /** 升级后等待绑定的上限（ms）：升级刚完成时系统较忙，给足时间；跑在后台线程，不阻塞任何 UI */
    private static final long SEED_WAIT_MS = 5000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        // 镜像已播种（例如本次升级前就绑过一次）→ 无事可做，直接返回，不占进程
        // 等待额度用尽（该设备上从没等到过 binder，多半没装/没激活框架）→ 也不占进程
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
                    /** 忽略：进程即将退出，无需补救 */
                }
            }
        }, "apperrors-upgrade-seed").start();
    }
}
