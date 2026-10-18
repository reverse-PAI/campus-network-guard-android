
package com.cng.guard;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class Prefs {

    static final String DEFAULT_USER = "";
    static final String DEFAULT_PASS = "";
    static final String DEFAULT_HOST = "192.168.199.22";
    static final String DEFAULT_ACID = "1";

    static final int MIN_PERIOD = 30, MAX_PERIOD = 3600;
    static final int MIN_INTERVAL = 1, MAX_INTERVAL = 600;
    static final int MIN_FAIL = 1, MAX_FAIL = 99;
    static final int MIN_COOLDOWN = 0, MAX_COOLDOWN = 86400;

    private static final String ALIAS = "cng_guard_key";
    private static final String SP = "cng_guard";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(SP, Context.MODE_PRIVATE);
        if (!sp.getBoolean("init", false)) {
            SharedPreferences.Editor e = sp.edit();
            e.putString("user", DEFAULT_USER);
            e.putString("host", DEFAULT_HOST);
            e.putString("acid", DEFAULT_ACID);
            e.putInt("period", 60);
            e.putInt("min_interval", 8);
            e.putInt("max_fail", 5);
            e.putInt("cooldown", 600);
            e.putBoolean("only_campus", true);
            e.putBoolean("notify", true);
            e.putBoolean("init", true);
            e.apply();
            setPassword(DEFAULT_PASS);
        }
    }

    String user() { return sp.getString("user", ""); }
    void setUser(String v) { sp.edit().putString("user", v == null ? "" : v.trim()).apply(); }

    String host() { return sp.getString("host", ""); }
    void setHost(String v) { sp.edit().putString("host", v == null ? "" : v.trim()).apply(); }

    String acid() {
        String v = sp.getString("acid", "1");
        return (v == null || v.trim().isEmpty()) ? "1" : v.trim();   
    }
    void setAcid(String v) {
        sp.edit().putString("acid", (v == null || v.trim().isEmpty()) ? "1" : v.trim()).apply();
    }

    
    String campusNets() { return sp.getString("campus_nets", ""); }
    void setCampusNets(String v) { sp.edit().putString("campus_nets", v == null ? "" : v.trim()).apply(); }

    int periodSec() { return clamp(sp.getInt("period", 60), MIN_PERIOD, MAX_PERIOD); }
    void setPeriodSec(int v) { sp.edit().putInt("period", clamp(v, MIN_PERIOD, MAX_PERIOD)).apply(); }

    int minIntervalSec() { return clamp(sp.getInt("min_interval", 8), MIN_INTERVAL, MAX_INTERVAL); }
    void setMinIntervalSec(int v) { sp.edit().putInt("min_interval", clamp(v, MIN_INTERVAL, MAX_INTERVAL)).apply(); }

    int maxFail() { return clamp(sp.getInt("max_fail", 5), MIN_FAIL, MAX_FAIL); }
    void setMaxFail(int v) { sp.edit().putInt("max_fail", clamp(v, MIN_FAIL, MAX_FAIL)).apply(); }

    int cooldownSec() { return clamp(sp.getInt("cooldown", 600), MIN_COOLDOWN, MAX_COOLDOWN); }
    void setCooldownSec(int v) { sp.edit().putInt("cooldown", clamp(v, MIN_COOLDOWN, MAX_COOLDOWN)).apply(); }

    boolean onlyCampus() { return sp.getBoolean("only_campus", true); }
    void setOnlyCampus(boolean v) { sp.edit().putBoolean("only_campus", v).apply(); }

    boolean notifyOn() { return sp.getBoolean("notify", true); }
    void setNotifyOn(boolean v) { sp.edit().putBoolean("notify", v).apply(); }

    String statDate() { return sp.getString("stat_date", ""); }
    int statTodayDown() { return sp.getInt("stat_down", 0); }
    int statChecks() { return sp.getInt("stat_checks", 0); }
    String statLastDown() { return sp.getString("stat_last_down", ""); }
    String statLastUp() { return sp.getString("stat_last_up", ""); }

    void saveStats(String date, int down, int checks, String lastDown, String lastUp) {
        sp.edit().putString("stat_date", date).putInt("stat_down", down).putInt("stat_checks", checks)
          .putString("stat_last_down", lastDown).putString("stat_last_up", lastUp).apply();
    }

    
    boolean passwordBroken() { return sp.getBoolean("pwd_broken", false); }

    
    long bootTime() { return sp.getLong("boot_time", 0L); }
    void setBootTime(long v) { sp.edit().putLong("boot_time", v).apply(); }

    boolean enabled() { return sp.getBoolean("enabled", false); }
    void setEnabled(boolean v) { sp.edit().putBoolean("enabled", v).apply(); }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    String password() {
        String enc = sp.getString("pwd", "");
        if (enc == null || enc.isEmpty()) return "";
        try {
            int i = enc.indexOf(':');
            byte[] iv = Base64.decode(enc.substring(0, i), Base64.NO_WRAP);
            byte[] ct = Base64.decode(enc.substring(i + 1), Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), "UTF-8");
        } catch (Exception e) {
            sp.edit().putBoolean("pwd_broken", true).apply();
            Logs.w("密码解密失败（重装或系统重置后密钥失效，需重新输入）");
            return "";
        }
    }

    boolean setPassword(String plain) {
        if (plain == null) plain = "";
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
            String enc = Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(ct, Base64.NO_WRAP);
            sp.edit().putString("pwd", enc).putBoolean("pwd_broken", false).apply();
            return true;
        } catch (Exception e) {
            Logs.e("密码保存失败：" + e);
            return false;
        }
    }

    String portalHost() {
        String h = host();
        int i = h.indexOf('/');
        if (i >= 0) h = h.substring(0, i);
        if (h.startsWith("http://")) h = h.substring(7);
        if (h.startsWith("https://")) h = h.substring(8);
        return h.trim();
    }

    int portalPort() {
        String h = portalHost();
        int i = h.lastIndexOf(':');
        if (i > 0) {
            try { return Integer.parseInt(h.substring(i + 1)); } catch (Exception ignored) { }
        }
        return 80;
    }

    String portalName() {
        String h = portalHost();
        int i = h.lastIndexOf(':');
        return i > 0 ? h.substring(0, i) : h;
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }
}