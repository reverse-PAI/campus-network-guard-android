
package com.cng.guard;

import android.net.Network;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class Srun {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final String ALPHA = "LVoJPiCN2R8G90yg+hmFHuacZ1OWMnrsSTXkYpUq/3dlbfKwv6xztjI7DeBE45QA";
    private static final String STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private Srun() { }

    

    private static int[] sBytes(byte[] msg, boolean withLen) {
        int words = msg.length / 4 + ((msg.length % 4) != 0 ? 1 : 0);
        int[] out = new int[words + (withLen ? 1 : 0)];
        int i = 0;
        for (int off = 0; off < msg.length; off += 4) {
            int b0 = msg[off] & 0xFF;
            int b1 = (off + 1 < msg.length) ? (msg[off + 1] & 0xFF) : 0;
            int b2 = (off + 2 < msg.length) ? (msg[off + 2] & 0xFF) : 0;
            int b3 = (off + 3 < msg.length) ? (msg[off + 3] & 0xFF) : 0;
            out[i++] = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
        }
        if (withLen) out[i] = msg.length;
        return out;
    }

    static byte[] xencode(byte[] msg, byte[] key) {
        if (msg == null || msg.length == 0) return new byte[0];
        int[] v = sBytes(msg, true);
        int[] k = sBytes(key, false);
        if (k.length < 4) {
            int[] k2 = new int[4];
            System.arraycopy(k, 0, k2, 0, k.length);
            k = k2;
        }
        int n = v.length - 1;
        int z = v[n];
        int y = v[0];
        int d = 0;
        int c = 0x86014019 | 0x183639A0;
        int q = 6 + 52 / (n + 1);
        int p;
        while (q-- > 0) {
            d = d + c;
            int e = (d >>> 2) & 3;
            p = 0;
            while (p < n) {
                y = v[p + 1];
                int m = (z >>> 5) ^ (y << 2);
                m = m + ((y >>> 3) ^ (z << 4) ^ (d ^ y));
                m = m + (k[(p & 3) ^ e] ^ z);
                z = v[p] + m;
                v[p] = z;
                p++;
            }
            y = v[0];
            int m = (z >>> 5) ^ (y << 2);
            m = m + ((y >>> 3) ^ (z << 4) ^ (d ^ y));
            m = m + (k[(p & 3) ^ e] ^ z);
            z = v[n] + m;
            v[n] = z;
        }
        byte[] out = new byte[v.length * 4];
        for (int i = 0; i < v.length; i++) {
            out[i * 4] = (byte) (v[i] & 0xFF);
            out[i * 4 + 1] = (byte) ((v[i] >>> 8) & 0xFF);
            out[i * 4 + 2] = (byte) ((v[i] >>> 16) & 0xFF);
            out[i * 4 + 3] = (byte) ((v[i] >>> 24) & 0xFF);
        }
        return out;
    }

    static String b64Custom(byte[] data) {
        String s = Base64.encodeToString(data, Base64.NO_WRAP);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int idx = STD.indexOf(ch);
            sb.append(idx >= 0 ? ALPHA.charAt(idx) : ch);
        }
        return sb.toString();
    }

    

    static String hmacMd5(String token, String pwd) throws Exception {
        Mac mac = Mac.getInstance("HmacMD5");
        mac.init(new SecretKeySpec(token.getBytes("UTF-8"), "HmacMD5"));
        return hex(mac.doFinal(pwd.getBytes("UTF-8")));
    }

    static String sha1Hex(String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return hex(md.digest(text.getBytes("UTF-8")));
    }

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    static String jstr(String s) {
        if (s == null) s = "";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        String h = Integer.toHexString(ch);
                        sb.append("\\u");
                        for (int pad = h.length(); pad < 4; pad++) sb.append('0');
                        sb.append(h);
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.append('"').toString();
    }

    static String infoField(String user, String pwd, String ip, String acid, String token) throws Exception {
        String raw = "{\"username\":" + jstr(user)
                + ",\"password\":" + jstr(pwd)
                + ",\"ip\":" + jstr(ip)
                + ",\"acid\":" + jstr(acid)
                + ",\"enc_ver\":\"srun_bx1\"}";
        return "{SRBX1}" + b64Custom(xencode(raw.getBytes("UTF-8"), token.getBytes("UTF-8")));
    }

    static String chksum(String token, String user, String pwdHash, String acid, String ip,
                         int n, int type, String info) throws Exception {
        String s = token + user + token + pwdHash + token + acid + token + ip
                + token + n + token + type + token + info;
        return sha1Hex(s);
    }

    

    static String httpGet(Network net, String url, int timeoutSec) throws Exception {
        URL u = new URL(url);
        HttpURLConnection c = (HttpURLConnection) (net != null ? net.openConnection(u) : u.openConnection());
        try {
            c.setConnectTimeout(timeoutSec * 1000);
            c.setReadTimeout(timeoutSec * 1000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            c.setRequestProperty("Cache-Control", "no-cache");
            int code = c.getResponseCode();
            InputStream in;
            try {
                in = c.getInputStream();
            } catch (Exception e) {
                in = c.getErrorStream();
            }
            if (in == null) return "";
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            in.close();
            String body = new String(bo.toByteArray(), "UTF-8");
            if (code >= 400 && body.isEmpty()) throw new java.io.IOException("HTTP " + code);
            return body;
        } finally {
            c.disconnect();
        }
    }

    static JSONObject jsonp(String text) throws Exception {
        int i = text.indexOf('{');
        int j = text.lastIndexOf('}');
        if (i < 0 || j < i) {
            String head = text.length() > 120 ? text.substring(0, 120) : text;
            throw new IllegalStateException("响应不是 JSON: " + head);
        }
        return new JSONObject(text.substring(i, j + 1));
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    static JSONObject challenge(Network net, String host, int port, String user, String ip) throws Exception {
        String url = "http://" + host + ":" + port + "/cgi-bin/get_challenge?callback=cb"
                + "&username=" + enc(user)
                + "&ip=" + enc(ip)
                + "&_=" + System.currentTimeMillis();
        return jsonp(httpGet(net, url, 8));
    }

    static JSONObject login(Network net, String host, int port, String user, String pwd,
                            String acid, String localIp) throws Exception {
        JSONObject ch = challenge(net, host, port, user, localIp);
        String token = ch.optString("challenge", "");
        if (token.isEmpty()) {
            throw new IllegalStateException("未取得 token：" + ch.optString("error_msg", ch.optString("error", "未知")));
        }
        String ip = localIp;
        String clientIp = ch.optString("client_ip", "");
        if (!clientIp.isEmpty() && !clientIp.equals(ip)) {
            ip = clientIp;
            try {
                JSONObject ch2 = challenge(net, host, port, user, ip);
                String t2 = ch2.optString("challenge", "");
                if (!t2.isEmpty()) token = t2;
            } catch (Exception ignored) { }
        }
        String pwdHash = hmacMd5(token, pwd);
        String info = infoField(user, pwd, ip, acid, token);
        String chk = chksum(token, user, pwdHash, acid, ip, 200, 1, info);

        StringBuilder sb = new StringBuilder("http://");
        sb.append(host).append(':').append(port).append("/cgi-bin/srun_portal?callback=cb&action=login");
        sb.append("&username=").append(enc(user));
        sb.append("&password=").append(enc("{MD5}" + pwdHash));
        sb.append("&os=").append(enc("Windows NT"));
        sb.append("&name=").append(enc("Windows"));
        sb.append("&double_stack=0");
        sb.append("&chksum=").append(enc(chk));
        sb.append("&info=").append(enc(info));
        sb.append("&ac_id=").append(enc(acid));
        sb.append("&ip=").append(enc(ip));
        sb.append("&n=200&type=1&_=").append(System.currentTimeMillis());

        JSONObject res = jsonp(httpGet(net, sb.toString(), 10));
        String ecode = res.optString("ecode", "");
        boolean ok = "0".equals(ecode)
                || "ok".equals(res.optString("error", ""))
                || "ip_already_online_error".equals(ecode);
        res.put("ok", ok);
        res.put("ip", ip);
        return res;
    }
}
