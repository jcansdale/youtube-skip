package dev.jcansdale.youtubeskip;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;

final class SkipFeedback {
    private static final String TAG = "YoutubeSkipFeedback";

    private SkipFeedback() {
    }

    static void playAutoSkip(Context context) {
        MediaPlayer player = new MediaPlayer();
        try (AssetFileDescriptor afd = context.getResources().openRawResourceFd(R.raw.auto_skip_boing)) {
            if (afd == null) {
                player.release();
                return;
            }
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            player.setVolume(1.0f, 1.0f);
            player.setOnCompletionListener(MediaPlayer::release);
            player.setOnErrorListener((mp, what, extra) -> {
                mp.release();
                Log.d(TAG, "boing playback error what=" + what + " extra=" + extra);
                return true;
            });
            player.prepare();
            player.start();
            Log.d(TAG, "playing auto-skip boing");
        } catch (IOException | RuntimeException exception) {
            player.release();
            Log.d(TAG, "unable to play boing", exception);
        }
    }
}
