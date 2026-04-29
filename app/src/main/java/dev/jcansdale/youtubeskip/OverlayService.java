package dev.jcansdale.youtubeskip;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayService extends Service {
    private static final String ACTION_SHOW = "dev.jcansdale.youtubeskip.SHOW";
    private static final String ACTION_TOGGLE_TEST = "dev.jcansdale.youtubeskip.TOGGLE_TEST";
    private static final String ACTION_REFRESH = "dev.jcansdale.youtubeskip.REFRESH";
    private static final String ACTION_HIDE = "dev.jcansdale.youtubeskip.HIDE";

    private WindowManager windowManager;
    private View overlayView;
    private long testModeUntilMillis;

    public static void show(Context context) {
        Intent intent = new Intent(context, OverlayService.class).setAction(ACTION_SHOW);
        context.startService(intent);
    }

    public static void toggleForTest(Context context) {
        Intent intent = new Intent(context, OverlayService.class).setAction(ACTION_TOGGLE_TEST);
        context.startService(intent);
    }

    public static void hide(Context context) {
        Intent intent = new Intent(context, OverlayService.class).setAction(ACTION_HIDE);
        context.startService(intent);
    }

    public static void refresh(Context context) {
        Intent intent = new Intent(context, OverlayService.class).setAction(ACTION_REFRESH);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_HIDE : intent.getAction();
        if (ACTION_SHOW.equals(action)) {
            testModeUntilMillis = 0;
            showOverlay();
        } else if (ACTION_TOGGLE_TEST.equals(action)) {
            toggleTestOverlay();
        } else if (ACTION_REFRESH.equals(action)) {
            refreshOverlay();
        } else {
            hideOverlay();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        testModeUntilMillis = 0;
        hideOverlay();
        super.onDestroy();
    }

    private void showOverlay() {
        if (!Settings.canDrawOverlays(this) || !AppSettings.overlayButtonsEnabled(this) || overlayView != null) {
            return;
        }

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        controls.setElevation(dp(8));
        controls.setAlpha(AppSettings.overlayOpacity(this) / 100f);

        controls.addView(skipButton("-10", "Skip back", view -> YoutubeAccessibilityService.skipBackward()));
        controls.addView(skipButton("+10", "Skip forward", view -> YoutubeAccessibilityService.skipForward()));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            dp(144),
                dp(68),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        params.x = AppSettings.overlayX(this, getResources().getDisplayMetrics().density);
        params.y = AppSettings.overlayY(this, getResources().getDisplayMetrics().density);

        overlayView = controls;
        windowManager.addView(overlayView, params);
    }

    private TextView skipButton(String label, String contentDescription, View.OnClickListener onClickListener) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(contentDescription);
        button.setBackground(circleBackground());
        button.setOnClickListener(onClickListener);
        addDragHandle(button);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(62), dp(62));
        params.setMargins(dp(4), 0, dp(4), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void toggleTestOverlay() {
        if (testModeUntilMillis > SystemClock.uptimeMillis() && overlayView != null) {
            testModeUntilMillis = 0;
            hideOverlay();
            return;
        }

        testModeUntilMillis = Long.MAX_VALUE;
        showOverlay();
    }

    private void hideOverlay() {
        if (testModeUntilMillis > SystemClock.uptimeMillis()) {
            return;
        }
        if (overlayView == null) {
            return;
        }
        windowManager.removeView(overlayView);
        overlayView = null;
    }

    private void refreshOverlay() {
        if (overlayView == null) {
            return;
        }

        overlayView.setAlpha(AppSettings.overlayOpacity(this) / 100f);
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();
        params.x = AppSettings.overlayX(this, getResources().getDisplayMetrics().density);
        params.y = AppSettings.overlayY(this, getResources().getDisplayMetrics().density);
        windowManager.updateViewLayout(overlayView, params);
    }

    private void addDragHandle(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean moved;

            @Override
            public boolean onTouch(View touchedView, MotionEvent event) {
                if (overlayView == null) {
                    return false;
                }
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayView.getLayoutParams();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (initialTouchX - event.getRawX());
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        moved = moved || Math.abs(deltaX) > dp(4) || Math.abs(deltaY) > dp(4);
                        params.x = Math.max(0, initialX + deltaX);
                        params.y = initialY + deltaY;
                        windowManager.updateViewLayout(overlayView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            touchedView.performClick();
                        } else {
                            AppSettings.setOverlayPosition(OverlayService.this, params.x, params.y);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    @Override
    public boolean onUnbind(Intent intent) {
        hideOverlay();
        return super.onUnbind(intent);
    }

    private GradientDrawable circleBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.rgb(15, 118, 110));
        drawable.setStroke(dp(2), Color.argb(220, 255, 255, 255));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}