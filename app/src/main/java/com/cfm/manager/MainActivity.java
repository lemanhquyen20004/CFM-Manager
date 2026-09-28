package com.cfm.manager;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView details;
    private UpdateManager updateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(11, 15, 20));
        getWindow().setNavigationBarColor(Color.rgb(11, 15, 20));
        buildUi();
        updateManager = new UpdateManager(this);
        updateManager.checkForUpdates(false);
        refresh();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        root.setBackgroundColor(Color.rgb(11, 15, 20));
        scroll.addView(root);

        TextView title = text("CFM Manager", 24, true);
        root.addView(title);
        TextView sub = text("Clash for Magisk • Root control", 13, false);
        sub.setTextColor(Color.rgb(155, 168, 180));
        root.addView(sub);

        status = text("Đang kiểm tra…", 24, true);
        LinearLayout card = card();
        card.addView(status);
        details = text("", 14, false);
        details.setTextColor(Color.rgb(155, 168, 180));
        details.setPadding(0, dp(8), 0, 0);
        card.addView(details);
        root.addView(card, cardParams());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button start = button("START");
        Button stop = button("STOP");
        Button restart = button("RESTART");
        row.addView(start, weightParams());
        row.addView(stop, weightParams());
        row.addView(restart, weightParams());
        root.addView(row);

        Button refresh = button("Làm mới trạng thái");
        root.addView(refresh, fullButtonParams());
        Button checkUpdate = button("Kiểm tra cập nhật");
        root.addView(checkUpdate, fullButtonParams());

        TextView note = text("Bản repo này giữ module Clash for Magisk làm backend. App chỉ gọi script root và API của Clash, tránh tự sửa iptables ngoài luồng điều khiển của module.", 13, false);
        note.setTextColor(Color.rgb(155, 168, 180));
        LinearLayout noteCard = card();
        noteCard.addView(note);
        root.addView(noteCard, cardParams());

        start.setOnClickListener(v -> runAction("Đang khởi động Clash…", ModuleController::start));
        stop.setOnClickListener(v -> runAction("Đang dừng Clash…", ModuleController::stop));
        restart.setOnClickListener(v -> runAction("Đang khởi động lại Clash…", ModuleController::restart));
        refresh.setOnClickListener(v -> refresh());
        checkUpdate.setOnClickListener(v -> updateManager.checkForUpdates(true));

        setContentView(scroll);
    }

    private void refresh() {
        status.setText("Đang kiểm tra…");
        io.execute(() -> {
            boolean root = RootShell.hasRoot();
            boolean installed = root && ModuleController.installed();
            boolean running = installed && ModuleController.running();
            String pid = running ? ModuleController.pid() : "—";
            String version = installed ? ModuleController.kernelVersion() : "—";
            String mode = installed ? ModuleController.getConfigValue("mode") : "—";
            ui.post(() -> {
                if (!root) {
                    status.setText("Chưa có quyền root");
                    status.setTextColor(Color.rgb(255, 107, 107));
                    details.setText("Hãy cấp quyền Superuser cho CFM Manager trong Magisk.");
                } else if (!installed) {
                    status.setText("Không tìm thấy module");
                    status.setTextColor(Color.rgb(255, 212, 59));
                    details.setText("Không thấy /data/clash của Clash for Magisk v3.0.");
                } else {
                    status.setText(running ? "Clash đang chạy" : "Clash đã dừng");
                    status.setTextColor(running ? Color.rgb(99, 230, 190) : Color.rgb(255, 212, 59));
                    details.setText("PID: " + pid + "\nMode: " + (mode.isEmpty() ? "—" : mode) + "\nCore: " + version);
                }
            });
        });
    }

    private interface RootAction { RootShell.Result run(); }

    private void runAction(String message, RootAction action) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            RootShell.Result r = action.run();
            ui.post(() -> {
                if (!r.ok()) {
                    new AlertDialog.Builder(this)
                            .setTitle("Lệnh thất bại")
                            .setMessage(r.combined().isEmpty() ? "Không có chi tiết lỗi." : r.combined())
                            .setPositiveButton("OK", null)
                            .show();
                }
                refresh();
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateManager != null) updateManager.onResume();
    }

    @Override
    protected void onDestroy() {
        if (updateManager != null) updateManager.destroy();
        io.shutdownNow();
        super.onDestroy();
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackgroundResource(R.drawable.bg_card);
        return l;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(244, 247, 250));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.rgb(244, 247, 250));
        b.setBackgroundResource(R.drawable.bg_button_secondary);
        return b;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(14);
        p.bottomMargin = dp(8);
        return p;
    }

    private LinearLayout.LayoutParams fullButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p.topMargin = dp(10);
        return p;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1f);
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
