
package com.cng.guard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class AlarmScheduler {

    static final String ACTION = "com.cng.guard.TICK";

    private AlarmScheduler() { }

    private static PendingIntent pi(Context c, boolean create) {
        Intent i = new Intent(c, TickReceiver.class);
        i.setAction(ACTION);
        int flags = PendingIntent.FLAG_IMMUTABLE
                | (create ? PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_NO_CREATE);
        return PendingIntent.getBroadcast(c, 0, i, flags);
    }

    private static long lastScheduleAt;

    static void start(Context c, int periodSec) {
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastScheduleAt < 5000) return;   
        lastScheduleAt = nowMs;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long ms = Math.max(30, periodSec) * 1000L;
        long at = System.currentTimeMillis() + ms;
        try {
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, true));
                    Logs.i("闹钟模式：已排下一次检测，间隔 " + (ms / 1000) + " 秒（精确）");
                    return;
                } catch (SecurityException se) {
                    Logs.w("精确闹钟被系统拒绝，改用非精确：" + se.getClass().getSimpleName());
                }
            }
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(c, true));
            Logs.i("闹钟模式：已排下一次检测，间隔 " + (ms / 1000) + " 秒（非精确，可能延迟）");
        } catch (Throwable t) {
            Logs.e("闹钟模式排期失败：" + t);
        }
    }

    static void stop(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            PendingIntent p = pi(c, false);      
            if (p == null) return;               
            am.cancel(p);
            p.cancel();
            Logs.i("已停止闹钟模式");
        } catch (Throwable ignored) { }
    }
}
