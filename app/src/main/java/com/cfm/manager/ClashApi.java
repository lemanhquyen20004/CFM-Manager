package com.cfm.manager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ClashApi {
    private static final String BASE = "http://127.0.0.1:9090";
    private ClashApi() {}

    public static final class Resp {
        public final int code;
        public final String body;
        Resp(int code, String body) { this.code = code; this.body = body == null ? "" : body; }
        public boolean ok() { return code >= 200 && code < 300; }
    }

    private static String secret() {
        String text = RootShell.readFile(ModuleController.TEMPLATE);
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("#")) continue;
            if (t.startsWith("secret:")) {
                String s = t.substring(7).trim();
                if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) s = s.substring(1, s.length()-1);
                return s;
            }
        }
        return "";
    }

    public static Resp request(String method, String path, String json) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(BASE + path).openConnection();
            c.setConnectTimeout(1800);
            c.setReadTimeout(2500);
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/json");
            String sec = secret();
            if (!sec.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + sec);
            if (json != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                byte[] b = json.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = c.getOutputStream()) { os.write(b); }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }
            }
            return new Resp(code, sb.toString().trim());
        } catch (Throwable e) {
            return new Resp(0, e.getMessage());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static Resp get(String path) { return request("GET", path, null); }

    public static boolean setMode(String mode) {
        return request("PATCH", "/configs", "{\"mode\":\"" + mode + "\"}").ok();
    }

    public static boolean selectProxy(String group, String name) {
        try {
            String p = "/proxies/" + URLEncoder.encode(group, "UTF-8").replace("+", "%20");
            JSONObject body = new JSONObject();
            body.put("name", name);
            return request("PUT", p, body.toString()).ok();
        } catch (Throwable e) {
            return false;
        }
    }
}
