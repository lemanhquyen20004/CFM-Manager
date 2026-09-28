package com.cfm.manager;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight self-update helper.
 *
 * Remote manifest format:
 * {
 *   "versionCode": 3,
 *   "versionName": "1.2.0",
 *   "apkUrl": "https://github.com/OWNER/REPO/releases/download/v1.2.0/CFM-Manager-v1.2.0.apk",
 *   "changelog": "..."
 * }
 */
public final class UpdateManager {
    private static final String PREFS = "cfm_manager_prefs";
    private static final String KEY_MANIFEST_URL = "update_manifest_url";
    private static final String KEY_PENDING_DOWNLOAD = "pending_update_download";
    public static final String DEFAULT_MANIFEST_URL = "https://raw.githubusercontent.com/lemanhquyen20004/CFM-Manager/main/update.json";

    private final Activity activity;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean receiverRegistered = false;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            long pending = prefs().getLong(KEY_PENDING_DOWNLOAD, -1L);
            if (id != pending) return;
            handleCompletedDownload(id);
        }
    };

    public UpdateManager(Activity activity) {
        this.activity = activity;
        registerReceiver();
    }

    public static String getManifestUrl(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Use the official repository automatically on first install.
        // If the user explicitly saves an empty URL, automatic checks are disabled.
        if (!p.contains(KEY_MANIFEST_URL)) return DEFAULT_MANIFEST_URL;
        return p.getString(KEY_MANIFEST_URL, "");
    }

    public static void setManifestUrl(Context context, String url) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_MANIFEST_URL, url == null ? "" : url.trim()).apply();
    }

    public void checkForUpdates(boolean manual) {
        final String manifestUrl = getManifestUrl(activity).trim();
        if (manifestUrl.isEmpty()) {
            if (manual) toast("Chưa cấu hình URL update.json trong Cài đặt");
            return;
        }

        io.execute(() -> {
            try {
                UpdateInfo info = fetchManifest(manifestUrl);
                int currentCode = BuildConfig.VERSION_CODE;
                if (info.versionCode > currentCode) {
                    activity.runOnUiThread(() -> showUpdateDialog(info));
                } else if (manual) {
                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                            .setTitle("CFM Manager")
                            .setMessage("Bạn đang dùng bản mới nhất (" + BuildConfig.VERSION_NAME + ").")
                            .setPositiveButton("OK", null)
                            .show());
                }
            } catch (Throwable e) {
                if (manual) {
                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                            .setTitle("Không kiểm tra được cập nhật")
                            .setMessage(e.getMessage() == null ? e.toString() : e.getMessage())
                            .setPositiveButton("OK", null)
                            .show());
                }
            }
        });
    }

    private UpdateInfo fetchManifest(String manifestUrl) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(manifestUrl).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "CFM-Manager/" + BuildConfig.VERSION_NAME);
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + " khi tải update.json");
            String body = readAll(c.getInputStream());
            JSONObject j = new JSONObject(body);
            int versionCode = j.optInt("versionCode", 0);
            String versionName = j.optString("versionName", "").trim();
            String apkUrl = j.optString("apkUrl", "").trim();
            String changelog = j.optString("changelog", "").trim();
            if (versionCode <= 0) throw new Exception("update.json thiếu versionCode hợp lệ");
            if (versionName.isEmpty()) versionName = String.valueOf(versionCode);
            if (apkUrl.isEmpty() || !(apkUrl.startsWith("https://") || apkUrl.startsWith("http://"))) {
                throw new Exception("update.json thiếu apkUrl hợp lệ");
            }
            return new UpdateInfo(versionCode, versionName, apkUrl, changelog);
        } finally {
            c.disconnect();
        }
    }

    private void showUpdateDialog(UpdateInfo info) {
        StringBuilder msg = new StringBuilder();
        msg.append("Có phiên bản mới: ").append(info.versionName)
                .append("\nBản hiện tại: ").append(BuildConfig.VERSION_NAME);
        if (!info.changelog.isEmpty()) msg.append("\n\n").append(info.changelog);

        new AlertDialog.Builder(activity)
                .setTitle("Có bản cập nhật mới")
                .setMessage(msg.toString())
                .setNegativeButton("Để sau", null)
                .setPositiveButton("Cập nhật", (d, w) -> downloadApk(info))
                .show();
    }

    private void downloadApk(UpdateInfo info) {
        try {
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) throw new Exception("DownloadManager không khả dụng");

            String safeVersion = info.versionName.replaceAll("[^A-Za-z0-9._-]", "_");
            String fileName = "CFM-Manager-" + safeVersion + ".apk";
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(info.apkUrl));
            req.setTitle("CFM Manager " + info.versionName);
            req.setDescription("Đang tải bản cập nhật…");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(false);
            req.setMimeType("application/vnd.android.package-archive");
            req.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName);

            long id = dm.enqueue(req);
            prefs().edit().putLong(KEY_PENDING_DOWNLOAD, id).apply();
            toast("Đang tải CFM Manager " + info.versionName);
        } catch (Throwable e) {
            showError("Không tải được APK: " + (e.getMessage() == null ? e : e.getMessage()));
        }
    }

    private void handleCompletedDownload(long id) {
        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;
        DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
        try (Cursor c = dm.query(q)) {
            if (c == null || !c.moveToFirst()) {
                showError("Không đọc được trạng thái file cập nhật");
                return;
            }
            int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                int reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                prefs().edit().remove(KEY_PENDING_DOWNLOAD).apply();
                showError("Tải APK thất bại. Mã lỗi: " + reason);
                return;
            }
        }
        installDownloadedApk(id);
    }

    private void installDownloadedApk(long id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            try {
                Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(permission);
                toast("Hãy bật Cho phép từ nguồn này, rồi quay lại app");
            } catch (Throwable e) {
                showError("Không mở được quyền cài APK: " + e.getMessage());
            }
            return;
        }

        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = dm == null ? null : dm.getUriForDownloadedFile(id);
        if (uri == null) {
            showError("Không lấy được APK đã tải");
            return;
        }
        try {
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(install);
            prefs().edit().remove(KEY_PENDING_DOWNLOAD).apply();
        } catch (Throwable e) {
            showError("Không mở được trình cài đặt: " + e.getMessage());
        }
    }

    /** Call from Activity.onResume so install continues after unknown-source permission is granted. */
    public void onResume() {
        long id = prefs().getLong(KEY_PENDING_DOWNLOAD, -1L);
        if (id <= 0) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) return;

        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;
        DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
        try (Cursor c = dm.query(q)) {
            if (c != null && c.moveToFirst()) {
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) installDownloadedApk(id);
            }
        } catch (Throwable ignored) {}
    }

    public void destroy() {
        io.shutdownNow();
        if (receiverRegistered) {
            try { activity.unregisterReceiver(downloadReceiver); } catch (Throwable ignored) {}
            receiverRegistered = false;
        }
    }

    private void registerReceiver() {
        try {
            IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(downloadReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(downloadReceiver, f);
            }
            receiverRegistered = true;
        } catch (Throwable ignored) {}
    }

    private SharedPreferences prefs() {
        return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void toast(String s) {
        activity.runOnUiThread(() -> Toast.makeText(activity, s, Toast.LENGTH_LONG).show());
    }

    private void showError(String s) {
        activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                .setTitle("Cập nhật thất bại")
                .setMessage(s)
                .setPositiveButton("OK", null)
                .show());
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
        }
        return b.toString();
    }

    private static final class UpdateInfo {
        final int versionCode;
        final String versionName;
        final String apkUrl;
        final String changelog;

        UpdateInfo(int versionCode, String versionName, String apkUrl, String changelog) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.changelog = changelog;
        }
    }
}
