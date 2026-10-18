
package com.cng.guard;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class Logs {

    static final int MAX = 400;
    private static final ArrayList<String> ring = new ArrayList<String>();
    private static volatile int version;
    private static File file;

    private Logs() { }

    static synchronized void init(Context c) {
        if (file != null) return;
        try {
            java.io.File dir = c.getApplicationContext().getFilesDir();
            if (dir != null) file = new File(dir, "guard.log");
        } catch (Throwable ignored) {
            
        }
    }

    static synchronized java.util.List<String> tail() {
        return new ArrayList<String>(ring);
    }

    static int version() { return version; }

    static synchronized void clear() {
        ring.clear();
        version++;
        if (file != null && file.exists()) {
            try { file.delete(); } catch (Exception ignored) { }
            File old = new File(file.getParentFile(), "guard.log.1");
            if (old.exists()) {
                try { old.delete(); } catch (Exception ignored) { }
            }
        }
    }

    static void i(String m) { add("INFO", m); }
    static void w(String m) { add("WARN", m); }
    static void e(String m) { add("ERROR", m); }

    private static synchronized void add(String level, String msg) {
        String hm = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        ring.add(hm + " " + msg);
        version++;
        while (ring.size() > MAX) ring.remove(0);
        android.util.Log.i("CNG", msg);
        if (file == null) return;
        try {
            if (file.length() > 256 * 1024) {
                File old = new File(file.getParentFile(), "guard.log.1");
                if (old.exists()) old.delete();
                file.renameTo(old);
            }
            String full = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            FileWriter w = new FileWriter(file, true);
            try {
                w.write(full + " [" + level + "] " + msg + "\n");
            } finally {
                w.close();
            }
        } catch (Exception ignored) { }
    }
}