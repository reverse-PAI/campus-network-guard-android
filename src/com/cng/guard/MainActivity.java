
package com.cng.guard;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private static final int GREEN = 0xFF12A150;
    private static final int RED = 0xFFE5484D;
    private static final int AMBER = 0xFFE08C00;
    private static final int GRAY = 0xFF9AA3AE;
    private static final int TEXT = 0xFF23272E;
    private static final int MUTED = 0xFF6B7280;
    private static final int REQ_NOTIFY = 100;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Guard guard;
    private Prefs prefs;

    private TextView tvDot, tvStatus, tvGuard, tvSub, tvNet, tvLog, tvNets, tvAcctHint;
    private TextView tvToday, tvLastDown, tvLastUp, tvChecks;
    private ScrollView logScroll;
    private LinearLayout advBox;
    private Button btnPower, btnMark, btnAdv, btnLogToggle;
    private Switch swNotify;
    private EditText etUser, etPass, etHost, etPeriod, etFail, etCool, etAcid;
    private int lastLogVer = -1;
    private String cachedNets = "";
    private long lastReviveMs;
    private long lastNetUiMs;
    private String cachedNetLine = "";
    private int cachedNetColor = 0x6B7280;
    private boolean cachedInCampus;
    private boolean advOpen = false, logOpen = false;

    private final Runnable tick = new Runnable() {
        public void run() { refresh(); ui.postDelayed(this, 1000); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logs.init(this);
        guard = Guard.get(this);
        prefs = new Prefs(this);
        
        long curBoot = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        long prevBoot = prefs.bootTime();
        rebootedThisLaunch = prevBoot != 0 && Math.abs(curBoot - prevBoot) > 60000;
        if (rebootedThisLaunch) rebootNoticeUntil = System.currentTimeMillis() + 60000;
        prefs.setBootTime(curBoot);
        buildUi();
        askNotify();
        if (prefs.enabled()) applyMode();
        Logs.i("界面已打开（v" + version() + "）");
    }

    private String version() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }

    
    private boolean notificationsBlocked() {
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return false;
            if (!nm.areNotificationsEnabled()) return true;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel ch = nm.getNotificationChannel("guard");
                return ch != null && ch.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    
    private boolean rebootedThisLaunch;
    
    private long rebootNoticeUntil;

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(tick);
        ui.post(tick);
        if (prefs.enabled()) applyMode();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout card(LinearLayout root, String title) {
        root.addView(tv(title, 12, MUTED, true));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(Color.WHITE);
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(10);
        root.addView(c, lp);
        return c;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFFEEF1F5);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(20));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(tv("校园网守护", 20, TEXT, true));
        root.addView(tv("深澜 SRun · 断线自动重认证 · v" + version(), 11, MUTED, false));

        
        LinearLayout st = card(root, "状态");
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        tvDot = tv("\u25CF", 20, GRAY, true);
        head.addView(tvDot);
        tvStatus = tv("尚未检测", 17, TEXT, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(8);
        head.addView(tvStatus, lp);
        st.addView(head);
        tvGuard = tv("", 12, TEXT, true);
        tvGuard.setPadding(0, dp(4), 0, 0);
        st.addView(tvGuard);
        tvSub = tv("", 12, MUTED, false);
        tvSub.setPadding(0, dp(2), 0, 0);
        st.addView(tvSub);
        tvNet = tv("", 13, TEXT, true);
        tvNet.setPadding(0, dp(6), 0, 0);
        st.addView(tvNet);

        
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button b1 = btn("立即检测");
        b1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { guard.checkAsync("手动"); toast("已开始检测"); }
        });
        Button b2 = btn("立即重连");
        b2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { guard.checkAsync("手动重连"); toast("已开始重连"); }
        });
        Button b3 = btn("测试登录");
        b3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toast("正在测试认证…");
                new Thread(new Runnable() {
                    public void run() {
                        final String r = guard.testLogin();
                        runOnUiThread(new Runnable() { public void run() { toast(r); } });
                    }
                }).start();
            }
        });
        for (Button b : new Button[]{b1, b2, b3}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.rightMargin = dp(4);
            row1.addView(b, p);
        }
        root.addView(row1);

        btnPower = btn(prefs.enabled() ? "停止守护" : "开始守护");
        btnPower.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean on = !prefs.enabled();
                prefs.setEnabled(on);
                applyMode();
                Logs.i(on ? "用户开启了守护" : "用户停止了守护");
                toast(on ? "已开始守护：断网会自动重新认证" : "已停止守护：不再自动检测");
                refresh();
            }
        });
        LinearLayout.LayoutParams lpg = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpg.topMargin = dp(6);
        root.addView(btnPower, lpg);

        btnMark = btn("标记当前网络为校园网");
        btnMark.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { toggleMark(); }
        });
        LinearLayout.LayoutParams lpm = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpm.topMargin = dp(6);
        root.addView(btnMark, lpm);

        
        LinearLayout stat = card(root, "统计");
        LinearLayout srow = new LinearLayout(this);
        srow.setOrientation(LinearLayout.HORIZONTAL);
        tvToday = tv("", 12, TEXT, false);
        tvChecks = tv("", 12, TEXT, false);
        srow.addView(tvToday, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        srow.addView(tvChecks, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stat.addView(srow);
        tvLastDown = tv("", 12, MUTED, false);
        tvLastDown.setPadding(0, dp(4), 0, 0);
        stat.addView(tvLastDown);
        tvLastUp = tv("", 12, MUTED, false);
        stat.addView(tvLastUp);

        
        LinearLayout set = card(root, "账号与设置");
        etUser = fld(set, "账号", prefs.user(), InputType.TYPE_CLASS_TEXT);
        etPass = fld(set, "密码", prefs.password(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etHost = fld(set, "认证服务器 IP（登录页地址栏里那个）", prefs.host(), InputType.TYPE_CLASS_TEXT);
        tvAcctHint = tv("", 11, RED, false);
        tvAcctHint.setPadding(0, dp(6), 0, 0);
        set.addView(tvAcctHint);
        Button save = btn("保存设置");
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { saveAll(); }
        });
        LinearLayout.LayoutParams lps = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lps.topMargin = dp(6);
        set.addView(save, lps);

        
        btnAdv = btn("高级设置 ▾");
        btnAdv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                advOpen = !advOpen;
                advBox.setVisibility(advOpen ? View.VISIBLE : View.GONE);
                btnAdv.setText(advOpen ? "高级设置 ▴" : "高级设置 ▾");
            }
        });
        LinearLayout.LayoutParams lpa = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpa.topMargin = dp(2);
        root.addView(btnAdv, lpa);

        advBox = new LinearLayout(this);
        advBox.setOrientation(LinearLayout.VERTICAL);
        advBox.setBackgroundColor(Color.WHITE);
        advBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        advBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams lpb = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpb.topMargin = dp(4);
        lpb.bottomMargin = dp(8);
        root.addView(advBox, lpb);

        etPeriod = fld(advBox, "自动检查间隔（秒，30–3600）", String.valueOf(prefs.periodSec()), InputType.TYPE_CLASS_NUMBER);
        etFail = fld(advBox, "连续失败几次后暂停（1–99）", String.valueOf(prefs.maxFail()), InputType.TYPE_CLASS_NUMBER);
        etCool = fld(advBox, "暂停多久（秒，0–86400）", String.valueOf(prefs.cooldownSec()), InputType.TYPE_CLASS_NUMBER);
        etAcid = fld(advBox, "认证接口编号（一般保持 1，不用改）", prefs.acid(), InputType.TYPE_CLASS_NUMBER);
        swNotify = switchRow(advBox, "常驻通知（关掉改走系统闹钟，可能被系统杀）", prefs.notifyOn());
        tvNets = tv("", 12, MUTED, false);
        tvNets.setPadding(0, dp(8), 0, 0);
        advBox.addView(tvNets);
        Button clearNets = btn("清除已识别的校园网");
        clearNets.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                prefs.setCampusNets("");
                Logs.i("已清除全部已识别的校园网");
                toast("已清除，下次进校园网点一次标记即可");
                refresh();
            }
        });
        advBox.addView(clearNets, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button advSave = btn("保存高级设置");
        advSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { saveAll(); }
        });
        advBox.addView(advSave, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button btnBat = btn("电源优化设置（小米/OPPO 必做）");
        btnBat.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) {
                    try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                    catch (Exception ignored) { toast("请到系统设置里关闭电池优化"); }
                }
            }
        });
        advBox.addView(btnBat, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnAuto = btn("自启动与省电设置（打开系统设置）");
        btnAuto.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openAutoStartSettings(); }
        });
        advBox.addView(btnAuto, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        
        LinearLayout logCard = card(root, "运行日志");
        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(0xFF1E2229);
        logScroll.setPadding(dp(8), dp(8), dp(8), dp(8));
        tvLog = tv("", 10, 0xFFD7DCE3, false);
        tvLog.setTypeface(Typeface.MONOSPACE);
        logScroll.addView(tvLog);
        logCard.addView(logScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96)));
        LinearLayout logBtns = new LinearLayout(this);
        logBtns.setOrientation(LinearLayout.HORIZONTAL);
        btnLogToggle = btn("展开全部");
        btnLogToggle.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                logOpen = !logOpen;
                btnLogToggle.setText(logOpen ? "收起" : "展开全部");
                LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) logScroll.getLayoutParams();
                p.height = logOpen ? dp(260) : dp(96);
                logScroll.setLayoutParams(p);
                lastLogVer = -1;
                refresh();
            }
        });
        Button btnCopy = btn("复制");
        btnCopy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                List<String> ls = Logs.tail();
                StringBuilder sb = new StringBuilder();
                for (String s : ls) sb.append(s).append("\n");
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("校园网守护日志", sb.toString()));
                toast("日志已复制");
            }
        });
        Button btnClear = btn("清空");
        btnClear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Logs.clear();
                lastLogVer = -1;
                refresh();
                toast("日志已清空");
            }
        });
        for (Button b : new Button[]{btnLogToggle, btnCopy, btnClear}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.rightMargin = dp(4);
            logBtns.addView(b, p);
        }
        logCard.addView(logBtns);

        setContentView(scroll);
    }

    private EditText fld(LinearLayout parent, String label, String value, int type) {
        TextView t = tv(label, 11, MUTED, false);
        t.setPadding(0, dp(8), 0, 0);
        parent.addView(t);
        EditText e = new EditText(this);
        e.setText(value == null ? "" : value);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setSingleLine(true);
        e.setInputType(type);                     
        if ((type & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0) {
            e.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        }
        parent.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private Switch switchRow(LinearLayout parent, String label, boolean checked) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(10), 0, 0);
        r.addView(tv(label, 12, MUTED, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch s = new Switch(this);
        s.setChecked(checked);
        r.addView(s);
        parent.addView(r);
        return s;
    }

    
    private void toggleMark() {
        android.net.Network wn = Net.wifi(this);
        String gw = Net.gateway(this, wn);
        String subnet = Net.subnetOf(gw);
        if (subnet.isEmpty()) {
            toast("现在读不到网络信息，请先连上校园网 WiFi");
            return;
        }
        String nets = prefs.campusNets();
        if (Net.prefixMatches(gw, nets)) {
            StringBuilder sb = new StringBuilder();
            for (String s : nets.split("[,，\\s]+")) {
                String x = s.trim();
                if (x.isEmpty() || gw.startsWith(x)) continue;
                if (sb.length() > 0) sb.append(",");
                sb.append(x);
            }
            prefs.setCampusNets(sb.toString());
            Logs.i("已取消标记校园网：" + subnet);
            toast("已取消标记：这个网络不再当作校园网");
        } else {
            String all = nets.trim().isEmpty() ? subnet : (nets.trim() + "," + subnet);
            prefs.setCampusNets(all);
            Logs.i("已标记当前网络为校园网：" + subnet);
            toast("已记为校园网；以后连这个网络才会自动重连");
        }
        lastNetUiMs = 0;   
        refresh();
        if (prefs.enabled()) applyMode();
    }

    private int num(EditText e, int def) {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch (Exception ex) { return def; }
    }

    private void saveAll() {
        prefs.setUser(etUser.getText().toString());
        boolean pwdOk = prefs.setPassword(etPass.getText().toString());
        prefs.setHost(etHost.getText().toString());
        prefs.setPeriodSec(num(etPeriod, 60));
        prefs.setMaxFail(num(etFail, 5));
        prefs.setCooldownSec(num(etCool, 600));
        prefs.setAcid(etAcid.getText().toString());
        prefs.setNotifyOn(swNotify.isChecked());
        applyMode();
        Logs.i("设置已保存：间隔 " + prefs.periodSec() + "s，通知=" + prefs.notifyOn()
                + "，已识别校园网=" + prefs.campusNets());
        if (!pwdOk) {
            toast("其余设置已保存，但密码没能加密保存（系统密钥库异常），请重试");
            Logs.e("密码未能保存，用户需重试");
        } else {
            toast("已保存并生效");
        }
        refresh();
    }

    
    private void openAutoStartSettings() {
        try {
            Intent i = new Intent();
            i.setComponent(new android.content.ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            startActivity(i);
            return;
        } catch (Throwable ignored) { }
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            toast("请手动到「设置 → 应用 → 校园网守护」里允许自启动");
        }
    }

    
    private void setTxt(TextView v, CharSequence text) {
        if (v == null) return;
        CharSequence old = v.getText();
        if (old == null || !old.equals(text)) v.setText(text);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void askNotify() {
        java.util.List<String> need = new java.util.ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_NOTIFY);
    }

    private void applyMode() {
        if (!prefs.enabled()) {
            stopService(new Intent(this, GuardService.class));
            AlarmScheduler.stop(this);
            return;
        }
        if (prefs.notifyOn()) {
            AlarmScheduler.stop(this);
            GuardService.start(this);
        } else {
            stopService(new Intent(this, GuardService.class));
            AlarmScheduler.start(this, prefs.periodSec());
        }
    }

    private void refresh() {
        if (guard == null) return;
        String st = guard.status;
        int color = GRAY;
        if ("online".equals(st)) color = GREEN;
        else if ("offline".equals(st)) color = RED;
        else if ("recovering".equals(st) || "other".equals(st)) color = AMBER;
        tvDot.setTextColor(color);
        setTxt(tvStatus, guard.statusText);

        boolean on = prefs.enabled();
        setTxt(btnPower, on ? "停止守护" : "开始守护");
        btnPower.setTextColor(on ? RED : GREEN);
        boolean alive = GuardService.running || !prefs.notifyOn();   
        if (on && !alive && System.currentTimeMillis() - lastReviveMs > 10000) {
            lastReviveMs = System.currentTimeMillis();
            applyMode();          
        }
        if (!on) {
            tvGuard.setTextColor(RED);
            tvGuard.setText("守护：已停止（不会自动检测）");
        } else if (alive) {
            tvGuard.setTextColor(GREEN);
            tvGuard.setText("守护：运行中 · 每 " + prefs.periodSec() + " 秒自检"
                    + (prefs.notifyOn() ? " · 常驻通知" : " · 无通知"));
        } else {
            tvGuard.setTextColor(AMBER);
            tvGuard.setText("守护：已开启，但后台服务没在运行（正在自动重启…）");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(guard.lastCheck.isEmpty() ? "尚未检测"
                : ("最近检测 " + guard.lastCheck + "（" + guard.lastTrigger + "）"));
        if (guard.notice != null && !guard.notice.isEmpty()) {
            sb.append("\n").append(guard.notice);
        } else if (!guard.lastProbe.isEmpty()) {
            sb.append("\n").append(guard.lastProbe);
        }
        if (guard.cooldownUntil > System.currentTimeMillis()) {
            sb.append("\n连续失败，暂停中：还剩 ")
              .append((guard.cooldownUntil - System.currentTimeMillis()) / 1000).append(" 秒");
        }
        if (prefs.notifyOn() && notificationsBlocked()) {
            sb.append("\n").append("通知被系统关掉了：常驻通知不会显示，建议去设置里打开，或改用「无通知」模式");
        }
        if (rebootedThisLaunch && prefs.enabled() && System.currentTimeMillis() < rebootNoticeUntil) {
            sb.append("\n").append("手机重启过：若系统拦截了自启动，守护可能没自动起来——"
                    + "已尝试恢复，若仍显示停止请手动点「开始守护」");
        }
        tvSub.setText(sb.toString());

        long nowMs = System.currentTimeMillis();
        if (nowMs - lastNetUiMs > 3000) {
            lastNetUiMs = nowMs;
            android.net.Network wn = Net.wifi(this);
            String gw = Net.gateway(this, wn);
            String nets0 = prefs.campusNets();
            cachedNets = nets0;
            boolean marked0 = !nets0.trim().isEmpty();
            boolean inCampus0 = Net.prefixMatches(gw, nets0);
            cachedInCampus = wn != null && marked0 && inCampus0;
            if (wn == null) {
                cachedNetColor = AMBER;
                cachedNetLine = "当前网络：未连接 WiFi";
            } else if (!marked0) {
                cachedNetColor = AMBER;
                cachedNetLine = "当前网络：尚未标记（点下面按钮，把这里设为校园网）";
            } else if (inCampus0) {
                cachedNetColor = GREEN;
                cachedNetLine = "当前网络：校园网 ✓";
            } else {
                cachedNetColor = GRAY;
                cachedNetLine = "当前网络：不是校园网（不会自动重连）";
            }
        }
        tvNet.setTextColor(cachedNetColor);
        setTxt(tvNet, cachedNetLine);
        String nets = cachedNets;
        boolean marked = !nets.trim().isEmpty();
        btnMark.setText(cachedInCampus
                ? "取消标记（这个网络不让它自动重连）"
                : "标记当前网络为校园网");
        setTxt(tvNets, "已识别的校园网：" + (marked ? nets : "（还没标记）"));
        if (tvAcctHint != null) {
            if (prefs.user().isEmpty()) {
                tvAcctHint.setText("还没填账号，填好并保存后才能自动认证");
            } else if (prefs.passwordBroken()) {
                tvAcctHint.setText("密码已失效（重装或系统重置后需重新输入一次）");
            } else if (prefs.password().isEmpty()) {
                tvAcctHint.setText("密码为空，请填写后保存");
            } else if (prefs.host().trim().isEmpty()) {
                tvAcctHint.setText("认证服务器为空，无法自动认证（填校园网认证页那个 IP）");
            } else {
                tvAcctHint.setText("");
            }
        }
        setTxt(tvToday, "今日断网 " + guard.todayDown + " 次");
        setTxt(tvChecks, "累计检测 " + guard.checks + " 次");
        setTxt(tvLastDown, "上次断网 " + (guard.lastDown.isEmpty() ? "—" : guard.lastDown));
        setTxt(tvLastUp, "上次恢复 " + (guard.lastUp.isEmpty() ? "—" : guard.lastUp));

        int ver = Logs.version();
        if (ver != lastLogVer) {
            lastLogVer = ver;
            List<String> lines = Logs.tail();
            int show = logOpen ? Math.min(lines.size(), 300) : Math.min(lines.size(), 3);
            StringBuilder lb = new StringBuilder();
            for (int i = lines.size() - show; i < lines.size(); i++) lb.append(lines.get(i)).append("\n");
            tvLog.setText(lb.toString());
            logScroll.post(new Runnable() { public void run() { logScroll.fullScroll(View.FOCUS_DOWN); } });
        }
    }
}
