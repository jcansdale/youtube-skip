package dev.jcansdale.youtubeskip;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public class SkipShortcutActivity extends Activity {
    public static final String ACTION_SKIP_FORWARD = "dev.jcansdale.youtubeskip.SKIP_FORWARD";
    public static final String ACTION_SKIP_BACK = "dev.jcansdale.youtubeskip.SKIP_BACK";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String action = getIntent() == null ? ACTION_SKIP_FORWARD : getIntent().getAction();
        if (ACTION_SKIP_BACK.equals(action)) {
            YoutubeAccessibilityService.skipBackward();
        } else {
            YoutubeAccessibilityService.skipForward();
        }

        if (!YoutubeAccessibilityService.isConnected()) {
            Toast.makeText(this, "Enable YouTube Skip Overlay accessibility service", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}