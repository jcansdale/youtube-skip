package dev.jcansdale.youtubeskip;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Point;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

public class YoutubeAccessibilityService extends AccessibilityService {
    private static final String TAG = "YoutubeSkipOverlay";
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final long VOLUME_DOUBLE_CLICK_MS = 450;
    private static YoutubeAccessibilityService activeService;
    private long lastVolumeUpDownTime;
    private long lastVolumeDownDownTime;

    public static void skipForward() {
        if (activeService != null) {
            activeService.dispatchSkipGesture(true);
        }
    }

    public static void skipBackward() {
        if (activeService != null) {
            activeService.dispatchSkipGesture(false);
        }
    }

    @Override
    protected void onServiceConnected() {
        activeService = this;
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        updateOverlayForFocusedWindow(null);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0 || !isYoutubeFocused()) {
            return false;
        }

        long eventTime = event.getEventTime();
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (isDoubleClick(eventTime, lastVolumeUpDownTime)) {
                    lastVolumeUpDownTime = 0;
                    Log.d(TAG, "volume double-click skip forward");
                    skipForward();
                    return true;
                }
                lastVolumeUpDownTime = eventTime;
                return false;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (isDoubleClick(eventTime, lastVolumeDownDownTime)) {
                    lastVolumeDownDownTime = 0;
                    Log.d(TAG, "volume double-click skip back");
                    skipBackward();
                    return true;
                }
                lastVolumeDownDownTime = eventTime;
                return false;
            default:
                return false;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        updateOverlayForFocusedWindow(event == null ? null : event.getPackageName());
    }

    @Override
    public void onInterrupt() {
        OverlayService.hide(this);
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        activeService = null;
        OverlayService.hide(this);
        return super.onUnbind(intent);
    }

    private void dispatchSkipGesture(boolean forward) {
        Point size = screenSize();
        boolean landscape = size.x > size.y;
        float x = size.x * (forward ? 0.82f : 0.18f);
        float y = landscape ? size.y * 0.50f : portraitVideoCenterY(size.x, size.y);

        Path firstTap = tapPath(x, y);
        Path secondTap = tapPath(x, y);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(firstTap, 0, 50))
                .addStroke(new GestureDescription.StrokeDescription(secondTap, 140, 50))
                .build();
        boolean dispatched = dispatchGesture(gesture, null, null);
        Log.d(TAG, "skip " + (forward ? "forward" : "back") + " dispatched=" + dispatched + " x=" + x + " y=" + y + " screen=" + size.x + "x" + size.y);
    }

    private void updateOverlayForFocusedWindow(CharSequence eventPackageName) {
        CharSequence focusedPackageName = focusedPackageName();
        CharSequence packageName = focusedPackageName == null ? eventPackageName : focusedPackageName;
        if (YOUTUBE_PACKAGE.contentEquals(packageName == null ? "" : packageName)) {
            Log.d(TAG, "show overlay for package=" + packageName);
            OverlayService.show(this);
        } else if (packageName != null && !SYSTEM_UI_PACKAGE.contentEquals(packageName)) {
            Log.d(TAG, "hide overlay for package=" + packageName);
            OverlayService.hide(this);
        }
    }

    private boolean isYoutubeFocused() {
        CharSequence packageName = focusedPackageName();
        return YOUTUBE_PACKAGE.contentEquals(packageName == null ? "" : packageName);
    }

    private boolean isDoubleClick(long eventTime, long lastEventTime) {
        return lastEventTime > 0 && eventTime - lastEventTime <= VOLUME_DOUBLE_CLICK_MS;
    }

    private CharSequence focusedPackageName() {
        List<AccessibilityWindowInfo> windows = getWindows();
        for (AccessibilityWindowInfo window : windows) {
            if (!window.isActive() && !window.isFocused()) {
                continue;
            }
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) {
                continue;
            }
            CharSequence packageName = root.getPackageName();
            root.recycle();
            if (packageName != null) {
                return packageName;
            }
        }
        return null;
    }

    private float portraitVideoCenterY(int width, int height) {
        float sixteenByNineHeight = width * 9f / 16f;
        float statusBarOffset = Math.min(height * 0.04f, dp(64));
        return statusBarOffset + (sixteenByNineHeight / 2f);
    }

    private Path tapPath(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        return path;
    }

    private Point screenSize() {
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);
        return size;
    }

    private int dp(int value) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return (int) (value * metrics.density + 0.5f);
    }
}