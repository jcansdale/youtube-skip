package dev.jcansdale.youtubeskip;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

final class MediaSessionSkipController {
    private static final String TAG = "YoutubeSkipMedia";
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube";

    static final class PlaybackInfo {
        final String title;
        final String artist;
        final long durationMs;
        final long positionMs;
        final long actions;

        PlaybackInfo(String title, String artist, long durationMs, long positionMs, long actions) {
            this.title = title;
            this.artist = artist;
            this.durationMs = durationMs;
            this.positionMs = positionMs;
            this.actions = actions;
        }

        boolean canSeek() {
            return (actions & PlaybackState.ACTION_SEEK_TO) != 0;
        }
    }

    private MediaSessionSkipController() {
    }

    static PlaybackInfo playbackInfo(Context context) {
        MediaController controller = youtubeController(context);
        if (controller == null) {
            return null;
        }
        PlaybackState state = controller.getPlaybackState();
        MediaMetadata metadata = controller.getMetadata();
        if (state == null || metadata == null) {
            return null;
        }

        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null) {
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }

        return new PlaybackInfo(
                metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist,
                metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                estimatedPosition(state),
                state.getActions()
        );
    }

    static boolean seekTo(Context context, long targetMs) {
        MediaController controller = youtubeController(context);
        if (controller == null) {
            return false;
        }
        PlaybackState state = controller.getPlaybackState();
        if (state == null || (state.getActions() & PlaybackState.ACTION_SEEK_TO) == 0) {
            return false;
        }
        long target = clampToDuration(controller, targetMs);
        controller.getTransportControls().seekTo(target);
        Log.d(TAG, "seekTo " + target);
        return true;
    }

    private static MediaController youtubeController(Context context) {
        MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (manager == null) {
            return null;
        }

        ComponentName listener = new ComponentName(context, YoutubeNotificationListenerService.class);
        List<MediaController> controllers;
        try {
            controllers = manager.getActiveSessions(listener);
        } catch (SecurityException exception) {
            Log.d(TAG, "notification listener access is not enabled", exception);
            return null;
        }

        MediaController fallback = null;
        for (MediaController controller : controllers) {
            if (!YOUTUBE_PACKAGE.equals(controller.getPackageName())) {
                continue;
            }
            PlaybackState state = controller.getPlaybackState();
            if (state == null) {
                fallback = controller;
                continue;
            }
            int playbackState = state.getState();
            if (playbackState == PlaybackState.STATE_PLAYING
                    || playbackState == PlaybackState.STATE_BUFFERING
                    || playbackState == PlaybackState.STATE_PAUSED) {
                return controller;
            }
            if (fallback == null) {
                fallback = controller;
            }
        }
        return fallback;
    }

    private static long estimatedPosition(PlaybackState state) {
        long position = state.getPosition();
        if (position < 0) {
            return -1;
        }
        if (state.getState() == PlaybackState.STATE_PLAYING
                && state.getLastPositionUpdateTime() > 0
                && state.getPlaybackSpeed() != 0f) {
            long elapsed = SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
            if (elapsed > 0) {
                position += (long) (elapsed * state.getPlaybackSpeed());
            }
        }
        return Math.max(0, position);
    }

    private static long clampToDuration(MediaController controller, long target) {
        MediaMetadata metadata = controller.getMetadata();
        long duration = metadata == null ? -1 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        target = Math.max(0, target);
        if (duration > 0) {
            target = Math.min(duration, target);
        }
        return target;
    }
}
