package dev.jcansdale.youtubeskip;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String ACTION_MANAGE_APP_OVERLAY_PERMISSION = "android.settings.MANAGE_APP_OVERLAY_PERMISSION";
    private static final String ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS";
    private static final String EXTRA_ACCESSIBILITY_COMPONENT_NAME = "android.provider.extra.ACCESSIBILITY_COMPONENT_NAME";

    private TextView overlayStatus;
    private TextView accessibilityStatus;
    private Button overlayButton;
    private Button accessibilityButton;
    private Button testButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int padding = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.rgb(248, 250, 252));

        TextView title = new TextView(this);
        title.setText("YouTube Skip Overlay");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(15, 23, 42));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title, fullWidthWrapHeight());

        overlayStatus = statusView();
        accessibilityStatus = statusView();
        root.addView(overlayStatus, fullWidthWrapHeight());
        root.addView(accessibilityStatus, fullWidthWrapHeight());

        overlayButton = button("Allow overlay");
        overlayButton.setOnClickListener(view -> openOverlaySettings());
        root.addView(overlayButton, fullWidthWrapHeight());

        accessibilityButton = button("Open accessibility settings");
        accessibilityButton.setOnClickListener(view -> openAccessibilitySettings());
        root.addView(accessibilityButton, fullWidthWrapHeight());

        testButton = button("Show test button");
        testButton.setOnClickListener(view -> OverlayService.showForTest(this));
        root.addView(testButton, fullWidthWrapHeight());

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean canDrawOverlays = Settings.canDrawOverlays(this);
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();
        overlayStatus.setText(canDrawOverlays ? "Overlay permission: on" : "Overlay permission: off");
        accessibilityStatus.setText(accessibilityEnabled ? "Accessibility service: on" : "Accessibility service: off");
        overlayButton.setText(canDrawOverlays ? "Overlay allowed" : "Allow overlay");
        overlayButton.setEnabled(!canDrawOverlays);
        accessibilityButton.setText(accessibilityEnabled ? "Accessibility enabled" : "Open accessibility settings");
        accessibilityButton.setEnabled(!accessibilityEnabled);
        testButton.setEnabled(canDrawOverlays);
    }

    private void openOverlaySettings() {
        Toast.makeText(this, "Turn on display over other apps for YouTube Skip Overlay", Toast.LENGTH_LONG).show();

        Intent appOverlayIntent = new Intent(
            ACTION_MANAGE_APP_OVERLAY_PERMISSION,
                Uri.fromParts("package", getPackageName(), null)
        );
        if (startSettingsActivity(appOverlayIntent)) {
            return;
        }

        Intent directIntent = new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", getPackageName(), null)
        );
        if (startSettingsActivity(directIntent)) {
            return;
        }

        Intent listIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        if (startSettingsActivity(listIntent)) {
            return;
        }

        Intent detailsIntent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
        );
        if (!startSettingsActivity(detailsIntent)) {
            Toast.makeText(this, "Unable to open Android settings", Toast.LENGTH_LONG).show();
        }
    }

    private void openAccessibilitySettings() {
        Toast.makeText(this, "Turn on YouTube Skip Overlay in Accessibility", Toast.LENGTH_LONG).show();

        Intent detailIntent = new Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
                .putExtra(
                        EXTRA_ACCESSIBILITY_COMPONENT_NAME,
                        new ComponentName(this, YoutubeAccessibilityService.class)
                );
        if (startSettingsActivity(detailIntent)) {
            return;
        }

        Intent listIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (!startSettingsActivity(listIntent)) {
            Toast.makeText(this, "Unable to open Accessibility settings", Toast.LENGTH_LONG).show();
        }
    }

    private boolean startSettingsActivity(Intent intent) {
        if (intent.resolveActivity(getPackageManager()) == null) {
            return false;
        }
        try {
            startActivity(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName expected = new ComponentName(this, YoutubeAccessibilityService.class);
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName enabledService = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(enabledService)) {
                return true;
            }
        }
        return false;
    }

    private TextView statusView() {
        TextView view = new TextView(this);
        view.setTextSize(16);
        view.setTextColor(Color.rgb(51, 65, 85));
        view.setPadding(0, dp(6), 0, dp(6));
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams fullWidthWrapHeight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}