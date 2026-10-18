
package com.cng.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TickReceiver extends BroadcastReceiver {

    private static final long WAIT_MS = 8000;

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pr = goAsync();
        final Context ctx = context.getApplicationContext();
        Logs.init(ctx);
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    Guard.get(ctx).check("定时自查");
                } catch (Throwable x) {
                    Logs.e("闹钟检测失败：" + x);
                } finally {
                    try {
                        Prefs p = new Prefs(ctx);
                        if (p.enabled() && !p.notifyOn()) {
                            AlarmScheduler.start(ctx, p.periodSec());   
                        }
                    } catch (Throwable ignored) { }
                }
            }
        }, "tick");
        t.start();
        try {
            t.join(WAIT_MS);
        } catch (InterruptedException ignored) {
        }
        try {
            pr.finish();
        } catch (Exception ignored) {
        }
    }
}
