package com.cfm.manager;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class RootShell {
    private RootShell() {}

    public static final class Result {
        public final int code;
        public final String out;
        public final String err;
        Result(int code, String out, String err) {
            this.code = code;
            this.out = out == null ? "" : out.trim();
            this.err = err == null ? "" : err.trim();
        }
        public boolean ok() { return code == 0; }
        public String combined() {
            if (err.isEmpty()) return out;
            if (out.isEmpty()) return err;
            return out + "\n" + err;
        }
    }

    public static boolean hasRoot() {
        Result r = run("id", 6);
        return r.ok() && r.out.contains("uid=0");
    }

    public static Result run(String command) {
        return run(command, 20);
    }

    public static Result run(String command, int timeoutSeconds) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", command).start();
            final Process proc = p;
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread t1 = pump(proc.getInputStream(), stdout);
            Thread t2 = pump(proc.getErrorStream(), stderr);
            boolean done = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!done) {
                proc.destroy();
                try { proc.destroyForcibly(); } catch (Throwable ignored) {}
                return new Result(124, stdout.toString("UTF-8"), "Command timed out");
            }
            t1.join(800);
            t2.join(800);
            return new Result(proc.exitValue(), stdout.toString("UTF-8"), stderr.toString("UTF-8"));
        } catch (Throwable e) {
            return new Result(127, "", e.getMessage());
        } finally {
            if (p != null) p.destroy();
        }
    }

    private static Thread pump(final InputStream in, final ByteArrayOutputStream out) {
        Thread t = new Thread(() -> {
            try {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) >= 0) out.write(b, 0, n);
            } catch (Throwable ignored) {}
        });
        t.start();
        return t;
    }

    public static String readFile(String path) {
        Result r = run("cat " + shQuote(path), 8);
        return r.ok() ? r.out : "";
    }

    public static boolean writeFile(String path, String content) {
        String b64 = Base64.encodeToString(content.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String cmd = "BB=/data/adb/magisk/busybox; " +
                "if [ -x \"$BB\" ]; then echo " + shQuote(b64) + " | \"$BB\" base64 -d > " + shQuote(path) + "; " +
                "else echo " + shQuote(b64) + " | base64 -d > " + shQuote(path) + "; fi";
        return run(cmd, 10).ok();
    }

    public static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
