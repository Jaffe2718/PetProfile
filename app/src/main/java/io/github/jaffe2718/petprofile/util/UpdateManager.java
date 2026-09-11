package io.github.jaffe2718.petprofile.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.jaffe2718.petprofile.BuildConfig;

/**
 * In-app update: queries the GitHub release, downloads the APK with a resumable transfer, verifies
 * its SHA-256 against the digest the release API publishes, and hands it to the system installer.
 *
 * <p>The download is a plain HTTP {@code Range} request against the same file, so an interrupted
 * transfer continues where it stopped instead of starting over. Because a resumed file is assembled
 * from several responses, it is only accepted once the whole file has been hashed and the digest
 * matches; the partial file is deleted whenever it cannot be trusted (digest or size mismatch).
 */
public final class UpdateManager {
    private static final String TAG = "UpdateManager";
    private static final String RELEASE_API =
            "https://api.github.com/repos/Jaffe2718/PetProfile/releases/latest";
    private static final String APK_ASSET = "petprofile.apk";
    private static final String UPDATE_DIR = "updates";
    private static final String PART_SUFFIX = ".apk.part";
    private static final String ETAG_SUFFIX = ".etag";
    /** A transfer or leftover that has not been touched for this long is discarded. */
    private static final long STALE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long PROGRESS_STEP = 256 * 1024;

    /** Its own worker: an update download must not queue behind a cloud sync. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private UpdateManager() {
    }

    /** A published release that is newer than the running build. */
    public static final class Release {
        public final String tag;
        public final String version;
        public final String apkUrl;
        /** Size in bytes as reported by the release API, or -1 when unknown. */
        public final long size;
        /** Lower-case hex SHA-256 of the APK, or null when the release does not publish one. */
        public final String sha256;
        /**
         * A verified APK of this exact release that is already in the update cache, or null. Set
         * while looking the release up, so the UI can offer to install instead of downloading again.
         */
        public File cachedApk;

        Release(String tag, String version, String apkUrl, long size, String sha256) {
            this.tag = tag;
            this.version = version;
            this.apkUrl = apkUrl;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    /** Handle for an in-flight download. */
    public static final class Download {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        public void cancel() {
            cancelled.set(true);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    public interface DownloadCallback {
        void onProgress(long downloaded, long total, boolean resumed);

        void onVerifying();

        void onReady(File apk);

        void onError(String message);
    }

    /** Looks up the latest release; the callback receives null when the running build is current. */
    public static void fetchLatest(Context context, Async.Result<Release> callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                Release release = readLatestRelease();
                if (release == null || !isNewer(release.version)) {
                    Async.post(callback, null, null);
                } else {
                    release.cachedApk = verifiedCachedApk(appContext, release);
                    Async.post(callback, release, null);
                }
            } catch (Throwable t) {
                Log.w(TAG, "release lookup failed", t);
                Async.post(callback, null, t);
            }
        });
    }

    private static Release readLatestRelease() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "PetProfile");
        int code = connection.getResponseCode();
        String body = code == 200
                ? readAll(connection.getInputStream())
                : readAll(connection.getErrorStream());
        connection.disconnect();
        if (code != 200) {
            throw new IOException("release lookup failed: HTTP " + code);
        }
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        String tag = string(json, "tag_name");
        if (tag == null) {
            throw new IOException("release has no tag");
        }
        String url = null;
        long size = -1;
        String sha256 = null;
        JsonElement assets = json.get("assets");
        if (assets != null && assets.isJsonArray()) {
            JsonArray array = assets.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject asset = element.getAsJsonObject();
                if (!APK_ASSET.equals(string(asset, "name"))) {
                    continue;
                }
                url = string(asset, "browser_download_url");
                if (asset.has("size") && asset.get("size").isJsonPrimitive()) {
                    size = asset.get("size").getAsLong();
                }
                // The API publishes "sha256:<hex>"; anything else is not a digest we can check.
                String digest = string(asset, "digest");
                if (digest != null && digest.toLowerCase(Locale.ROOT).startsWith("sha256:")) {
                    sha256 = digest.substring("sha256:".length()).trim().toLowerCase(Locale.ROOT);
                }
                break;
            }
        }
        if (url == null) {
            url = "https://github.com/Jaffe2718/PetProfile/releases/download/" + tag + "/" + APK_ASSET;
        }
        String version = tag.startsWith("v") ? tag.substring(1) : tag;
        return new Release(tag, version, url, size, sha256);
    }

    /**
     * Downloads the release APK, resuming a previous partial transfer when one is present, and only
     * reports success once the file matches the published SHA-256 (and size, when known).
     */
    public static Download download(Context context, Release release, DownloadCallback callback) {
        Download download = new Download();
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            File dir = updateDir(appContext);
            File part = new File(dir, APK_ASSET + PART_SUFFIX);
            File apk = new File(dir, APK_ASSET);
            File etagFile = new File(dir, APK_ASSET + ETAG_SUFFIX);
            try {
                // A package that was downloaded and verified earlier is reused as is: the user may
                // simply not have gone through the installer yet (a missing install permission, a
                // dismissed dialog), and downloading the same bytes again would be pointless.
                File cached = verifiedCachedApk(appContext, release);
                if (cached != null) {
                    callback.onProgress(cached.length(), release.size > 0 ? release.size : cached.length(), false);
                    callback.onReady(cached);
                    return;
                }
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IOException("cannot create " + dir);
                }
                boolean resumed = part.isFile() && part.length() > 0;
                long existing = resumed ? part.length() : 0;
                if (release.size > 0 && existing >= release.size) {
                    // A complete-looking leftover: keep it only if it verifies, otherwise start over.
                    if (matches(part, release)) {
                        replace(part, apk);
                        etagFile.delete();
                        callback.onReady(apk);
                        return;
                    }
                    part.delete();
                    existing = 0;
                    resumed = false;
                }

                HttpURLConnection connection = (HttpURLConnection) new URL(release.apkUrl).openConnection();
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(20_000);
                connection.setReadTimeout(60_000);
                connection.setRequestProperty("User-Agent", "PetProfile");
                if (existing > 0) {
                    connection.setRequestProperty("Range", "bytes=" + existing + "-");
                    String previousEtag = readText(etagFile);
                    // If the published file changed since the partial transfer, start over instead of
                    // stitching two different builds together.
                    if (previousEtag != null) {
                        connection.setRequestProperty("If-Range", previousEtag);
                    }
                }
                int code = connection.getResponseCode();
                if (code == 206) {
                    long remaining = connection.getContentLengthLong();
                    if (remaining < 0) {
                        existing = 0;
                        resumed = false;
                    }
                } else if (code == 200) {
                    // The server ignored the range, so this response is the whole file again.
                    existing = 0;
                    resumed = false;
                } else {
                    String detail = readAll(connection.getErrorStream());
                    connection.disconnect();
                    throw new IOException("HTTP " + code + (detail.isEmpty() ? "" : " " + detail));
                }
                long total = release.size > 0 ? release.size : existing + connection.getContentLengthLong();
                String etag = connection.getHeaderField("ETag");
                if (etag != null) {
                    writeText(etagFile, etag);
                }

                long done = existing;
                if (resumed) {
                    callback.onProgress(done, total, true);
                }
                try (InputStream in = connection.getInputStream();
                     OutputStream out = new FileOutputStream(part, existing > 0)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long lastReport = 0;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (download.isCancelled()) {
                            throw new InterruptedException();
                        }
                        out.write(buffer, 0, read);
                        done += read;
                        if (done - lastReport >= PROGRESS_STEP) {
                            lastReport = done;
                            callback.onProgress(done, total, resumed);
                        }
                    }
                } finally {
                    connection.disconnect();
                }
                callback.onProgress(done, total, resumed);

                callback.onVerifying();
                if (!matches(part, release)) {
                    long actualSize = part.length();
                    part.delete();
                    etagFile.delete();
                    throw new IOException("checksum mismatch (" + actualSize + " bytes)");
                }
                replace(part, apk);
                etagFile.delete();
                Log.i(TAG, "update downloaded and verified: " + apk.getAbsolutePath());
                callback.onReady(apk);
            } catch (InterruptedException e) {
                // Cancelled: keep the partial file so the next attempt can resume it.
                Async.ui(() -> callback.onError(""));
            } catch (Throwable t) {
                Log.w(TAG, "update download failed", t);
                String message = t.getMessage();
                Async.ui(() -> callback.onError(message == null ? t.getClass().getSimpleName() : message));
            }
        });
        return download;
    }

    /**
     * Launches the system installer for a downloaded APK.
     *
     * @return false when the user first has to allow installing from this app, in which case the
     *         settings screen for that permission has been opened instead
     */
    public static boolean install(Activity activity, File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
            } catch (Throwable t) {
                Log.w(TAG, "cannot open the unknown-sources settings", t);
            }
            return false;
        }
        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        return true;
    }

    /**
     * Removes leftovers from earlier updates. A cached package that is newer than what is installed
     * is kept: it is still waiting for the user to walk through the installer. Everything else — a
     * package that has been installed already, one whose manifest cannot be read, or a partially
     * transferred file that nobody touched for a week — is dropped.
     */
    public static void cleanupStale(Context context) {
        File dir = updateDir(context);
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long installed = installedVersionCode(context);
        long now = System.currentTimeMillis();
        for (File file : files) {
            String name = file.getName();
            boolean stale = now - file.lastModified() > STALE_MS;
            if (name.endsWith(".apk")) {
                Long version = versionCodeOf(context, file);
                if (version == null || version <= installed) {
                    Log.i(TAG, "removing cached update " + name + " (version " + version
                            + " <= installed " + installed + ")");
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            } else if ((name.endsWith(PART_SUFFIX) || name.endsWith(ETAG_SUFFIX)) && stale) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    /**
     * The finished download of this exact release, when the cache still holds one that hashes
     * correctly. A file that is unreadable or no longer matches the release is deleted here, so a
     * corrupt leftover can never be installed later on.
     */
    private static File verifiedCachedApk(Context context, Release release) {
        File apk = new File(updateDir(context), APK_ASSET);
        if (!apk.isFile()) {
            return null;
        }
        if (versionCodeOf(context, apk) != null && matches(apk, release)) {
            return apk;
        }
        Log.w(TAG, "discarding an unusable cached update");
        //noinspection ResultOfMethodCallIgnored
        apk.delete();
        return null;
    }

    /**
     * The cached update package that is still waiting to be installed, if there is one. Used when
     * the user comes back through the "download finished" notification.
     */
    public static File pendingInstallApk(Context context) {
        File apk = new File(updateDir(context), APK_ASSET);
        if (!apk.isFile()) {
            return null;
        }
        Long version = versionCodeOf(context, apk);
        return version != null && version > installedVersionCode(context) ? apk : null;
    }

    // ----- helpers -----

    private static File updateDir(Context context) {
        return new File(context.getCacheDir(), UPDATE_DIR);
    }

    /** True when the file has the published digest and, when known, the published size. */
    private static boolean matches(File file, Release release) {
        if (!file.isFile()) {
            return false;
        }
        if (release.size > 0 && file.length() != release.size) {
            return false;
        }
        if (release.sha256 == null) {
            Log.w(TAG, "release publishes no SHA-256; accepted on size alone");
            return release.size > 0;
        }
        try {
            return release.sha256.equalsIgnoreCase(sha256(file));
        } catch (Throwable t) {
            Log.w(TAG, "cannot hash the downloaded file", t);
            return false;
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static void replace(File from, File to) throws IOException {
        if (to.exists() && !to.delete()) {
            throw new IOException("cannot replace " + to.getName());
        }
        if (!from.renameTo(to)) {
            // Fall back to a copy when the rename is refused.
            try (InputStream in = new java.io.FileInputStream(from);
                 OutputStream out = new FileOutputStream(to)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            //noinspection ResultOfMethodCallIgnored
            from.delete();
        }
    }

    private static long installedVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** The APK's own version code, read from its manifest without installing it. */
    private static Long versionCodeOf(Context context, File apk) {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            return info == null ? null : (long) info.versionCode;
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when {@code version} (x.y.z) is newer than the running build. */
    private static boolean isNewer(String version) {
        int[] remote = parseVersion(version);
        int[] current = parseVersion(BuildConfig.VERSION_NAME);
        if (remote == null || current == null) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (remote[i] != current[i]) {
                return remote[i] > current[i];
            }
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            if (parts.length < 3) {
                return null;
            }
            return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            };
        } catch (Exception e) {
            return null;
        }
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static String readAll(InputStream input) {
        if (input == null) {
            return "";
        }
        try (InputStream in = input) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String readText(File file) {
        try (InputStream in = new java.io.FileInputStream(file)) {
            return readAll(in).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeText(File file, String text) {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Log.w(TAG, "cannot store the ETag", t);
        }
    }
}
