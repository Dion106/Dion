package id.biztech.kasirhub;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppConfig {
    private static final String PREF = "kasirhub_local";
    private final SharedPreferences p;
    public AppConfig(Context c) { p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    public String mode() { return p.getString("mode", "hub"); }
    public void mode(String v) { p.edit().putString("mode", v).apply(); }
    public String station() { return p.getString("station", "cashier"); }
    public void station(String v) { p.edit().putString("station", v).apply(); }
    public String hubUrl() { String v = p.getString("hubUrl", "http://127.0.0.1:8787"); return trimSlash(v); }
    public void hubUrl(String v) { p.edit().putString("hubUrl", trimSlash(v)).apply(); }
    public int port() { return p.getInt("port", 8787); }
    public void port(int v) { p.edit().putInt("port", v).apply(); }
    public String printerHost() { return p.getString("printerHost", ""); }
    public void printerHost(String v) { p.edit().putString("printerHost", v == null ? "" : v.trim()).apply(); }
    public int printerPort() { return p.getInt("printerPort", 9100); }
    public void printerPort(int v) { p.edit().putInt("printerPort", v); }
    public String bluetoothMac() { return p.getString("bluetoothMac", ""); }
    public void bluetoothMac(String v) { p.edit().putString("bluetoothMac", v == null ? "" : v.trim()).apply(); }
    public boolean alarmEnabled() { return p.getBoolean("alarm", true); }
    public void alarmEnabled(boolean v) { p.edit().putBoolean("alarm", v).apply(); }
    public String deviceId() {
        String v = p.getString("deviceId", "");
        if (v.isEmpty()) { v = "kh-" + java.util.UUID.randomUUID().toString().substring(0, 8); p.edit().putString("deviceId", v).apply(); }
        return v;
    }
    private static String trimSlash(String s) { if (s == null) return ""; s = s.trim(); while (s.endsWith("/")) s = s.substring(0, s.length() - 1); return s; }
}