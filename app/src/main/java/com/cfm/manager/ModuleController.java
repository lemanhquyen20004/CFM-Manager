package com.cfm.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ModuleController {
    public static final String DATA = "/data/clash";
    public static final String CONFIG = DATA + "/clash.config";
    public static final String TEMPLATE = DATA + "/template";
    public static final String PACKAGES = DATA + "/packages.list";
    public static final String LOG = DATA + "/run/run.logs";
    public static final String SERVICE = DATA + "/scripts/clash.service";
    public static final String IPTABLES = DATA + "/scripts/clash.iptables";
    public static final String TOOL = DATA + "/scripts/clash.tool";
    public static final String KERNEL = DATA + "/kernel/clash";

    private ModuleController() {}

    public static boolean installed() {
        return RootShell.run("[ -f " + RootShell.shQuote(SERVICE) + " ] && [ -f " + RootShell.shQuote(CONFIG) + " ]", 5).ok();
    }

    public static boolean running() {
        RootShell.Result r = RootShell.run("pidof clash 2>/dev/null", 5);
        return r.ok() && !r.out.isEmpty();
    }

    public static String pid() {
        RootShell.Result r = RootShell.run("pidof clash 2>/dev/null", 5);
        return r.ok() ? r.out : "";
    }

    public static RootShell.Result start() {
        String cmd = "chmod 755 " + SERVICE + " " + IPTABLES + " " + TOOL + "; " +
                SERVICE + " -s; rc=$?; " +
                "if [ $rc -eq 0 ] && [ -f /data/clash/run/clash.pid ]; then " + IPTABLES + " -s; iptables -N CFM_APP_BLOCK 2>/dev/null || true; iptables -D FORWARD -j CFM_APP_BLOCK 2>/dev/null || true; iptables -I FORWARD 1 -j CFM_APP_BLOCK; fi; exit $rc";
        return RootShell.run(cmd, 45);
    }

    public static RootShell.Result stop() {
        String cmd = IPTABLES + " -k >/dev/null 2>&1; " + SERVICE + " -k; exit 0";
        return RootShell.run(cmd, 30);
    }

    public static RootShell.Result restart() {
        String cmd = IPTABLES + " -k >/dev/null 2>&1; " + SERVICE + " -k >/dev/null 2>&1; " +
                "sleep 1; " + SERVICE + " -s; rc=$?; " +
                "if [ $rc -eq 0 ] && [ -f /data/clash/run/clash.pid ]; then " + IPTABLES + " -s; iptables -N CFM_APP_BLOCK 2>/dev/null || true; iptables -D FORWARD -j CFM_APP_BLOCK 2>/dev/null || true; iptables -I FORWARD 1 -j CFM_APP_BLOCK; fi; exit $rc";
        return RootShell.run(cmd, 55);
    }

    public static RootShell.Result reapplyFirewall() {
        if (!running()) return new RootShell.Result(0, "Clash is stopped", "");
        String cmd = IPTABLES + " -k >/dev/null 2>&1; " + TOOL + " -f >/dev/null 2>&1; " + IPTABLES + " -s";
        return RootShell.run(cmd, 35);
    }

    public static String kernelVersion() {
        RootShell.Result r = RootShell.run(KERNEL + " -v 2>/dev/null | head -2", 8);
        return r.ok() ? r.out : "Không đọc được";
    }

    public static String getConfigValue(String key) {
        String text = RootShell.readFile(CONFIG);
        if (text.isEmpty()) return "";
        String prefix = key + "=";
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (!t.startsWith(prefix)) continue;
            String v = t.substring(prefix.length()).trim();
            if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
                v = v.substring(1, v.length()-1);
            }
            return v;
        }
        return "";
    }

    public static boolean setConfigValue(String key, String value, boolean quote) {
        String text = RootShell.readFile(CONFIG);
        if (text.isEmpty()) return false;
        String replacement = key + "=" + (quote ? "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : value);
        StringBuilder out = new StringBuilder();
        boolean found = false;
        for (String line : text.split("\\r?\\n", -1)) {
            String t = line.trim();
            if (t.startsWith(key + "=")) {
                out.append(replacement);
                found = true;
            } else out.append(line);
            out.append('\n');
        }
        if (!found) out.append(replacement).append('\n');
        return RootShell.writeFile(CONFIG, out.toString());
    }

    public static String getYamlValue(String key) {
        String text = RootShell.readFile(TEMPLATE);
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith(key + ":")) return t.substring((key + ":").length()).trim().replace("\"", "");
        }
        return "";
    }

    public static String getTunEnabled() {
        String text = RootShell.readFile(TEMPLATE);
        String[] lines = text.split("\\r?\\n");
        boolean inTun = false;
        int tunIndent = -1;
        for (String line : lines) {
            if (line.trim().equals("tun:")) {
                inTun = true;
                tunIndent = leadingSpaces(line);
                continue;
            }
            if (inTun) {
                if (!line.trim().isEmpty() && leadingSpaces(line) <= tunIndent) break;
                String t = line.trim();
                if (t.startsWith("enable:")) return t.substring(7).trim();
            }
        }
        return "false";
    }

    private static int leadingSpaces(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == ' ') n++;
        return n;
    }

    public static boolean setTunEnabled(boolean enabled) {
        String text = RootShell.readFile(TEMPLATE);
        if (text.isEmpty()) return false;
        String[] lines = text.split("\\r?\\n", -1);
        boolean inTun = false;
        int tunIndent = -1;
        boolean changed = false;
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line.trim().equals("tun:")) {
                inTun = true;
                tunIndent = leadingSpaces(line);
            } else if (inTun && !line.trim().isEmpty() && leadingSpaces(line) <= tunIndent) {
                inTun = false;
            }
            if (inTun && line.trim().startsWith("enable:")) {
                int spaces = leadingSpaces(line);
                StringBuilder p = new StringBuilder();
                for (int i=0;i<spaces;i++) p.append(' ');
                line = p + "enable: " + (enabled ? "true" : "false");
                changed = true;
            }
            out.append(line).append('\n');
        }
        return changed && RootShell.writeFile(TEMPLATE, out.toString());
    }

    public static Set<String> readPackageFilter() {
        String text = RootShell.readFile(PACKAGES);
        Set<String> set = new LinkedHashSet<>();
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) set.add(t);
        }
        return set;
    }

    public static boolean writePackageFilter(Set<String> packages) {
        List<String> list = new ArrayList<>(packages);
        Collections.sort(list);
        StringBuilder b = new StringBuilder();
        for (String s : list) b.append(s).append('\n');
        return RootShell.writeFile(PACKAGES, b.toString());
    }

    public static RootShell.Result updateSubscription() {
        return RootShell.run(TOOL + " -o", 40);
    }

    public static String getSubscriptionUrl() {
        return getConfigValue("Subcript_url");
    }

    public static boolean setSubscriptionUrl(String url) {
        return setConfigValue("Subcript_url", url, true);
    }

    public static String readLog(int lines) {
        RootShell.Result r = RootShell.run("tail -n " + Math.max(20, Math.min(lines, 1000)) + " " + LOG + " 2>/dev/null", 8);
        return r.ok() ? r.out : r.combined();
    }

    public static void clearLog() {
        RootShell.run(": > " + LOG, 6);
    }

    public static String hotspotArp() {
        RootShell.Result r = RootShell.run("cat /proc/net/arp 2>/dev/null", 5);
        return r.ok() ? r.out : "";
    }

    public static boolean installManagerFirewallChain() {
        String cmd = "iptables -N CFM_APP_BLOCK 2>/dev/null || true; " +
                "iptables -C FORWARD -j CFM_APP_BLOCK 2>/dev/null || iptables -I FORWARD 1 -j CFM_APP_BLOCK";
        return RootShell.run(cmd, 8).ok();
    }

    public static boolean setMacBlocked(String mac, boolean blocked) {
        if (mac == null || !mac.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) return false;
        installManagerFirewallChain();
        String m = mac.toUpperCase(Locale.US);
        String rule = "-m mac --mac-source " + m + " -j DROP";
        String cmd;
        if (blocked) {
            cmd = "iptables -C CFM_APP_BLOCK " + rule + " 2>/dev/null || iptables -A CFM_APP_BLOCK " + rule;
        } else {
            cmd = "while iptables -C CFM_APP_BLOCK " + rule + " 2>/dev/null; do iptables -D CFM_APP_BLOCK " + rule + "; done";
        }
        return RootShell.run(cmd, 8).ok();
    }

    public static boolean isMacBlocked(String mac) {
        if (mac == null || !mac.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) return false;
        return RootShell.run("iptables -C CFM_APP_BLOCK -m mac --mac-source " + mac.toUpperCase(Locale.US) + " -j DROP 2>/dev/null", 5).ok();
    }
    public static boolean setYamlTopValue(String key, String value) {
        String text = RootShell.readFile(TEMPLATE);
        if (text.isEmpty()) return false;
        StringBuilder out = new StringBuilder();
        boolean found = false;
        for (String line : text.split("\\r?\\n", -1)) {
            if (!line.startsWith(" ") && !line.startsWith("\t") && line.trim().startsWith(key + ":") && !line.trim().startsWith("#")) {
                line = key + ": " + value;
                found = true;
            }
            out.append(line).append('\n');
        }
        if (!found) out.append(key).append(": ").append(value).append('\n');
        return RootShell.writeFile(TEMPLATE, out.toString());
    }

    public static boolean setTunValue(String key, String value) {
        String text = RootShell.readFile(TEMPLATE);
        if (text.isEmpty()) return false;
        String[] lines = text.split("\\r?\\n", -1);
        boolean inTun = false;
        int tunIndent = -1;
        boolean changed = false;
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line.trim().equals("tun:")) {
                inTun = true;
                tunIndent = leadingSpaces(line);
            } else if (inTun && !line.trim().isEmpty() && leadingSpaces(line) <= tunIndent) {
                inTun = false;
            }
            if (inTun && line.trim().startsWith(key + ":") && !line.trim().startsWith("#")) {
                int spaces = leadingSpaces(line);
                StringBuilder pre = new StringBuilder();
                for (int i=0;i<spaces;i++) pre.append(' ');
                line = pre + key + ": " + value;
                changed = true;
            }
            out.append(line).append('\n');
        }
        return changed && RootShell.writeFile(TEMPLATE, out.toString());
    }

    public static boolean setYamlSectionValue(String section, String key, String value) {
        String text = RootShell.readFile(TEMPLATE);
        if (text.isEmpty()) return false;
        String[] lines = text.split("\\r?\\n", -1);
        boolean inSection = false;
        int sectionIndent = -1;
        boolean changed = false;
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (line.trim().equals(section + ":") && !line.trim().startsWith("#")) {
                inSection = true;
                sectionIndent = leadingSpaces(line);
            } else if (inSection && !line.trim().isEmpty() && leadingSpaces(line) <= sectionIndent) {
                inSection = false;
            }
            if (inSection && line.trim().startsWith(key + ":") && !line.trim().startsWith("#")) {
                int spaces = leadingSpaces(line);
                StringBuilder pre = new StringBuilder();
                for (int i=0;i<spaces;i++) pre.append(' ');
                line = pre + key + ": " + value;
                changed = true;
            }
            out.append(line).append('\n');
        }
        return changed && RootShell.writeFile(TEMPLATE, out.toString());
    }

    public static void backupAll() {
        RootShell.run("cp -f " + CONFIG + " " + CONFIG + ".cfm.bak 2>/dev/null; " +
                "cp -f " + TEMPLATE + " " + TEMPLATE + ".cfm.bak 2>/dev/null; " +
                "cp -f " + PACKAGES + " " + PACKAGES + ".cfm.bak 2>/dev/null", 8);
    }

    public static RootShell.Result restoreBackup() {
        String cmd = "[ -f " + CONFIG + ".cfm.bak ] && cp -f " + CONFIG + ".cfm.bak " + CONFIG + "; " +
                "[ -f " + TEMPLATE + ".cfm.bak ] && cp -f " + TEMPLATE + ".cfm.bak " + TEMPLATE + "; " +
                "[ -f " + PACKAGES + ".cfm.bak ] && cp -f " + PACKAGES + ".cfm.bak " + PACKAGES + "; " +
                IPTABLES + " -k >/dev/null 2>&1; " + SERVICE + " -k >/dev/null 2>&1; sleep 1; " + SERVICE + " -s; rc=$?; " +
                "if [ $rc -eq 0 ] && [ -f /data/clash/run/clash.pid ]; then " + IPTABLES + " -s; fi; exit $rc";
        return RootShell.run(cmd, 55);
    }

}
