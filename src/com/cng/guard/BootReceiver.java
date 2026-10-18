
package com.cng.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Prefs p = new Prefs(context);
            if (!p.enabled()) return;
            Logs.init(context);
            Logs.i("开机自启：恢复守护");
            if (!p.notifyOn()) {
                AlarmScheduler.start(context, p.periodSec());   
                return;
            }
            try {
                GuardService.start(context);
            } catch (Throwable t) {
                Logs.e("开机自启：前台服务被系统限制，改走闹钟模式：" + t);
                AlarmScheduler.start(context, p.periodSec());
            }
        } catch (Exception e) {
            Logs.e("开机自启失败：" + e);
        }
    }
}
