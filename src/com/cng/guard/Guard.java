
package com.cng.guard;

import android.content.Context;
import android.net.Network;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Guard {

    private static Guard inst;

    static synchronized Guard get(Context c) {
        if (inst == null) inst = new Guard(c.getApplicationContext());
        return inst;
    }

    private static final long ONLINE_LOG_GAP_MS = 5 * 60 * 1000L;

    final Context ctx;
    final Prefs prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();

    volatile String status = "unknown";
    volatile String statusText = "尚未检测";
    volatile String lastCheck = "";
    volatile String lastTrigger = "";
    volatile String lastProbe = "";
    volatile String lastAction = "";
    volatile String lastUp = "";
    volatile String lastDown = "";
    volatile int checks;
    volatile int todayDown;
    volatile int failStreak;
    volatile long cooldownUntil;
    volatile boolean busy;

    private volatile long lastCheckMs;
    private volatile long lastOnlineLogMs;
    private volatile String today = "";

    volatile String notice = "";
    volatile long noticeAt;
    
    volatile Runnable onChange;

    private Guard(Context c) {
        ctx = c;
        prefs = new Prefs(c);
        today = prefs.statDate();
        todayDown = prefs.statTodayDown();
        checks = prefs.statChecks();
        lastDown = prefs.statLastDown();
        lastUp = prefs.statLastUp();
    }

    private void persistStats() {
        prefs.saveStats(today, todayDown, checks, lastDown, lastUp);
    }

    static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static String todayStr() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    void checkAsync(final String trigger) {
        worker.submit(new Runnable() {
            public void run() {
                check(trigger);
            }
        });
    }

    void check(final String trigger) {
        boolean manual = trigger != null && trigger.startsWith("手动");
        long now0 = System.currentTimeMillis();
        if (now0 - noticeAt > 60000) {
            notice = "";          
        }
        if (shouldSkip(manual, prefs.enabled(), lastCheckMs, now0, prefs.minIntervalSec())) return;
        synchronized (lock) {
            if (busy) return;
            busy = true;
        }
        try {
            String td = todayStr();
            if (!td.equals(today)) {
                today = td;
                todayDown = 0;
            }
            checks++;
            Network wifi = Net.wifi(ctx);
            String detail = Net.probe(wifi);
            lastCheck = now();
            lastTrigger = trigger;
            lastProbe = (detail == null) ? "探测通过" : friendly(detail);
            if (detail == null) {
                boolean wasDown = !"online".equals(status);
                status = "online";
                statusText = "网络正常";
                failStreak = 0;
                cooldownUntil = 0;
                lastAction = "";
                if (wasDown) lastUp = lastCheck;
                persistStats();
                long ms = System.currentTimeMillis();
                if (wasDown || manual || ms - lastOnlineLogMs > ONLINE_LOG_GAP_MS) {
                    Logs.i("检测(" + trigger + ")：网络正常");
                    lastOnlineLogMs = ms;
                }
                fireOnChange();
                return;
            }
            boolean wasDown = "offline".equals(status) || "recovering".equals(status) || "other".equals(status);
            status = "offline";
            statusText = "已断网，正在尝试恢复";
            if (!wasDown) {
                lastDown = lastCheck;
                todayDown++;
            }
            persistStats();
            Logs.w("检测(" + trigger + ")：网络不通 —— " + detail);
            recover(manual);
        } catch (Throwable t) {
            Logs.e("检测出错：" + t);
            statusText = "检测出错：" + t.getClass().getSimpleName();
        } finally {
            fireOnChange();
            lastCheckMs = System.currentTimeMillis();
            synchronized (lock) {
                busy = false;
            }
        }
    }

    private void recover(boolean manual) {
        long nowMs = System.currentTimeMillis();
        if (inCooldown(cooldownUntil, nowMs, manual)) {
            statusText = "熔断中，还有 " + ((cooldownUntil - nowMs) / 1000) + " 秒";
            Logs.i(statusText);
            return;
        }
        String host = prefs.portalName();
        int port = prefs.portalPort();
        String user = prefs.user();
        String pwd = prefs.password();
        String acid = prefs.acid();

        if (user == null || user.isEmpty() || pwd == null || pwd.isEmpty()) {
            status = "other";
            statusText = "未配置账号或密码";
            Logs.w(statusText);
            return;
        }
        if (host == null || host.isEmpty()) {
            status = "other";
            statusText = "未配置认证服务器";
            Logs.w(statusText);
            return;
        }
        Network wifi = Net.wifi(ctx);
        if (wifi == null) {
            status = "offline";
            statusText = "当前没有连接 WiFi";
            Logs.w(statusText);
            fail();
            return;
        }
        String gw = Net.gateway(ctx, wifi);
        boolean gwKnown = !gw.isEmpty();
        String nets = prefs.campusNets();
        boolean gwOk = gwKnown && Net.prefixMatches(gw, nets);
        boolean netsEmpty = nets.trim().isEmpty();
        Logs.i("当前网络：网关=" + (gwKnown ? gw : "读不到")
                + "，已识别校园网=" + (netsEmpty ? "（未标记，默认放行）" : nets));

        if (prefs.onlyCampus() && !netsEmpty && gwKnown && !gwOk) {
            status = "other";
            statusText = "当前不在已识别的校园网内（" + gw + "），未自动重连";
            Logs.w(statusText);
            return;
        }
        boolean reachable = Net.tcp(wifi, host, port, 1500) || Net.tcp(null, host, port, 1200);
        if (!reachable) Logs.w("门户 " + host + ":" + port + " 暂时连不通，仍尝试重新认证");

        status = "recovering";
        statusText = "正在重新认证…";
        lastAction = "SRun 重新认证";
        Logs.i("--- 恢复步骤：SRun 重新认证 ---");
        try {
            String ip = Net.ipv4(ctx, wifi);
            JSONObject res = Srun.login(wifi, host, port, user, pwd, acid, ip);
            boolean ok = res.optBoolean("ok", false);
            Logs.i("SRun 认证" + (ok ? "成功" : "失败")
                    + " ecode=" + res.optString("ecode", "?")
                    + " suc_msg=" + res.optString("suc_msg", "")
                    + " error_msg=" + res.optString("error_msg", ""));
            if (ok) {
                String again = Net.probe(wifi);
                if (again == null) {
                    status = "online";
                    statusText = "网络已恢复";
                    lastUp = now();
                    failStreak = 0;
                    cooldownUntil = 0;
                    lastAction = "";
                    Logs.i("通过 [SRun 重新认证] 恢复成功");
                    return;
                }
                Logs.w("认证成功但探测仍失败：" + again);
            }
        } catch (Throwable t) {
            Logs.e("SRun 认证异常：" + t);
        }
        fail();
    }

    
    
    static boolean shouldSkip(boolean manual, boolean enabled, long lastCheckMs, long nowMs, int minIntervalSec) {
        if (manual) return false;                 
        if (!enabled) return true;                
        if (minIntervalSec <= 0) return false;
        long elapsed = nowMs - lastCheckMs;
        return elapsed >= 0 && elapsed < minIntervalSec * 1000L;
    }

    
    static boolean inCooldown(long cooldownUntil, long nowMs, boolean manual) {
        return !manual && cooldownUntil > nowMs;
    }

    static String friendly(String detail) {
        if (detail == null || detail.isEmpty()) return "网络不通";
        String d = detail;
        if (d.contains("UnknownHostException")) return "域名解析失败（多半已掉线）";
        if (d.contains("被劫持")) return "被门户劫持（需要重新认证）";
        if (d.contains("SocketTimeoutException") || d.contains("timeout") || d.contains("Timeout"))
            return "连接超时（网络可能已断）";
        if (d.contains("ConnectException") || d.contains("refused")) return "连接被拒绝";
        return d.length() > 80 ? d.substring(0, 80) : d;
    }

    private void fireOnChange() {
        Runnable r = onChange;
        if (r != null) {
            try { r.run(); } catch (Throwable ignored) { }
        }
    }

    private void fail() {
        failStreak++;
        status = "offline";
        statusText = "恢复失败（已连续 " + failStreak + " 次）";
        int maxFail = prefs.maxFail();
        if (failStreak >= maxFail) {
            int cd = prefs.cooldownSec();
            cooldownUntil = System.currentTimeMillis() + cd * 1000L;
            failStreak = 0;
            statusText = cd > 0 ? ("连续失败，已暂停 " + (cd / 60) + " 分钟") : "连续失败";
            Logs.e("连续 " + maxFail + " 次恢复失败，" + statusText);
        }
    }

    
    String testLogin() {
        String host = prefs.portalName();
        int port = prefs.portalPort();
        String user = prefs.user();
        String pwd = prefs.password();
        String out;
        if (user.isEmpty() || pwd.isEmpty()) {
            out = "测试失败：未配置账号或密码";
        } else if (host.isEmpty()) {
            out = "测试失败：未配置认证服务器";
        } else if (Net.wifi(ctx) == null) {
            out = "测试失败：当前没有连接 WiFi";
        } else {
            try {
                JSONObject r = Srun.login(Net.wifi(ctx), host, port, user, pwd, prefs.acid(), Net.ipv4(ctx, Net.wifi(ctx)));
                if (r.optBoolean("ok", false)) {
                    String suc = r.optString("suc_msg", "");
                    if ("ip_already_online_error".equals(suc)) {
                        
                        out = "已登录（IP 已在线）——此时无法验证账号密码；要严格验证请先在门户注销再测";
                    } else {
                        out = "认证成功：" + (suc.isEmpty() ? "ok" : suc);
                    }
                } else {
                    out = "测试失败：ecode=" + r.optString("ecode")
                            + " " + r.optString("error_msg", r.optString("error", ""));
                }
            } catch (Throwable t) {
                out = "测试失败：" + friendly(String.valueOf(t));
            }
        }
        notice = out;
        noticeAt = System.currentTimeMillis();
        Logs.i("测试登录｜" + out);
        fireOnChange();
        return out;
    }
}
