package com.vstory.test.crashstorm;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * AppErrorNotify 崩溃通知 / 风暴抑制 的实机测试工具。
 *
 * ⚠️ 2026-09-05 温和化：完整风暴(12次)曾把系统 crash_dump 链路拖垮导致死机（真机实证），
 *    日常验证请用【温和风暴 4 次】（第 3 次即触发熔断 force-stop，足够闭环）；
 *    完整风暴仅供压力场景，高风险，慎用！
 *
 * 用法：
 *  - 前台崩溃：留在本页面点「前台崩溃」立即崩 → 模块详情应显示 崩溃时页面=MainActivity。
 *  - 后台崩溃：点「后台崩溃」后按 Home 键回桌面（本页退到后台、无可见界面），3 秒后自动崩
 *    → 模块详情应显示 崩溃时页面=后台。⚠️ 若点完不按 Home 键，3 秒到点仍在前台 → 仍算前台崩溃。
 *  - 单次崩溃：主线程抛异常，应触发 1 条崩溃通知。
 *  - 温和风暴(4次)：自动连崩 4 次（每次间隔约 2-3 秒）。第 3 次（30 秒内）触发自动抑制：
 *    force-stop + 清通知 + 弹「已自动暂停」说明通知，第 4 次应被 force-stop 掐断（闹钟清空）。
 *    用于验证：
 *      1) 通知收敛：同应用反复崩溃只替换同一条通知，不堆积；
 *      2) 自动抑制：30 秒内 ≥3 次 → force-stop + 说明通知；
 *      3) 手动恢复：点说明通知「恢复通知」→ 计数归零；
 *      4) 自动恢复：平静 3 分钟自动解除。
 */
public class MainActivity extends Activity {

    private static final String PREFS = "storm";
    private static final String K_ON = "on";       // 风暴进行中
    private static final String K_LEFT = "left";   // 剩余要崩的次数（不含刚崩的这次）
    private static final String K_TOTAL = "total"; // 本轮风暴总次数
    private static final int STORM_MILD = 4;        // 温和风暴：3 连崩即触发熔断，4 次足够闭环
    private static final int STORM_FULL = 12;       // 完整风暴：⚠️ 高风险（曾死机），仅压力测试用
    private static final long RELAUNCH_DELAY_MILD_MS = 1500; // 温和：放慢节奏，减轻 crash_dump 瞬时压力
    private static final long RELAUNCH_DELAY_FULL_MS = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean crashReady = false;
    private long relaunchDelayMs = RELAUNCH_DELAY_MILD_MS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean on = sp.getBoolean(K_ON, false);
        int left = sp.getInt(K_LEFT, 0);
        if (on) {
            if (left > 0) {
                // 闹钟复活：本次要再崩一次。等 onWindowFocusChanged（界面真正可见）再崩，
                // 确保走"前台崩溃 → 系统崩溃对话框"路径（后台崩溃不会弹通知）。
                crashReady = true;
                return;
            }
            // 风暴结束
            sp.edit().putBoolean(K_ON, false).apply();
            int total = sp.getInt(K_TOTAL, STORM_MILD);
            Toast.makeText(this, "风暴结束（共 " + total + " 次崩溃）", Toast.LENGTH_LONG).show();
        }
        buildUi();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && crashReady) {
            crashReady = false;
            doStormCrash();
        }
    }

    /** 崩一次（风暴模式）：维护计数 + 安排下次复活 + 崩溃 */
    private void doStormCrash() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int total = sp.getInt(K_TOTAL, STORM_MILD);
        int left = sp.getInt(K_LEFT, 0);            // 崩前剩余（不含本次）
        int seq = total - left + 1;                  // 本次是第几次
        int next = left - 1;
        sp.edit().putInt(K_LEFT, next).apply();
        if (next > 0) scheduleRelaunch();
        crashNow("storm #" + seq + "/" + total);
    }

    /** 安排一次自复活（延迟后启动本 activity） */
    private void scheduleRelaunch() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + relaunchDelayMs, pi);
    }

    /** 主线程抛异常 → 崩溃（走 system_server AppErrors 前台崩溃路径） */
    private void crashNow(final String tag) {
        handler.postDelayed(() -> {
            throw new RuntimeException("CrashStorm: " + tag);
        }, 150);
    }

    /** 后台崩溃：先退回桌面（本页无可见界面），3 秒后在主线程抛异常 */
    private void crashBackground() {
        moveTaskToBack(true);
        handler.postDelayed(() -> {
            throw new RuntimeException("CrashStorm: background (expect page=后台)");
        }, 3000L);
    }

    /** 启动一轮风暴 */
    private void startStorm(final int total, final long delayMs) {
        relaunchDelayMs = delayMs;
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        sp.edit().putBoolean(K_ON, true)
                .putInt(K_TOTAL, total)
                .putInt(K_LEFT, total - 1).apply();
        scheduleRelaunch();   // 先为"复活后再崩"排好闹钟
        crashNow("storm #1/" + total);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView tip = new TextView(this);
        tip.setTextSize(14);
        tip.setText("AppErrorNotify 崩溃通知 / 风暴抑制测试工具\n\n" +
                "单次崩溃：应收到 1 条崩溃通知。\n\n" +
                "温和风暴（推荐）：自动连崩 4 次（间隔约 2-3 秒）。\n" +
                "第 3 次（30 秒内）触发自动抑制：force-stop + 清通知 +\n" +
                "弹「已自动暂停」说明通知，第 4 次应被掐断不再崩。\n\n" +
                "⚠️ 完整风暴（12 次快速连崩）曾导致系统死机，仅供压力场景慎用！");
        root.addView(tip);

        Button fg = new Button(this);
        fg.setText("前台崩溃（验证 崩溃时页面=MainActivity）");
        fg.setOnClickListener(v -> crashNow("foreground (expect page=MainActivity)"));
        root.addView(fg, lp());

        Button bg = new Button(this);
        bg.setText("后台崩溃（点后按 Home，验证 崩溃时页面=后台）");
        bg.setOnClickListener(v -> crashBackground());
        root.addView(bg, lp());

        Button single = new Button(this);
        single.setText("单次崩溃");
        single.setOnClickListener(v -> crashNow("single"));
        root.addView(single, lp());

        Button mild = new Button(this);
        mild.setText("温和风暴（连崩 4 次，推荐）");
        mild.setOnClickListener(v -> startStorm(STORM_MILD, RELAUNCH_DELAY_MILD_MS));
        root.addView(mild, lp());

        Button full = new Button(this);
        full.setText("完整风暴（连崩 12 次，⚠️ 高风险）");
        full.setOnClickListener(v -> startStorm(STORM_FULL, RELAUNCH_DELAY_FULL_MS));
        root.addView(full, lp());

        setContentView(root);
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(12);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
