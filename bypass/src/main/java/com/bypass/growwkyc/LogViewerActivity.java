package com.bypass.growwkyc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class LogViewerActivity extends Activity {

    private TextView tvLogs, tvStatus;
    private ScrollView scroll;
    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logs);

        tvLogs   = findViewById(R.id.tv_logs);
        tvStatus = findViewById(R.id.tv_status);
        scroll   = findViewById(R.id.scroll);

        Button btnCopy    = findViewById(R.id.btn_copy);
        Button btnClear   = findViewById(R.id.btn_clear);
        Button btnRefresh = findViewById(R.id.btn_refresh);

        btnCopy.setOnClickListener(v -> copyLogs());
        btnClear.setOnClickListener(v -> clearLogs());
        btnRefresh.setOnClickListener(v -> refreshLogs());

        refreshLogs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLogs();
    }

    private void refreshLogs() {
        new Thread(() -> {
            String logs = LogStore.read();
            int lineCount = logs.split("\n").length;
            handler.post(() -> {
                tvLogs.setText(logs);
                tvStatus.setText("Lines: " + lineCount + " | Log: " + LogStore.LOG_FILE);
                // Auto-scroll to bottom
                scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
            });
        }).start();
    }

    private void copyLogs() {
        String logs = tvLogs.getText().toString();
        if (logs.isEmpty()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("GrowwKycBypassLogs", logs));
        Toast.makeText(this, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show();
    }

    private void clearLogs() {
        LogStore.clear();
        tvLogs.setText("Logs cleared.");
        tvStatus.setText("Open Groww to trigger hooks, then refresh.");
        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show();
    }
}
