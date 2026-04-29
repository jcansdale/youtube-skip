package dev.jcansdale.youtubeskip;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class SkipShortcutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String action = getIntent() == null ? MainActivity.ACTION_SKIP_FORWARD : getIntent().getAction();
        if (MainActivity.ACTION_SKIP_BACK.equals(action)) {
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