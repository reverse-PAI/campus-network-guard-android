
package com.cng.guard;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;

public final class Net {

    private static final String[] PROBES = {
            "http://connect.rom.miui.com/generate_204",
            "http://www.gstatic.com/generate_204",
            "http://www.msftconnecttest.com/connecttest.txt"
    };
    private static final String UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36";

    private Net() { }

    static ConnectivityManager cm(Context c) {
        return (ConnectivityManager) c.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    static Network wifi(Context c) {
        ConnectivityManager m = cm(c);
        if (m == null) return null;
        Network[] all = m.getAllNetworks();
        if (all == null) return null;
        for (Network n : all) {
            NetworkCapabilities nc = m.getNetworkCapabilities(n);
            if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return n;
        }
        return null;
    }

    static String ipv4(Context c, Network n) {
        ConnectivityManager m = cm(c);
        if (m == null || n == null) return "";
        LinkProperties lp = m.getLinkProperties(n);
        if (lp == null) return "";
        for (LinkAddress la : lp.getLinkAddresses()) {
            InetAddress a = la.getAddress();
            if (a instanceof Inet4Address) {
                String s = a.getHostAddress();
                if (s != null && !s.startsWith("127.")) return s;
            }
        }
        return "";
    }

    static boolean validated(Context c) {
        ConnectivityManager m = cm(c);
        if (m == null) return false;
        Network active = m.getActiveNetwork();
        if (active == null) return false;
        NetworkCapabilities nc = m.getNetworkCapabilities(active);
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    
    
    static String subnetOf(String ip) {
        if (ip == null) return "";
        String[] p = ip.trim().split("\\.");
        if (p.length < 2) return "";
        try {
            Integer.parseInt(p[0]);
            Integer.parseInt(p[1]);
        } catch (Exception e) {
            return "";
        }
        return p[0] + "." + p[1] + ".";
    }

    
    static boolean prefixMatches(String value, String list) {
        if (value == null || list == null) return false;
        String v = value.trim();
        if (v.isEmpty()) return false;
        for (String raw : list.split("[,，\\s]+")) {
            String pre = raw.trim();
            if (!pre.isEmpty() && v.startsWith(pre)) return true;
        }
        return false;
    }

    
    static boolean tcp(Network n, String host, int port, int timeoutMs) {
        if (host == null || host.isEmpty()) return false;
        Socket s = null;
        try {
            s = (n != null) ? n.getSocketFactory().createSocket() : new Socket();
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (s != null) try { s.close(); } catch (Exception ignored) { }
        }
    }

    
    static String gateway(Context c, Network n) {
        ConnectivityManager m = cm(c);
        if (m == null || n == null) return "";
        LinkProperties lp = m.getLinkProperties(n);
        if (lp == null) return "";
        try {
            for (android.net.RouteInfo r : lp.getRoutes()) {
                InetAddress gw = r.getGateway();
                if (gw != null && !gw.isAnyLocalAddress() && gw instanceof Inet4Address) {
                    return gw.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    
    static String probe(Network n) {
        String last = "所有探测点都失败";
        for (String url : PROBES) {
            try {
                URL u = new URL(url);
                HttpURLConnection c = (HttpURLConnection) (n != null ? n.openConnection(u) : u.openConnection());
                try {
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setInstanceFollowRedirects(false);
                    c.setRequestProperty("User-Agent", UA);
                    c.setRequestProperty("Cache-Control", "no-cache");
                    int code = c.getResponseCode();
                    InputStream in;
                    try {
                        in = c.getInputStream();
                    } catch (Exception e) {
                        in = c.getErrorStream();
                    }
                    byte[] body = new byte[0];
                    if (in != null) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] buf = new byte[2048];
                        int r;
                        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
                        in.close();
                        body = bo.toByteArray();
                    }
                    if (code == 204) return null;
                    if (url.contains("msftconnecttest")) {
                        if (new String(body, "UTF-8").contains("Microsoft Connect Test")) return null;
                    } else if (code == 200 && body.length == 0) {
                        return null;
                    }
                    last = url + " -> 被劫持/内容异常(code=" + code + ")";
                } finally {
                    c.disconnect();
                }
            } catch (Exception e) {
                last = url + " -> " + e.getClass().getSimpleName() + ":" + e.getMessage();
            }
        }
        return last;
    }
}