package dev.jcansdale.youtubeskip;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    private static final String PREFS_NAME = "youtube_skip_overlay";
    private static final String KEY_OVERLAY_BUTTONS_ENABLED = "overlay_buttons_enabled";
    private static final String KEY_VOLUME_DOUBLE_CLICK_ENABLED = "volume_double_click_enabled";
    private static final String KEY_OVERLAY_OPACITY = "overlay_opacity";
    private static final String KEY_OVERLAY_X = "overlay_x";
    private static final String KEY_OVERLAY_Y = "overlay_y";

    private static final boolean DEFAULT_OVERLAY_BUTTONS_ENABLED = true;
    private static final boolean DEFAULT_VOLUME_DOUBLE_CLICK_ENABLED = true;
    private static final int DEFAULT_OVERLAY_OPACITY = 85;
    private static final int DEFAULT_OVERLAY_X_DP = 18;
    private static final int DEFAULT_OVERLAY_Y_DP = 0;

    private AppSettings() {
    }

    public static boolean overlayButtonsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_OVERLAY_BUTTONS_ENABLED, DEFAULT_OVERLAY_BUTTONS_ENABLED);
    }

    public static void setOverlayButtonsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_OVERLAY_BUTTONS_ENABLED, enabled).apply();
    }

    public static boolean volumeDoubleClickEnabled(Context context) {
        return prefs(context).getBoolean(KEY_VOLUME_DOUBLE_CLICK_ENABLED, DEFAULT_VOLUME_DOUBLE_CLICK_ENABLED);
    }

    public static void setVolumeDoubleClickEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_VOLUME_DOUBLE_CLICK_ENABLED, enabled).apply();
    }

    public static int overlayOpacity(Context context) {
        return prefs(context).getInt(KEY_OVERLAY_OPACITY, DEFAULT_OVERLAY_OPACITY);
    }

    public static void setOverlayOpacity(Context context, int opacity) {
        int boundedOpacity = Math.max(40, Math.min(100, opacity));
        prefs(context).edit().putInt(KEY_OVERLAY_OPACITY, boundedOpacity).apply();
    }

    public static int overlayX(Context context, float density) {
        return prefs(context).getInt(KEY_OVERLAY_X, dp(DEFAULT_OVERLAY_X_DP, density));
    }

    public static int overlayY(Context context, float density) {
        return prefs(context).getInt(KEY_OVERLAY_Y, dp(DEFAULT_OVERLAY_Y_DP, density));
    }

    public static void setOverlayPosition(Context context, int x, int y) {
        prefs(context).edit()
                .putInt(KEY_OVERLAY_X, x)
                .putInt(KEY_OVERLAY_Y, y)
                .apply();
    }

    public static void resetOverlayPosition(Context context) {
        prefs(context).edit()
                .remove(KEY_OVERLAY_X)
                .remove(KEY_OVERLAY_Y)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static int dp(int value, float density) {
        return (int) (value * density + 0.5f);
    }
}