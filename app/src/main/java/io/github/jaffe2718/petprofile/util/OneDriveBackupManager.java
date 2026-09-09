package io.github.jaffe2718.petprofile.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import io.github.jaffe2718.petprofile.BuildConfig;
import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.repository.PetRepository;

/**
 * OneDrive backup via Microsoft Graph using the authorization-code + PKCE flow (no SDK).
 * The app's Android client_id is registered in Azure; users sign in with a Microsoft account.
 */
public final class OneDriveBackupManager {
    public static final String FILE_NAME = "pet-profile-backup.zip";
    private static final String SCOPE = "Files.ReadWrite.AppFolder offline_access openid profile";
    private static final String AUTHORITY = "https://login.microsoftonline.com/consumers";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String PREFS = "pet_profile_onedrive";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_EXPIRES = "expires_at";
    private static final String KEY_ACCOUNT = "account_name";

    public interface Callback {
        void onSuccess(String message);

        void onError(String message);
    }

    private interface TokenReady {
        void onReady(String token);
    }

    // pending operation carried across the browser redirect
    private static TokenReady pendingReady;
    private static String codeVerifier;
    private static String state;

    private OneDriveBackupManager() {
    }

    public static String getClientId(Context context) {
        return BuildConfig.MSAL_CLIENT_ID;
    }

    public static boolean hasClientId(Context context) {
        String id = BuildConfig.MSAL_CLIENT_ID;
        return id != null && !id.trim().isEmpty() && !id.contains("YOUR_MSAL");
    }

    public static boolean isSignedIn(Context context) {
        String access = prefs(context).getString(KEY_ACCESS, null);
        long expires = prefs(context).getLong(KEY_EXPIRES, 0);
        return access != null && System.currentTimeMillis() < expires;
    }

    /** The signed-in Microsoft account name/email, or null. */
    public static String getAccountName(Context context) {
        return prefs(context).getString(KEY_ACCOUNT, null);
    }

    public static void signOut(Context context) {
        prefs(context).edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES).remove(KEY_ACCOUNT).apply();
    }

    /** Acquire a token (showing the Microsoft login in a browser if needed) and report success. */
    public static void signIn(Activity activity, Callback callback) {
        if (!hasClientId(activity)) {
            callback.onError(activity.getString(R.string.onedrive_no_client_id));
            return;
        }
        getToken(activity, token -> callback.onSuccess("ok"), callback);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String redirectUri(Context context) {
        return "msal" + BuildConfig.MSAL_CLIENT_ID + "://auth";
    }

    public static void upload(Activity activity, byte[] zipBytes, Callback callback) {
        if (!hasClientId(activity)) {
            callback.onError(activity.getString(R.string.onedrive_no_client_id));
            return;
        }
        getToken(activity, token -> Async.run(() -> {
            try {
                HttpURLConnection connection = openGraph("PUT", token);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/zip");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(zipBytes);
                }
                int code = connection.getResponseCode();
                Log.i("ODBackup", "upload response=" + code + " len=" + zipBytes.length);
                if (code != 200 && code != 201) {
                    throw new IOException("OneDrive upload failed: HTTP " + code + " " + readError(connection));
                }
                Async.ui(() -> callback.onSuccess(activity.getString(R.string.onedrive_synced)));
            } catch (Throwable t) {
                Log.e("ODBackup", "upload error", t);
                Async.ui(() -> callback.onError(t.getMessage()));
            }
        }), callback);
    }

    public static void download(Activity activity, Callback callback) {
        if (!hasClientId(activity)) {
            callback.onError(activity.getString(R.string.onedrive_no_client_id));
            return;
        }
        getToken(activity, token -> Async.run(() -> {
            try {
                HttpURLConnection connection = openGraph("GET", token);
                int code = connection.getResponseCode();
                Log.i("ODBackup", "download response=" + code);
                if (code != 200) {
                    throw new IOException("OneDrive download failed: HTTP " + code + " " + readError(connection));
                }
                byte[] zipBytes = readAll(connection.getInputStream());
                Log.i("ODBackup", "download bytes=" + zipBytes.length);
                ExportBundle bundle = BackupManager.readZipBytes(activity, zipBytes);
                PetRepository.get(activity).importBundle(bundle, new Async.EmptyResult() {
                    @Override
                    public void onSuccess() {
                        RoutineScheduler.scheduleAll(activity);
                        RoutineNotifier.sync(activity);
                        Async.ui(() -> callback.onSuccess(activity.getString(R.string.onedrive_restored)));
                    }

                    @Override
                    public void onError(Throwable error) {
                        Async.ui(() -> callback.onError(error.getMessage()));
                    }
                });
            } catch (Throwable t) {
                Async.ui(() -> callback.onError(t.getMessage()));
            }
        }), callback);
    }

    private static volatile String lastResult;
    private static volatile boolean cloudBusy = false;

    /** True while an upload/download is running (survives page recreation). */
    public static boolean isCloudBusy() {
        return cloudBusy;
    }

    public static void setCloudBusy(boolean busy) {
        cloudBusy = busy;
    }

    /** Result of the most recent MCP-triggered upload/download, so the LLM can capture the callback. */
    public static String getLastResult() {
        return lastResult;
    }

    public static void setLastResult(String result) {
        lastResult = result;
    }

    /** MCP helper: upload using the cached access token (must already be signed in). */
    public static void upload(Context context, byte[] zipBytes, Callback cb) {
        String token = prefs(context).getString(KEY_ACCESS, null);
        if (token == null) {
            reportDone(cb, context.getString(R.string.onedrive_not_signed_in));
            return;
        }
        Async.run(() -> {
            try {
                HttpURLConnection connection = openGraph("PUT", token);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/zip");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(zipBytes);
                }
                int code = connection.getResponseCode();
                String result = (code == 200 || code == 201)
                        ? context.getString(R.string.onedrive_synced)
                        : "OneDrive upload failed: HTTP " + code + " " + readError(connection);
                reportDone(cb, result);
            } catch (Throwable t) {
                reportDone(cb, t.getMessage());
            }
        });
    }

    /** MCP helper: download using the cached access token (must already be signed in). */
    public static void download(Context context, Callback cb) {
        String token = prefs(context).getString(KEY_ACCESS, null);
        if (token == null) {
            reportDone(cb, context.getString(R.string.onedrive_not_signed_in));
            return;
        }
        Async.run(() -> {
            try {
                HttpURLConnection connection = openGraph("GET", token);
                int code = connection.getResponseCode();
                if (code != 200) {
                    reportDone(cb, "OneDrive download failed: HTTP " + code + " " + readError(connection));
                    return;
                }
                byte[] zipBytes = readAll(connection.getInputStream());
                ExportBundle bundle = BackupManager.readZipBytes(context, zipBytes);
                PetRepository.get(context).importBundle(bundle, new Async.EmptyResult() {
                    @Override
                    public void onSuccess() {
                        RoutineScheduler.scheduleAll(context);
                        RoutineNotifier.sync(context);
                        reportDone(cb, context.getString(R.string.onedrive_restored));
                    }

                    @Override
                    public void onError(Throwable error) {
                        reportDone(cb, error.getMessage());
                    }
                });
            } catch (Throwable t) {
                reportDone(cb, t.getMessage());
            }
        });
    }

    private static void reportDone(Callback cb, String message) {
        lastResult = message;
        Async.ui(() -> cb.onSuccess(message));
    }

    private static HttpURLConnection openGraph(String method, String token) throws Exception {
        String url = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/" + FILE_NAME + ":/content";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        return connection;
    }

    private static void getToken(Activity activity, TokenReady ready, Callback callback) {
        pendingReady = ready;
        String access = prefs(activity).getString(KEY_ACCESS, null);
        long expires = prefs(activity).getLong(KEY_EXPIRES, 0);
        Log.i("ODBackup", "getToken access=" + (access != null) + " expires=" + expires + " now=" + System.currentTimeMillis());
        if (access != null && System.currentTimeMillis() < expires) {
            pendingReady = null;
            ready.onReady(access);
            return;
        }
        String refresh = prefs(activity).getString(KEY_REFRESH, null);
        if (refresh != null) {
            refreshToken(activity, refresh, callback);
            return;
        }
        startLogin(activity, callback);
    }

    private static void startLogin(Activity activity, Callback callback) {
        if (!hasClientId(activity)) {
            callback.onError(activity.getString(R.string.onedrive_no_client_id));
            return;
        }
        codeVerifier = randomString(64);
        state = randomString(16);
        String challenge;
        try {
            challenge = sha256Base64Url(codeVerifier);
        } catch (Exception e) {
            fail(activity, e.getMessage());
            return;
        }
        String url = AUTHORITY + "/oauth2/v2.0/authorize"
                + "?client_id=" + enc(getClientId(activity))
                + "&response_type=code"
                + "&redirect_uri=" + enc(redirectUri(activity))
                + "&response_mode=query"
                + "&scope=" + enc(SCOPE)
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(challenge)
                + "&code_challenge_method=S256";
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        activity.startActivity(intent);
    }

    /** Called from OneDriveAuthActivity after the browser returns. */
    public static void handleRedirect(Activity activity, Uri uri) {
        String code = uri.getQueryParameter("code");
        String returnedState = uri.getQueryParameter("state");
        if (returnedState != null && !returnedState.equals(state)) {
            fail(activity, "State mismatch");
            return;
        }
        if (code == null) {
            fail(activity, "授权失败: " + uri.getQueryParameter("error_description"));
            return;
        }
        exchangeToken(activity, code, new Callback() {
            @Override
            public void onSuccess(String message) {
                runPending(activity);
            }

            @Override
            public void onError(String message) {
                fail(activity, message);
            }
        });
    }

    private static void runPending(Activity activity) {
        if (pendingReady == null) {
            return;
        }
        String access = prefs(activity).getString(KEY_ACCESS, null);
        if (access == null) {
            fail(activity, "未获取到访问令牌");
            return;
        }
        TokenReady ready = pendingReady;
        pendingReady = null;
        ready.onReady(access);
    }

    private static void refreshToken(Activity activity, String refresh, Callback callback) {
        Async.run(() -> {
            try {
                String body = "client_id=" + enc(getClientId(activity))
                        + "&scope=" + enc(SCOPE)
                        + "&refresh_token=" + enc(refresh)
                        + "&grant_type=refresh_token";
                JsonObject json = postForm(TOKEN_URL, body);
                storeToken(activity, json);
                Async.ui(() -> runPending(activity));
            } catch (Throwable t) {
                Async.ui(() -> fail(activity, t.getMessage()));
            }
        });
    }

    private static void exchangeToken(Activity activity, String code, Callback callback) {
        Async.run(() -> {
            try {
                String body = "client_id=" + enc(getClientId(activity))
                        + "&scope=" + enc(SCOPE)
                        + "&code=" + enc(code)
                        + "&redirect_uri=" + enc(redirectUri(activity))
                        + "&grant_type=authorization_code"
                        + "&code_verifier=" + enc(codeVerifier);
                JsonObject json = postForm(TOKEN_URL, body);
                storeToken(activity, json);
                Async.ui(() -> callback.onSuccess("ok"));
            } catch (Throwable t) {
                Async.ui(() -> callback.onError(t.getMessage()));
            }
        });
    }

    private static void storeToken(Context context, JsonObject json) {
        String access = json.get("access_token") != null ? json.get("access_token").getAsString() : null;
        String refresh = json.get("refresh_token") != null ? json.get("refresh_token").getAsString() : null;
        String idToken = json.get("id_token") != null ? json.get("id_token").getAsString() : null;
        long expiresIn = json.get("expires_in") != null ? json.get("expires_in").getAsLong() : 3600;
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
            if (obj.get("name") != null) {
                return obj.get("name").getAsString();
            }
            if (obj.get("preferred_username") != null) {
                return obj.get("preferred_username").getAsString();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject postForm(String urlText, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = connection.getResponseCode();
        String text = code >= 400 ? readError(connection) : readAllStr(connection.getInputStream());
        if (code != 200) {
            throw new IOException("HTTP " + code + " " + text);
        }
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static void fail(Activity activity, String message) {
        pendingReady = null;
        Toast.makeText(activity, message == null || message.isEmpty() ? "操作失败" : message, Toast.LENGTH_LONG).show();
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

    private static String readAllStr(InputStream input) throws IOException {
        return new String(readAll(input), StandardCharsets.UTF_8);
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
}
