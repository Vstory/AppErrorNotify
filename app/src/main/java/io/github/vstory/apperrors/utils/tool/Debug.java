/*
 * AppErrorsTracking (api102 重构版) - 独立调试日志类 (Java 化)
 * 对应模板 Debug.smali：封装 D 级(DEBUG=3)「双通道」调试日志。
 *
 * 双通道 = 同时输出：
 *   ① 框架日志 module.log(Log.DEBUG, tag, msg, throwable) —— LSPosed 管理器「日志」可见
 *   ② 进程 logcat android.util.Log.d(tag, msg, throwable) —— adb / LSPosed 自带 logcat 可见
 *
 * ⚠️ 规范（2026-09-01 知识库确认）：
 *   - D 级调试日志会刷屏 → 正式版【只注释调用点】；本类文件与方法【永不删】。
 *   - D 级必须双通道（框架 log(3) + logcat Log.d），两边都能看调试日志。
 *   - tag 默认用模块名 AppErrorNotify（log() 显式传入的 tag 字符串）。
 *
 * 用法：Debug.d("msg") / Debug.d("tag", "msg") / Debug.d("tag", "msg", throwable)
 */
package io.github.vstory.apperrors.utils.tool;

import android.util.Log;

import io.github.vstory.apperrors.hook.HookEntry;

/** 双通道 D 级(DEBUG)调试日志（正式版只注释调用点，本类永留） */
public class Debug {

    /** 模块名 tag（与框架日志 module,tag 对齐） */
    public static final String TAG = "AppErrorNotify";

    /** D 级双通道：默认 tag = 模块名 */
    public static void d(String msg) {
        d(TAG, msg, null);
    }

    /** D 级双通道：tag + msg */
    public static void d(String tag, String msg) {
        d(tag, msg, null);
    }

    /** D 级双通道：tag + msg + throwable */
    public static void d(String tag, String msg, Throwable throwable) {
        // ① 框架日志（LSPosed 管理器可见）—— module.log(级别, tag, msg, throwable)
        HookEntry entry = HookEntry.getInstance();
        if (entry != null) {
            entry.log(Log.DEBUG, tag, msg != null ? msg : "", throwable);
        }
        // ② 进程 logcat（adb / LSPosed 自带 logcat 可见）—— 即使模块实例 null 也不丢
        if (throwable != null) {
            Log.d(tag, msg != null ? msg : "", throwable);
        } else {
            Log.d(tag, msg != null ? msg : "");
        }
    }

    private Debug() {}
}
