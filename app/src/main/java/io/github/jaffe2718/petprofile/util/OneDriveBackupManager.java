package io.github.jaffe2718.petprofile.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.jaffe2718.petprofile.BuildConfig;
import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.mcp.McpServer;
import io.github.jaffe2718.petprofile.repository.PetRepository;

/**
 * OneDrive backup via Microsoft Graph using the authorization-code + PKCE flow (no SDK).
 *
 * <p>The cloud copy is an <b>unpacked</b> tree ({@code data.json} + {@code images/<name>}) rather
 * than a single ZIP, so a sync only transfers what changed: the small data.json always, the missing
 * images on upload, and on download only the registered images whose local copy is absent or differs
 * (size + SHA-1). Image names never change, so a sync is pure list comparison, and a restore that
 * was interrupted simply continues on the next run.
 *
 * <p>A download is <b>incremental and resumable</b>: data.json is imported first (so the records are
 * there immediately), then every needed image is streamed to {@code files/images/} one by one. A
 * reference whose image has not arrived yet is a legal state — the stored reference already points at
 * the final path, the UI simply does not render it, and a later sync fills it in. The local
 * {@code files/backup/} snapshots were removed: ZIP export/import is the rollback path.
 *
 * <p>Auth is a stored refresh token plus a short-lived access token: any operation silently renews
 * the access token when it has expired and only falls back to the interactive browser login when
 * there is no usable session. Every operation ends in exactly one {@link Callback} invocation, so
 * the UI can always clear its busy state.
 */
public final class OneDriveBackupManager {
    private static final String TAG = "ODBackup";
    private static final String SCOPE = "Files.ReadWrite.AppFolder offline_access openid profile";
    private static final String AUTHORITY = "https://login.microsoftonline.com/consumers";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String GRAPH = "https://graph.microsoft.com/v1.0/me/drive/special/approot";
    private static final String IMAGES_FOLDER = "images";
    /** The single-ZIP layout written by 0.3.0; still read (and then replaced) for compatibility. */
    private static final String LEGACY_ZIP = "pet-profile-backup.zip";
    /** Suffix of the half-written image file, so an interrupted download is never taken for a real one. */
    private static final String PART_SUFFIX = ".part";
    /** Graph lists at most $top entries per page, so follow @odata.nextLink (bounded). */
    private static final int MAX_LIST_PAGES = 50;
    /** TCP connect timeout for every Graph call. */
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    /**
     * Socket read (inactivity) timeout for one file transfer, 5 minutes.
     *
     * <p>This is a per-read ceiling, not a total per-file wall clock: a file that keeps streaming is
     * never cut off, however large it is. It only bounds a stalled transfer, so the worst case for a
     * single file is {@link #CONNECT_TIMEOUT_MS} plus this value before it counts as failed — and a
     * failed file is skipped and picked up again by the next sync.
     */
    private static final int TRANSFER_READ_TIMEOUT_MS = 300_000;
    /** The token endpoint answers quickly, so it does not get the transfer budget. */
    private static final int AUTH_READ_TIMEOUT_MS = 60_000;
    /** Renew a little before the token really expires, so a request never races the deadline. */
    private static final long EXPIRY_SLACK_MS = 60_000L;
    private static final String PREFS = "pet_profile_onedrive";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_EXPIRES = "expires_at";
    private static final String KEY_ACCOUNT = "account_name";

    public interface Callback {
        void onSuccess(String message);

        void onError(String message);
    }

    /** Continuation run once a usable access token is available. */
    private interface TokenReady {
        void onReady(String token);
    }

    /** The cloud work to run with a resolved token. */
    private interface TokenTask {
        void run(String token) throws Exception;
    }

    // pending interactive login (carried across the browser redirect)
    private static Callback pendingCallback;
    private static TokenReady pendingReady;
    private static String codeVerifier;
    private static String state;
    private static volatile String lastResult;
    private static volatile boolean cloudBusy = false;

    private OneDriveBackupManager() {
    }

    /** True while an upload/download is running (survives page recreation). */
    public static boolean isCloudBusy() {
        return cloudBusy;
    }

    public static void setCloudBusy(boolean busy) {
        cloudBusy = busy;
    }

    /** Result of the most recent cloud op, so an MCP caller can capture the callback. */
    public static String getLastResult() {
        return lastResult;
    }

    public static void setLastResult(String result) {
        lastResult = result;
    }

    public static String getClientId(Context context) {
        return BuildConfig.MSAL_CLIENT_ID;
    }

    public static boolean hasClientId(Context context) {
        String id = BuildConfig.MSAL_CLIENT_ID;
        return id != null && !id.trim().isEmpty() && !id.contains("YOUR_MSAL");
    }

    /**
     * True when the app can reach OneDrive without asking the user to sign in again: either the
     * cached access token is still valid, or a refresh token is stored and can renew it silently.
     */
    public static boolean isSignedIn(Context context) {
        if (prefs(context).getString(KEY_REFRESH, null) != null) {
            return true;
        }
        String access = prefs(context).getString(KEY_ACCESS, null);
        return access != null && System.currentTimeMillis() < prefs(context).getLong(KEY_EXPIRES, 0);
    }

    /** The signed-in Microsoft account name/email, or null. */
    public static String getAccountName(Context context) {
        return prefs(context).getString(KEY_ACCOUNT, null);
    }

    public static void signOut(Context context) {
        pendingCallback = null;
        pendingReady = null;
        prefs(context).edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES).remove(KEY_ACCOUNT).apply();
    }

    // ----- operations -----

    /** Signs in: silently renews an existing session, otherwise opens the Microsoft login page. */
    public static void signIn(Activity activity, Callback callback) {
        if (!hasClientId(activity)) {
            callback.onError(activity.getString(R.string.onedrive_no_client_id));
            return;
        }
        Context context = activity.getApplicationContext();
        Async.runLong(() -> {
            String token;
            try {
                token = resolveToken(context);
            } catch (Throwable t) {
                reportError(callback, describe(context, t));
                return;
            }
            if (token != null) {
                reportSuccess(callback, context.getString(R.string.onedrive_connected));
                return;
            }
            Async.ui(() -> startLogin(activity, callback, null));
        });
    }

    /** Upload with an interactive login fallback (UI entry point). */
    public static void upload(Activity activity, ExportBundle bundle, Callback callback) {
        Context context = activity.getApplicationContext();
        withToken(activity, callback, token -> syncUpload(context, token, bundle, callback));
    }

    /** Upload using the stored session (MCP entry point; the user must already be signed in). */
    public static void upload(Context context, ExportBundle bundle, Callback callback) {
        withStoredSession(context, callback, token -> syncUpload(context, token, bundle, callback));
    }

    /** Download with an interactive login fallback (UI entry point). */
    public static void download(Activity activity, Callback callback) {
        Context context = activity.getApplicationContext();
        withToken(activity, callback, token -> syncDownload(context, token, callback));
    }

    /** Download using the stored session (MCP entry point; the user must already be signed in). */
    public static void download(Context context, Callback callback) {
        withStoredSession(context, callback, token -> syncDownload(context, token, callback));
    }

    /** Resolves a token (renewing silently if needed) and only then logs in interactively. */
    private static void withToken(Activity activity, Callback callback, TokenTask task) {
        if (!hasClientId(activity)) {
            callback.onError(activity.getString(R.string.onedrive_no_client_id));
            return;
        }
        Context context = activity.getApplicationContext();
        Async.runLong(() -> {
            String token;
            try {
                token = resolveToken(context);
            } catch (Throwable t) {
                reportError(callback, describe(context, t));
                return;
            }
            if (token == null) {
                // No session: log in in the browser, then continue with the original operation.
                Async.ui(() -> startLogin(activity, callback,
                        value -> Async.runLong(() -> runTask(context, callback, task, value))));
                return;
            }
            runTask(context, callback, task, token);
        });
    }

    /** Resolves a token from the stored session only; reports an error when there is none. */
    private static void withStoredSession(Context context, Callback callback, TokenTask task) {
        Async.runLong(() -> {
            String token;
            try {
                token = resolveToken(context);
            } catch (Throwable t) {
                reportError(callback, describe(context, t));
                return;
            }
            if (token == null) {
                reportError(callback, context.getString(R.string.onedrive_not_signed_in));
                return;
            }
            runTask(context, callback, task, token);
        });
    }

    private static void runTask(Context context, Callback callback, TokenTask task, String token) {
        try {
            task.run(token);
        } catch (Throwable t) {
            failSync(context, callback, t);
        }
    }

    /**
     * Upload plan: an image is uploaded only when the cloud has no copy of that name or its copy
     * differs (size + SHA-1); {@code data.json} is written <b>after</b> the images so it can never
     * point at an image the cloud does not have yet; cloud images the new state no longer registers
     * are pruned last.
     */
    private static void syncUpload(Context context, String token, ExportBundle bundle, Callback callback) {
        String title = context.getString(R.string.sync_title_upload);
        lastResult = null;
        try {
            BackupManager.Tree tree = BackupManager.buildTree(context, bundle);
            Map<String, CloudItem> cloud = listImages(token);

            Map<String, Uri> images = new LinkedHashMap<>();
            List<String> toUpload = new ArrayList<>();
            for (Map.Entry<String, Uri> entry : tree.images.entrySet()) {
                String name = BackupManager.stripPrefix(entry.getKey());
                images.put(name, entry.getValue());
                if (!sameAsCloud(new File(BackupManager.imagesDir(context), name), cloud.get(name))) {
                    toUpload.add(name);
                }
            }
            List<String> toPrune = new ArrayList<>();
            for (String name : cloud.keySet()) {
                if (!images.containsKey(name)) {
                    toPrune.add(name);
                }
            }

            int total = toUpload.size() + toPrune.size() + 1;
            SyncNotifier.start(context, title, total);
            int done = 0;
            int unreadable = 0;
            for (String name : toUpload) {
                byte[] bytes = readImage(context, images.get(name));
                if (bytes == null) {
                    unreadable++;
                } else {
                    putFile(token, IMAGES_FOLDER + "/" + name, bytes, "application/octet-stream");
                }
                SyncNotifier.progress(context, title, ++done, total);
            }
            putFile(token, BackupManager.DATA_ENTRY, tree.dataJson, "application/json");
            SyncNotifier.progress(context, title, ++done, total);
            for (String name : toPrune) {
                deleteFile(token, IMAGES_FOLDER + "/" + name);
                SyncNotifier.progress(context, title, ++done, total);
            }
            deleteLegacyZip(token);
            SyncNotifier.finish(context);
            Log.i(TAG, "upload: " + images.size() + " registered, " + toUpload.size() + " sent, "
                    + toPrune.size() + " pruned, " + unreadable + " unreadable");
            if (unreadable > 0) {
                reportSuccess(callback, context.getString(R.string.onedrive_synced_skipped, unreadable));
            } else {
                reportSuccess(callback, context.getString(R.string.onedrive_synced));
            }
        } catch (Throwable t) {
            failSync(context, callback, t);
        }
    }

    /**
     * Download plan — incremental and resumable:
     * <ol>
     *   <li>{@code data.json} is fetched and imported first, so the records appear immediately.</li>
     *   <li>Every registered image the cloud has and this device lacks (or holds in a different
     *       version: size + SHA-1) is streamed straight to {@code files/images/<name>}, one file at a
     *       time — a single failure only affects that file and is retried by the next sync.</li>
     *   <li>Local files referenced by neither the cloud data nor the local data are pruned.</li>
     * </ol>
     * A reference whose image has not arrived yet is a legal state: it already points at the final
     * {@code file://} path, so the UI simply renders nothing for it, and once the image is stored the
     * same reference becomes valid without touching the database.
     */
    private static void syncDownload(Context context, String token, Callback callback) {
        lastResult = null;
        try {
            byte[] dataJson = getFile(token, BackupManager.DATA_ENTRY);
            if (dataJson == null) {
                byte[] legacyZip = getFile(token, LEGACY_ZIP);
                if (legacyZip == null) {
                    reportError(callback, context.getString(R.string.onedrive_no_cloud_data));
                    return;
                }
                restoreLegacyZip(context, callback, legacyZip);
                return;
            }

            Set<String> registered = BackupManager.registeredImages(dataJson);
            ExportBundle bundle = BackupManager.readTree(context, dataJson, Collections.<String, byte[]>emptyMap());
            if (bundle == null || bundle.profiles == null || bundle.profiles.isEmpty()) {
                // A truncated or empty backup is refused instead of silently reporting success.
                reportError(callback, context.getString(R.string.onedrive_restore_empty));
                return;
            }
            // The import happens before any image is touched: the listing must not delay it.
            importThenFetch(context, token, callback, bundle, registered);
        } catch (Throwable t) {
            failSync(context, callback, t);
        }
    }

    /** What a download still has to transfer, plus what the cloud cannot supply at all. */
    private static final class DownloadPlan {
        /** Registered images present in the cloud whose local copy is missing or different. */
        final List<String> toFetch;
        /** Registered images that are in neither the cloud nor this device (permanent gaps). */
        final int absent;

        DownloadPlan(List<String> toFetch, int absent) {
            this.toFetch = toFetch;
            this.absent = absent;
        }
    }

    /** Decides per file what to transfer: the same name + size + SHA-1 is skipped as unchanged. */
    private static DownloadPlan planImageFetches(Context context, String token, Set<String> registered) throws Exception {
        Map<String, CloudItem> cloud = listImages(token);
        List<String> toFetch = new ArrayList<>();
        int absent = 0;
        for (String ref : registered) {
            String name = BackupManager.stripPrefix(ref);
            File local = new File(BackupManager.imagesDir(context), name);
            CloudItem item = cloud.get(name);
            if (item == null) {
                if (!local.exists()) {
                    absent++;
                }
                continue;
            }
            if (!sameAsCloud(local, item)) {
                toFetch.add(name);
            }
        }
        return new DownloadPlan(toFetch, absent);
    }

    /** Imports the data first (the change is visible at once), then works out and transfers the images. */
    private static void importThenFetch(Context context, String token, Callback callback, ExportBundle bundle,
                                        Set<String> registered) {
        PetRepository.get(context).importBundle(bundle, new Async.EmptyResult() {
            @Override
            public void onSuccess() {
                RoutineScheduler.scheduleAll(context);
                RoutineNotifier.sync(context);
                // The records exist now, so refresh the screens before the images are fetched.
                notifyDataChanged(context);
                Async.runLong(() -> fetchPhase(context, token, callback, registered));
            }

            @Override
            public void onError(Throwable error) {
                SyncNotifier.finish(context);
                reportError(callback, describe(context, error));
            }
        });
    }

    /** Plans and then transfers the images; a failure here leaves the imported data in place. */
    private static void fetchPhase(Context context, String token, Callback callback, Set<String> registered) {
        DownloadPlan plan;
        try {
            plan = planImageFetches(context, token, registered);
        } catch (Throwable t) {
            // data.json is already imported; another sync completes the images.
            failSync(context, callback, t);
            return;
        }
        fetchImages(context, token, callback, registered, plan);
    }

    /** Streams every planned image to disk; one failing file never aborts the rest of the restore. */
    private static void fetchImages(Context context, String token, Callback callback,
                                    Set<String> registered, DownloadPlan plan) {
        String title = context.getString(R.string.sync_title_download);
        int total = plan.toFetch.size();
        SyncNotifier.start(context, title, total);
        int done = 0;
        int failed = 0;
        for (String name : plan.toFetch) {
            try {
                if (!downloadImage(context, token, name)) {
                    // The cloud file vanished between the listing and the fetch.
                    failed++;
                }
            } catch (GraphAuthException e) {
                // The session died mid-restore: stop, but keep every file already stored.
                SyncNotifier.finish(context);
                signOut(context);
                reportError(callback, context.getString(R.string.onedrive_session_expired));
                return;
            } catch (Throwable t) {
                failed++;
                Log.w(TAG, "image download failed: " + name, t);
            }
            SyncNotifier.progress(context, title, ++done, total);
        }
        pruneLocalImages(context, registered);
        notifyDataChanged(context);
        SyncNotifier.finish(context);
        Log.i(TAG, "download: " + registered.size() + " registered, " + total + " planned, "
                + (total - failed) + " stored, " + failed + " failed, " + plan.absent + " absent in cloud");
        if (failed > 0 || plan.absent > 0) {
            reportSuccess(callback, context.getString(R.string.onedrive_restored_incomplete, plan.absent, failed));
        } else {
            reportSuccess(callback, context.getString(R.string.onedrive_restored));
        }
    }

    /**
     * Streams one image to {@code files/images/<name>} through a {@code .part} file, so an interrupted
     * transfer can never be mistaken for a complete image. Returns false when the cloud has no such
     * file (HTTP 404).
     */
    private static boolean downloadImage(Context context, String token, String name) throws Exception {
        File dir = BackupManager.imagesDir(context);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create images directory.");
        }
        HttpURLConnection connection = open(GRAPH + ":/" + encodePath(IMAGES_FOLDER + "/" + name) + ":/content", token);
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        if (code == 404) {
            connection.disconnect();
            return false;
        }
        if (code == 401) {
            connection.disconnect();
            throw new GraphAuthException();
        }
        if (code != 200) {
            String error = readError(connection);
            connection.disconnect();
            throw new IOException("download " + name + " failed: HTTP " + code + " " + error);
        }
        File temp = new File(dir, name + PART_SUFFIX);
        try (InputStream input = connection.getInputStream();
             OutputStream output = new FileOutputStream(temp)) {
            copy(input, output);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw e;
        } finally {
            connection.disconnect();
        }
        File target = new File(dir, name);
        if (target.exists() && !target.delete()) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Unable to replace " + name);
        }
        if (!temp.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            throw new IOException("Unable to store " + name);
        }
        return true;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    /** Restores a backup written by 0.3.0 or earlier: a single ZIP holding data.json + images. */
    private static void restoreLegacyZip(Context context, Callback callback, byte[] zipBytes) {
        try {
            SyncNotifier.start(context, context.getString(R.string.sync_title_download), 0);
            ExportBundle bundle = BackupManager.readZipBytes(context, zipBytes);
            if (bundle == null || bundle.profiles == null || bundle.profiles.isEmpty()) {
                SyncNotifier.finish(context);
                reportError(callback, context.getString(R.string.onedrive_restore_empty));
                return;
            }
            Log.i(TAG, "restoring the legacy single-ZIP backup");
            PetRepository.get(context).importBundle(bundle, new Async.EmptyResult() {
                @Override
                public void onSuccess() {
                    RoutineScheduler.scheduleAll(context);
                    RoutineNotifier.sync(context);
                    pruneLocalImages(context, Collections.<String>emptySet());
                    notifyDataChanged(context);
                    SyncNotifier.finish(context);
                    reportSuccess(callback, context.getString(R.string.onedrive_restored));
                }

                @Override
                public void onError(Throwable error) {
                    SyncNotifier.finish(context);
                    reportError(callback, describe(context, error));
                }
            });
        } catch (Throwable t) {
            failSync(context, callback, t);
        }
    }

    /**
     * Asks the foreground data screens to reload. Reuses the package-scoped broadcast the MCP server
     * already fires after a successful write, so no new refresh mechanism is introduced.
     */
    private static void notifyDataChanged(Context context) {
        try {
            Intent intent = new Intent(McpServer.ACTION_DATA_CHANGED);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Deletes local images that neither the restored cloud data nor the data now in the database
     * references. The import merges by profile id, so profiles created after the last upload stay
     * behind and must keep their own images; the prune is skipped entirely when the local reference
     * set cannot be computed, because guessing there would delete files that are still in use.
     */
    private static void pruneLocalImages(Context context, Set<String> cloudRefs) {
        Set<String> keep = new HashSet<>();
        for (String ref : cloudRefs) {
            keep.add(BackupManager.stripPrefix(ref));
        }
        try {
            BackupManager.Tree local = BackupManager.buildTree(context, PetRepository.get(context).exportAllSync());
            for (String ref : local.images.keySet()) {
                keep.add(BackupManager.stripPrefix(ref));
            }
        } catch (Throwable t) {
            Log.w(TAG, "keeping every local image (local reference set unavailable)", t);
            return;
        }
        File[] files = BackupManager.imagesDir(context).listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!keep.contains(file.getName())) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    // ----- result plumbing -----

    private static void reportSuccess(Callback callback, String message) {
        lastResult = message;
        Async.ui(() -> callback.onSuccess(message));
    }

    private static void reportError(Callback callback, String message) {
        lastResult = message;
        Async.ui(() -> callback.onError(message));
    }

    /** Ends a failed sync: a rejected token drops the session so the user can sign in again. */
    private static void failSync(Context context, Callback callback, Throwable t) {
        SyncNotifier.finish(context);
        if (t instanceof GraphAuthException) {
            Log.w(TAG, "Graph rejected the token; clearing the OneDrive session");
            signOut(context);
            reportError(callback, context.getString(R.string.onedrive_session_expired));
            return;
        }
        Log.e(TAG, "cloud sync failed", t);
        reportError(callback, describe(context, t));
    }

    /** A user-facing message for a failed call. */
    private static String describe(Context context, Throwable t) {
        if (t instanceof AuthException) {
            AuthException auth = (AuthException) t;
            String detail = auth.description == null || auth.description.trim().isEmpty() ? auth.code : auth.description;
            return context.getString(R.string.onedrive_auth_failed, detail);
        }
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = t.getClass().getSimpleName();
        }
        return context.getString(R.string.onedrive_failed, message);
    }

    // ----- Graph -----

    private static void putFile(String token, String path, byte[] bytes, String contentType) throws Exception {
        HttpURLConnection connection = open(GRAPH + ":/" + encodePath(path) + ":/content", token);
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(bytes.length);
        connection.setRequestProperty("Content-Type", contentType);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(bytes);
        }
        int code = connection.getResponseCode();
        if (code == 401) {
            connection.disconnect();
            throw new GraphAuthException();
        }
        if (code != 200 && code != 201) {
            String error = readError(connection);
            connection.disconnect();
            throw new IOException("upload " + path + " failed: HTTP " + code + " " + error);
        }
        connection.disconnect();
    }

    private static byte[] getFile(String token, String path) throws Exception {
        HttpURLConnection connection = open(GRAPH + ":/" + encodePath(path) + ":/content", token);
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        if (code == 404) {
            connection.disconnect();
            return null;
        }
        if (code == 401) {
            connection.disconnect();
            throw new GraphAuthException();
        }
        if (code != 200) {
            String error = readError(connection);
            connection.disconnect();
            throw new IOException("download " + path + " failed: HTTP " + code + " " + error);
        }
        byte[] bytes = readAll(connection.getInputStream());
        connection.disconnect();
        return bytes;
    }

    private static void deleteFile(String token, String path) throws Exception {
        HttpURLConnection connection = open(GRAPH + ":/" + encodePath(path) + ":", token);
        connection.setRequestMethod("DELETE");
        int code = connection.getResponseCode();
        connection.disconnect();
        if (code == 401) {
            throw new GraphAuthException();
        }
        if (code != 204 && code != 200 && code != 404) {
            throw new IOException("delete " + path + " failed: HTTP " + code);
        }
    }

    /** Best effort: the 0.3.0 single-ZIP backup is superseded, but never fail the sync over it. */
    private static void deleteLegacyZip(String token) {
        try {
            deleteFile(token, LEGACY_ZIP);
        } catch (Throwable t) {
            Log.w(TAG, "could not remove the legacy backup zip", t);
        }
    }

    /** A cloud image entry: name, byte size and the content SHA-1 Graph reports (metadata only). */
    private static final class CloudItem {
        final long size;
        final String sha1;

        CloudItem(long size, String sha1) {
            this.size = size;
            this.sha1 = sha1;
        }
    }

    /**
     * Lists the cloud images with {@code size} and {@code file.hashes.sha1Hash}, following
     * {@code @odata.nextLink}, so the sync can decide what to transfer without downloading.
     */
    private static Map<String, CloudItem> listImages(String token) throws Exception {
        Map<String, CloudItem> items = new LinkedHashMap<>();
        String url = GRAPH + ":/" + encodePath(IMAGES_FOLDER) + ":/children?$select=name,size,file&$top=999";
        for (int page = 0; url != null && page < MAX_LIST_PAGES; page++) {
            HttpURLConnection connection = open(url, token);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            if (code == 404) {
                // No images folder in the cloud yet.
                connection.disconnect();
                return items;
            }
            if (code == 401) {
                connection.disconnect();
                throw new GraphAuthException();
            }
            if (code != 200) {
                String error = readError(connection);
                connection.disconnect();
                throw new IOException("list images failed: HTTP " + code + " " + error);
            }
            JsonObject obj = JsonParser.parseString(
                    new String(readAll(connection.getInputStream()), StandardCharsets.UTF_8)).getAsJsonObject();
            connection.disconnect();
            collectImages(obj, items);
            url = nextLink(obj);
        }
        return items;
    }

    private static void collectImages(JsonObject obj, Map<String, CloudItem> items) {
        JsonArray value = obj.has("value") && obj.get("value").isJsonArray() ? obj.getAsJsonArray("value") : new JsonArray();
        for (int i = 0; i < value.size(); i++) {
            if (!value.get(i).isJsonObject()) {
                continue;
            }
            JsonObject item = value.get(i).getAsJsonObject();
            String name = asString(item, "name");
            if (name == null) {
                continue;
            }
            if (!item.has("file") || !item.get("file").isJsonObject()) {
                // A folder inside images/ is not an image entry.
                continue;
            }
            long size = item.has("size") && item.get("size").isJsonPrimitive() ? item.get("size").getAsLong() : -1;
            String sha1 = null;
            JsonObject file = item.getAsJsonObject("file");
            if (file.has("hashes") && file.get("hashes").isJsonObject()) {
                sha1 = asString(file.getAsJsonObject("hashes"), "sha1Hash");
            }
            items.put(name, new CloudItem(size, sha1));
        }
    }

    private static String nextLink(JsonObject obj) {
        return asString(obj, "@odata.nextLink");
    }

    private static String asString(JsonObject obj, String key) {
        if (!obj.has(key)) {
            return null;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    /** SHA-1 of a local file's content. */
    private static byte[] sha1Bytes(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    /**
     * Graph reports the hash as a hex or base64 string depending on the drive, so accept either
     * (case-insensitive for hex) rather than assuming one format.
     */
    private static boolean hashMatches(String cloudHash, byte[] localDigest) {
        if (cloudHash == null) {
            return false;
        }
        String cloud = cloudHash.trim();
        StringBuilder hex = new StringBuilder();
        for (byte b : localDigest) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return cloud.equalsIgnoreCase(hex.toString())
                || cloud.equals(Base64.encodeToString(localDigest, Base64.NO_WRAP));
    }

    /** True when the cloud copy of this name is byte-identical to the local file (size + SHA-1). */
    private static boolean sameAsCloud(File local, CloudItem cloud) throws Exception {
        if (cloud == null || !local.exists() || local.length() != cloud.size) {
            return false;
        }
        if (cloud.sha1 == null) {
            // No hash reported by the drive: an equal size is the best signal we have.
            return true;
        }
        return hashMatches(cloud.sha1, sha1Bytes(local));
    }

    private static HttpURLConnection open(String url, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(TRANSFER_READ_TIMEOUT_MS);
        return connection;
    }

    private static String encodePath(String path) {
        StringBuilder builder = new StringBuilder();
        for (String segment : path.split("/")) {
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(enc(segment));
        }
        return builder.toString();
    }

    /** The bytes of a stored image, or null when it cannot be read (the sync then skips it). */
    private static byte[] readImage(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }
        try {
            return readUri(context, uri);
        } catch (Throwable t) {
            Log.w(TAG, "skipping unreadable image: " + uri, t);
            return null;
        }
    }

    private static byte[] readUri(Context context, Uri uri) throws IOException {
        try (InputStream input = "file".equalsIgnoreCase(uri.getScheme())
                ? new FileInputStream(uri.getPath())
                : context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IOException("Unable to read " + uri);
            }
            return readAll(input);
        }
    }

    // ----- auth -----

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String redirectUri() {
        return "msal" + BuildConfig.MSAL_CLIENT_ID + "://auth";
    }

    /**
     * A usable access token: the cached one while it is still valid, otherwise a silent refresh.
     * Returns null when the user must sign in interactively (no refresh token, or it was rejected).
     */
    private static String resolveToken(Context context) throws Exception {
        String access = prefs(context).getString(KEY_ACCESS, null);
        long expires = prefs(context).getLong(KEY_EXPIRES, 0);
        if (access != null && System.currentTimeMillis() < expires - EXPIRY_SLACK_MS) {
            return access;
        }
        String refresh = prefs(context).getString(KEY_REFRESH, null);
        if (refresh == null) {
            return null;
        }
        try {
            storeToken(context, postForm(TOKEN_URL, refreshBody(context, refresh)));
        } catch (AuthException e) {
            Log.w(TAG, "refresh rejected: " + e.code);
            signOut(context);
            return null;
        }
        return prefs(context).getString(KEY_ACCESS, null);
    }

    private static void startLogin(Activity activity, Callback callback, TokenReady ready) {
        if (!hasClientId(activity)) {
            callback.onError(activity.getString(R.string.onedrive_no_client_id));
            return;
        }
        pendingCallback = callback;
        pendingReady = ready;
        codeVerifier = randomString(64);
        state = randomString(16);
        String challenge;
        try {
            challenge = sha256Base64Url(codeVerifier);
        } catch (Exception e) {
            completeLoginError(activity, activity.getString(R.string.onedrive_failed, String.valueOf(e.getMessage())));
            return;
        }
        String url = AUTHORITY + "/oauth2/v2.0/authorize"
                + "?client_id=" + enc(getClientId(activity))
                + "&response_type=code"
                + "&redirect_uri=" + enc(redirectUri())
                + "&response_mode=query"
                + "&scope=" + enc(SCOPE)
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(challenge)
                + "&code_challenge_method=S256";
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            completeLoginError(activity, describe(activity, t));
        }
    }

    /** Called from OneDriveAuthActivity after the browser returns. */
    public static void handleRedirect(Activity activity, Uri uri) {
        String error = uri.getQueryParameter("error");
        if (error != null) {
            String description = uri.getQueryParameter("error_description");
            completeLoginError(activity, activity.getString(R.string.onedrive_auth_failed,
                    description == null || description.trim().isEmpty() ? error : description));
            return;
        }
        String code = uri.getQueryParameter("code");
        String returnedState = uri.getQueryParameter("state");
        if (returnedState == null || !returnedState.equals(state)) {
            completeLoginError(activity, activity.getString(R.string.onedrive_state_mismatch));
            return;
        }
        if (code == null) {
            completeLoginError(activity, activity.getString(R.string.onedrive_auth_cancelled));
            return;
        }
        Context context = activity.getApplicationContext();
        Async.runLong(() -> {
            try {
                storeToken(context, postForm(TOKEN_URL, codeBody(context, code)));
            } catch (Throwable t) {
                String message = describe(context, t);
                Async.ui(() -> completeLoginError(activity, message));
                return;
            }
            Async.ui(() -> completeLogin(context));
        });
    }

    /** Ends a started login: either continue the operation that asked for it, or just report it. */
    private static void completeLogin(Context context) {
        Callback callback = pendingCallback;
        TokenReady ready = pendingReady;
        pendingCallback = null;
        pendingReady = null;
        String access = prefs(context).getString(KEY_ACCESS, null);
        if (access == null) {
            if (callback != null) {
                callback.onError(context.getString(R.string.onedrive_no_token));
            }
            return;
        }
        if (ready != null) {
            ready.onReady(access);
        } else if (callback != null) {
            callback.onSuccess(context.getString(R.string.onedrive_connected));
        }
    }

    /** Ends a failed login; the pending callback (if any) is what clears the UI busy state. */
    private static void completeLoginError(Activity activity, String message) {
        Callback callback = pendingCallback;
        pendingCallback = null;
        pendingReady = null;
        if (callback != null) {
            callback.onError(message);
        } else {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
    }

    private static String refreshBody(Context context, String refresh) {
        return "client_id=" + enc(getClientId(context))
                + "&scope=" + enc(SCOPE)
                + "&refresh_token=" + enc(refresh)
                + "&grant_type=refresh_token";
    }

    private static String codeBody(Context context, String code) {
        return "client_id=" + enc(getClientId(context))
                + "&scope=" + enc(SCOPE)
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri())
                + "&grant_type=authorization_code"
                + "&code_verifier=" + enc(codeVerifier);
    }

    private static void storeToken(Context context, JsonObject json) {
        String access = asString(json, "access_token");
        String refresh = asString(json, "refresh_token");
        String idToken = asString(json, "id_token");
        long expiresIn = json.has("expires_in") && json.get("expires_in").isJsonPrimitive()
                ? json.get("expires_in").getAsLong()
                : 3600;
        SharedPreferences.Editor editor = prefs(context).edit();
        if (access != null) {
            editor.putString(KEY_ACCESS, access).putLong(KEY_EXPIRES, System.currentTimeMillis() + expiresIn * 1000);
        }
        if (refresh != null) {
            editor.putString(KEY_REFRESH, refresh);
        }
        if (idToken != null) {
            String account = accountFromIdToken(idToken);
            if (account != null) {
                editor.putString(KEY_ACCOUNT, account);
            }
        }
        editor.apply();
    }

    private static String accountFromIdToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payload = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            JsonObject obj = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            String name = asString(obj, "name");
            return name != null ? name : asString(obj, "preferred_username");
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject postForm(String urlText, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(AUTH_READ_TIMEOUT_MS);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        String text = code >= 400
                ? readError(connection)
                : new String(readAll(connection.getInputStream()), StandardCharsets.UTF_8);
        connection.disconnect();
        if (code != 200) {
            throw authException(code, text);
        }
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** The token endpoint reports failures as {@code {"error":..,"error_description":..}}. */
    private static AuthException authException(int code, String text) {
        String errorCode = null;
        String description = null;
        try {
            JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
            errorCode = asString(obj, "error");
            description = asString(obj, "error_description");
        } catch (Exception ignored) {
        }
        if (errorCode == null) {
            errorCode = "HTTP " + code;
        }
        return new AuthException(errorCode, description == null ? text : description);
    }

    private static String randomString(int len) {
        byte[] bytes = new byte[len];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String sha256Base64Url(String input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private static String readError(HttpURLConnection connection) {
        try (InputStream in = connection.getErrorStream()) {
            if (in == null) {
                return "";
            }
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /** The token endpoint rejected the request (bad code, revoked refresh token, ...). */
    private static final class AuthException extends Exception {
        final String code;
        final String description;

        AuthException(String code, String description) {
            super(code + ": " + description);
            this.code = code;
            this.description = description;
        }
    }

    /** Graph answered 401: the session must be renewed by signing in again. */
    private static final class GraphAuthException extends IOException {
        GraphAuthException() {
            super("HTTP 401");
        }
    }
}
