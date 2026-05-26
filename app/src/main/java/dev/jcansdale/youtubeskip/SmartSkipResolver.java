package dev.jcansdale.youtubeskip;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class SmartSkipResolver {
    private static final String TAG = "YoutubeSmartSkip";
    private static final String INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    private static final String WEB_CLIENT_VERSION = "2.20260521.00.00";
    private static final String ANDROID_CLIENT_VERSION = "21.21.83";
    private static final long CACHE_MAX_AGE_MS = 6L * 60L * 60L * 1000L;
    private static final long CUE_TOLERANCE_MS = 1_500;

    private static final Map<String, SmartSkip> cache = new ConcurrentHashMap<>();
    private static final Set<String> resolving = ConcurrentHashMap.newKeySet();
    private static volatile String lastAutoSkippedKey;

    private SmartSkipResolver() {
    }

    static void prefetch(Context context) {
        MediaSessionSkipController.PlaybackInfo info = validPlaybackInfo(context);
        if (info == null) {
            return;
        }
        String key = cacheKey(info);
        SmartSkip smartSkip = cache.get(key);
        if (smartSkip == null || smartSkip.isExpired()) {
            resolveInBackground(key, info);
        }
    }

    static boolean tryAutoSmartSkip(Context context) {
        MediaSessionSkipController.PlaybackInfo info = validPlaybackInfo(context);
        if (info == null) {
            return false;
        }

        String key = cacheKey(info);
        SmartSkip smartSkip = cache.get(key);
        if (smartSkip == null || smartSkip.isExpired()) {
            resolveInBackground(key, info);
            return false;
        }
        if (!smartSkip.hasTarget()) {
            return false;
        }
        if (info.positionMs + CUE_TOLERANCE_MS < smartSkip.cueStartMs
                || info.positionMs - CUE_TOLERANCE_MS > smartSkip.cueEndMs
                || info.positionMs >= smartSkip.seekToMs - CUE_TOLERANCE_MS) {
            return false;
        }

        String autoKey = key + "|" + smartSkip.videoId + "|" + smartSkip.seekToMs;
        if (autoKey.equals(lastAutoSkippedKey)) {
            return false;
        }

        boolean sought = MediaSessionSkipController.seekTo(context, smartSkip.seekToMs);
        if (sought) {
            lastAutoSkippedKey = autoKey;
            SkipFeedback.playAutoSkip(context);
        }
        Log.d(TAG, "auto smart skip result=" + sought
                + " position=" + info.positionMs
                + " target=" + smartSkip.seekToMs
                + " cue=" + smartSkip.cueStartMs + ".." + smartSkip.cueEndMs
                + " videoId=" + smartSkip.videoId);
        return sought;
    }

    private static MediaSessionSkipController.PlaybackInfo validPlaybackInfo(Context context) {
        MediaSessionSkipController.PlaybackInfo info = MediaSessionSkipController.playbackInfo(context);
        if (info == null || !info.canSeek() || isBlank(info.title) || isBlank(info.artist) || info.durationMs <= 0) {
            return null;
        }
        return info;
    }

    private static void resolveInBackground(String key, MediaSessionSkipController.PlaybackInfo info) {
        if (!resolving.add(key)) {
            return;
        }
        new Thread(() -> {
            try {
                SmartSkip smartSkip = resolve(info);
                if (smartSkip != null) {
                    cache.put(key, smartSkip);
                    Log.d(TAG, "resolved smart skip videoId=" + smartSkip.videoId
                            + " cue=" + smartSkip.cueStartMs + ".." + smartSkip.cueEndMs
                            + " target=" + smartSkip.seekToMs
                            + " title=" + info.title
                            + " artist=" + info.artist);
                } else {
                    cache.put(key, SmartSkip.noMetadata());
                    Log.d(TAG, "no smart skip metadata resolved for title=" + info.title + " artist=" + info.artist);
                }
            } catch (Exception exception) {
                Log.d(TAG, "smart skip resolve failed", exception);
            } finally {
                resolving.remove(key);
            }
        }, "SmartSkipResolver").start();
    }

    private static SmartSkip resolve(MediaSessionSkipController.PlaybackInfo info) throws IOException, JSONException {
        String videoId = findVideoId(info);
        return videoId == null ? null : fetchSmartSkip(videoId);
    }

    private static String findVideoId(MediaSessionSkipController.PlaybackInfo info) throws IOException, JSONException {
        JSONObject body = new JSONObject()
                .put("context", new JSONObject()
                        .put("client", new JSONObject()
                                .put("clientName", "WEB")
                                .put("clientVersion", WEB_CLIENT_VERSION)
                                .put("hl", "en")
                                .put("gl", "US")))
                .put("query", info.title + " " + info.artist);
        JSONObject response = postJson("https://www.youtube.com/youtubei/v1/search?key=" + INNERTUBE_KEY, body, webUserAgent());
        VideoSearchResult best = findMatchingVideo(response, info);
        if (best == null) {
            Log.d(TAG, "search found no exact video match for title=" + info.title + " artist=" + info.artist + " duration=" + info.durationMs);
            return null;
        }
        Log.d(TAG, "search matched videoId=" + best.videoId + " title=" + best.title + " owner=" + best.owner + " duration=" + best.durationMs);
        return best.videoId;
    }

    private static SmartSkip fetchSmartSkip(String videoId) throws IOException, JSONException {
        JSONObject body = new JSONObject()
                .put("context", new JSONObject()
                        .put("client", new JSONObject()
                                .put("clientName", "ANDROID")
                                .put("clientVersion", ANDROID_CLIENT_VERSION)
                                .put("androidSdkVersion", 35)
                                .put("hl", "en")
                                .put("gl", "US")))
                .put("videoId", videoId);
        JSONObject response = postJson("https://www.youtube.com/youtubei/v1/next?key=" + INNERTUBE_KEY, body, androidUserAgent());
        JSONObject action = findJumpAheadAction(response);
        if (action == null) {
            return null;
        }

        JSONObject cueRange = action.optJSONObject("cueRange");
        long cueStart = parseLong(cueRange == null ? null : cueRange.opt("startTimeMilliseconds"), -1);
        long cueEnd = parseLong(cueRange == null ? null : cueRange.opt("endTimeMilliseconds"), -1);
        long target = parseLong(action.opt("seekToTimeMs"), -1);

        JSONObject smartSkipMetadata = action.optJSONObject("smartSkipMetadata");
        JSONObject smartSkipData = smartSkipMetadata == null ? null : smartSkipMetadata.optJSONObject("smartSkipData");
        if (target < 0 && smartSkipData != null) target = parseLong(smartSkipData.opt("endMillis"), -1);
        if (cueStart < 0 && smartSkipData != null) cueStart = parseLong(smartSkipData.opt("startMillis"), -1);
        if (cueEnd < 0 && smartSkipData != null) cueEnd = parseLong(smartSkipData.opt("endMillis"), -1);
        if (cueStart < 0 || cueEnd < 0 || target < 0) {
            return null;
        }
        return new SmartSkip(videoId, cueStart, cueEnd, target);
    }

    private static JSONObject findJumpAheadAction(Object value) throws JSONException {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONObject buttonData = object.optJSONObject("buttonData");
            String title = buttonData == null ? null : buttonData.optString("title", null);
            if (title != null && title.toLowerCase().contains("jump ahead") && object.has("seekToTimeMs")) {
                return object;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                JSONObject result = findJumpAheadAction(object.get(keys.next()));
                if (result != null) return result;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                JSONObject result = findJumpAheadAction(array.get(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private static VideoSearchResult findMatchingVideo(Object value, MediaSessionSkipController.PlaybackInfo info) throws JSONException {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONObject renderer = object.optJSONObject("videoRenderer");
            if (renderer != null) {
                VideoSearchResult result = parseVideoRenderer(renderer);
                if (result != null && matches(result, info)) return result;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                VideoSearchResult result = findMatchingVideo(object.get(keys.next()), info);
                if (result != null) return result;
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                VideoSearchResult result = findMatchingVideo(array.get(i), info);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static VideoSearchResult parseVideoRenderer(JSONObject renderer) {
        String videoId = renderer.optString("videoId", null);
        String title = runsText(renderer.optJSONObject("title"));
        String owner = runsText(renderer.optJSONObject("ownerText"));
        if (isBlank(owner)) owner = runsText(renderer.optJSONObject("longBylineText"));
        JSONObject lengthText = renderer.optJSONObject("lengthText");
        long durationMs = parseColonDurationMs(lengthText == null ? null : lengthText.optString("simpleText", null));
        if (isBlank(videoId) || isBlank(title) || isBlank(owner) || durationMs <= 0) return null;
        return new VideoSearchResult(videoId, title, owner, durationMs);
    }

    private static boolean matches(VideoSearchResult result, MediaSessionSkipController.PlaybackInfo info) {
        String resultTitle = normalize(result.title);
        String infoTitle = normalize(info.title);
        String resultOwner = normalize(result.owner);
        String infoArtist = normalize(info.artist);
        return resultTitle.equals(infoTitle)
                && (resultOwner.equals(infoArtist) || resultOwner.contains(infoArtist) || infoArtist.contains(resultOwner))
                && Math.abs(result.durationMs - info.durationMs) <= 2_000;
    }

    private static JSONObject postJson(String url, JSONObject body, String userAgent) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", userAgent);
        try (OutputStream output = connection.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output))) {
            writer.write(body.toString());
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = readAll(stream);
        if (status < 200 || status >= 300) throw new IOException("HTTP " + status + ": " + response);
        return new JSONObject(response);
    }

    private static String readAll(InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private static String runsText(JSONObject object) {
        if (object == null) return null;
        String simpleText = object.optString("simpleText", null);
        if (!isBlank(simpleText)) return simpleText;
        JSONArray runs = object.optJSONArray("runs");
        if (runs == null) return null;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < runs.length(); i++) {
            JSONObject run = runs.optJSONObject(i);
            if (run != null) builder.append(run.optString("text", ""));
        }
        return builder.toString();
    }

    private static long parseColonDurationMs(String value) {
        if (isBlank(value)) return -1;
        String[] parts = value.split(":");
        long total = 0;
        for (String part : parts) {
            try {
                total = (total * 60) + Long.parseLong(part.trim());
            } catch (NumberFormatException exception) {
                return -1;
            }
        }
        return total * 1000;
    }

    private static long parseLong(Object value, long fallback) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase()
                .replace('․', '.')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String cacheKey(MediaSessionSkipController.PlaybackInfo info) {
        return normalize(info.title) + "|" + normalize(info.artist) + "|" + info.durationMs;
    }

    private static String webUserAgent() {
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36";
    }

    private static String androidUserAgent() {
        return "com.google.android.youtube/" + ANDROID_CLIENT_VERSION + " (Linux; U; Android 15) gzip";
    }

    private static final class VideoSearchResult {
        final String videoId;
        final String title;
        final String owner;
        final long durationMs;

        VideoSearchResult(String videoId, String title, String owner, long durationMs) {
            this.videoId = videoId;
            this.title = title;
            this.owner = owner;
            this.durationMs = durationMs;
        }
    }

    private static final class SmartSkip {
        final String videoId;
        final long cueStartMs;
        final long cueEndMs;
        final long seekToMs;
        final long fetchedAtMs = System.currentTimeMillis();

        SmartSkip(String videoId, long cueStartMs, long cueEndMs, long seekToMs) {
            this.videoId = videoId;
            this.cueStartMs = cueStartMs;
            this.cueEndMs = cueEndMs;
            this.seekToMs = seekToMs;
        }

        static SmartSkip noMetadata() {
            return new SmartSkip(null, -1, -1, -1);
        }

        boolean hasTarget() {
            return seekToMs >= 0;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - fetchedAtMs > CACHE_MAX_AGE_MS;
        }
    }
}
