package dev.jcansdale.youtubeskip;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.List;

public class YoutubeNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "YoutubeSkipNotify";
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube";
    private static final long AUTO_SMART_SKIP_POLL_MS = 1_000;

    private MediaSessionManager mediaSessionManager;
    private MediaController youtubeController;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable autoSmartSkipPoll = new Runnable() {
        @Override
        public void run() {
            if (youtubeController == null) return;
            SmartSkipResolver.tryAutoSmartSkip(YoutubeNotificationListenerService.this);
            scheduleAutoSmartSkipPoll();
        }
    };

    private final MediaController.Callback youtubeCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            Log.d(TAG, "YouTube media metadata changed");
            SmartSkipResolver.prefetch(YoutubeNotificationListenerService.this);
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                SmartSkipResolver.prefetch(YoutubeNotificationListenerService.this);
            }
            scheduleAutoSmartSkipPoll();
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener = this::updateYoutubeController;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "notification listener connected");
        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (mediaSessionManager != null) {
            ComponentName listener = new ComponentName(this, YoutubeNotificationListenerService.class);
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, listener);
            updateYoutubeController(mediaSessionManager.getActiveSessions(listener));
        }
        SmartSkipResolver.prefetch(this);
    }

    @Override
    public void onListenerDisconnected() {
        Log.d(TAG, "notification listener disconnected");
        clearYoutubeController();
        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
            mediaSessionManager = null;
        }
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn != null && YOUTUBE_PACKAGE.equals(sbn.getPackageName())) {
            Log.d(TAG, "YouTube notification posted/updated");
            updateYoutubeControllerFromManager();
            SmartSkipResolver.prefetch(this);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null && YOUTUBE_PACKAGE.equals(sbn.getPackageName())) {
            Log.d(TAG, "YouTube notification removed");
        }
    }

    private void updateYoutubeControllerFromManager() {
        if (mediaSessionManager == null) return;
        ComponentName listener = new ComponentName(this, YoutubeNotificationListenerService.class);
        try {
            updateYoutubeController(mediaSessionManager.getActiveSessions(listener));
        } catch (SecurityException exception) {
            Log.d(TAG, "unable to read active sessions", exception);
        }
    }

    private void updateYoutubeController(List<MediaController> controllers) {
        MediaController controller = chooseYoutubeController(controllers);
        if (sameSession(youtubeController, controller)) {
            return;
        }
        clearYoutubeController();
        youtubeController = controller;
        if (youtubeController != null) {
            youtubeController.registerCallback(youtubeCallback);
            Log.d(TAG, "registered YouTube media controller callback");
            SmartSkipResolver.prefetch(this);
            scheduleAutoSmartSkipPoll();
        } else {
            Log.d(TAG, "no active YouTube media controller");
        }
    }

    private MediaController chooseYoutubeController(List<MediaController> controllers) {
        if (controllers == null) return null;
        MediaController fallback = null;
        for (MediaController controller : controllers) {
            if (!YOUTUBE_PACKAGE.equals(controller.getPackageName())) continue;
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
            if (fallback == null) fallback = controller;
        }
        return fallback;
    }

    private boolean sameSession(MediaController first, MediaController second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return first.getSessionToken().equals(second.getSessionToken());
    }

    private void clearYoutubeController() {
        mainHandler.removeCallbacks(autoSmartSkipPoll);
        if (youtubeController != null) {
            youtubeController.unregisterCallback(youtubeCallback);
            youtubeController = null;
        }
    }

    private void scheduleAutoSmartSkipPoll() {
        mainHandler.removeCallbacks(autoSmartSkipPoll);
        if (youtubeController == null) return;
        PlaybackState state = youtubeController.getPlaybackState();
        if (state == null || state.getState() != PlaybackState.STATE_PLAYING) return;
        mainHandler.postDelayed(autoSmartSkipPoll, AUTO_SMART_SKIP_POLL_MS);
    }
}
