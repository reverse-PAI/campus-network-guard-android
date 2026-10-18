
package com.cng.guard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class GuardService extends Service {

    static final String CH_ID = "guard";
    static final int NID = 1001;

    static volatile boolean running = false;

    private Handler handler;
    private ConnectivityManager.NetworkCallback callback;
    private Runnable periodic;
    private volatile boolean registered;
    private volatile boolean lastValidated = true;

    
    private final Runnable pendingCheck = new Runnable() {
        public void run() {
            Guard.get(GuardService.this).checkAsync("网络事件");
            updateNotification();
        }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, GuardService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    private final Runnable notifyRefresh = new Runnable() {
        public void run() { updateNotification(); }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        Guard.get(this).onChange = new Runnable() {
            public void run() {
                if (handler != null) handler.post(notifyRefresh);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) {
            
            updateNotification();
            return START_STICKY;
        }
        try {
            startFg();
        } catch (Exception e) {
            Logs.e("前台服务启动失败：" + e);
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        registerNet();
        if (periodic == null) {
            periodic = new Runnable() {
                public void run() {
                    try {
                        Guard.get(GuardService.this).checkAsync("定时自查");
                        handler.postDelayed(new Runnable() {
                            public void run() { updateNotification(); }
                        }, 3000);
                    } catch (Throwable t) {
                        Logs.e("定时自查出错：" + t);
                    } finally {
                        long period = new Prefs(GuardService.this).periodSec() * 1000L;
                        if (handler != null) handler.postDelayed(this, period);
                    }
                }
            };
        }
        if (handler != null) {
            handler.removeCallbacks(periodic);   
            handler.postDelayed(periodic, 2500);
            Logs.i("守护服务已启动，检测间隔 " + new Prefs(this).periodSec() + " 秒");
        }
        return START_STICKY;
    }

    private void startFg() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH_ID, "校园网守护", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        startForeground(NID, buildNotification());
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Guard g = Guard.get(this);
        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CH_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("校园网守护")
                .setContentText(g.statusText)
                .setOngoing(true)
                .setContentIntent(pi)
                .setOnlyAlertOnce(true);
        return b.build();
    }

    private void updateNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(NID, buildNotification());
        } catch (Exception ignored) { }
    }

    private void registerNet() {
        if (registered) return;
        ConnectivityManager cm = Net.cm(this);
        if (cm == null) return;
        NetworkRequest req = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        callback = new ConnectivityManager.NetworkCallback() {
            public void onAvailable(Network network) {
                schedule(3000);
            }

            public void onLost(Network network) {
                schedule(2000);
            }

            public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                boolean ok = caps != null
                        && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                if (ok != lastValidated) {        
                    lastValidated = ok;
                    schedule(ok ? 2500 : 5000);
                }
            }
        };
        try {
            cm.registerNetworkCallback(req, callback);
            registered = true;
            Logs.i("已订阅 WiFi 网络事件");
        } catch (Exception e) {
            Logs.w("订阅网络事件失败：" + e);
        }
    }

    private void schedule(long delayMs) {
        if (handler == null) return;
        handler.removeCallbacks(pendingCheck);   
        handler.postDelayed(pendingCheck, delayMs);
    }

    @Override
    public void onDestroy() {
        running = false;
        try { Guard.get(this).onChange = null; } catch (Throwable ignored) { }
        if (handler != null) {
            if (periodic != null) handler.removeCallbacks(periodic);
            handler.removeCallbacksAndMessages(null);
        }
        ConnectivityManager cm = Net.cm(this);
        if (registered && cm != null && callback != null) {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (Exception ignored) { }
        }
        registered = false;
        Logs.i("守护服务已停止");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}