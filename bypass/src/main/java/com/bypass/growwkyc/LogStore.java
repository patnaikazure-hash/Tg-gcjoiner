package com.bypass.growwkyc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Shared log store: Xposed module writes here, LogViewerActivity reads here.
 * Uses /data/data/com.bypass.growwkyc/files/kyc_bypass.log
 * (world-readable so the hook running in Groww's process can write to it too)
 */
public class LogStore {

    public static final String LOG_FILE = "/data/data/com.bypass.growwkyc/files/kyc_bypass.log";
    private static final int MAX_LINES = 500;

    public static void write(String message) {
        try {
            File f = new File(LOG_FILE);
            f.getParentFile().mkdirs();
            String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(f, true))) {
                bw.write("[" + timestamp + "] " + message);
                bw.newLine();
            }
            // Keep file from growing forever
            trimIfNeeded(f);
            // Make world-readable so hook (running as Groww) can write too
            f.setReadable(true, false);
            f.setWritable(true, false);
        } catch (Throwable ignored) {}
    }

    public static String read() {
        try {
            File f = new File(LOG_FILE);
            if (!f.exists()) return "No log file yet.\n\nOpen Groww and tap Refresh.";
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            return sb.length() == 0 ? "Log file is empty." : sb.toString();
        } catch (Throwable e) {
            return "Error reading log: " + e.getMessage();
        }
    }

    public static void clear() {
        try {
            File f = new File(LOG_FILE);
            if (f.exists()) f.delete();
        } catch (Throwable ignored) {}
    }

    private static void trimIfNeeded(File f) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(f));
            java.util.List<String> lines = new java.util.ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
            br.close();
            if (lines.size() > MAX_LINES) {
                lines = lines.subList(lines.size() - MAX_LINES, lines.size());
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(f, false))) {
                    for (String l : lines) { bw.write(l); bw.newLine(); }
                }
            }
        } catch (Throwable ignored) {}
    }
}
