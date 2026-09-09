package io.github.jaffe2718.petprofile.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import io.github.jaffe2718.petprofile.R;
import io.github.jaffe2718.petprofile.data.ExportBundle;
import io.github.jaffe2718.petprofile.repository.PetRepository;

/**
 * Minimal Google Drive backup using play-services-auth (GoogleSignIn) + Drive REST v3.
 * The uploaded payload is the same ".petprofile" zip used by BackupManager.
 */
public final class DriveBackupManager {
    public static final String FILE_NAME = "pet-profile-backup.zip";
    public static final int REQUEST_AUTH = 65303;
    private static final String SCOPES = "https://www.googleapis.com/auth/drive.file";
    private static final String PREFS = "pet_profile_drive";
    private static final String KEY_EMAIL = "account_email";
    private static final String KEY_FILE_ID = "file_id";
    private static final Gson GSON = new Gson();

    public interface Callback {
        void onSuccess(String message);

        void onError(String message);
    }

    private DriveBackupManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isSignedIn(Context context) {
        return getAccountEmail(context) != null;
    }

    public static String getAccountEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, null);
    }

    public static void saveAccount(Context context, GoogleSignInAccount account) {
        if (account == null || account.getEmail() == null) {
            return;
        }
        prefs(context).edit().putString(KEY_EMAIL, account.getEmail()).apply();
    }

    public static void clearAccount(Context context) {
        prefs(context).edit().remove(KEY_EMAIL).remove(KEY_FILE_ID).apply();
    }

    public static String getClientId(Context context) {
        return context.getString(R.string.google_oauth_client_id);
    }

    public static boolean hasClientId(Context context) {
        String id = getClientId(context);
        return id != null && !id.trim().isEmpty();
    }

    public static GoogleSignInAccount getLastSignedInAccount(Context context) {
        return GoogleSignIn.getLastSignedInAccount(context);
    }

    public static GoogleSignInClient buildSignInClient(Context context) {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(SCOPES))
                .build();
        return GoogleSignIn.getClient(context, options);
    }

    public static Intent getSignInIntent(Context context) {
        return buildSignInClient(context).getSignInIntent();
    }

    public static void signOut(Context context) {
        clearAccount(context);
        buildSignInClient(context).signOut();
    }

    /** Blocking token fetch; call off the main thread. */
    private static String fetchToken(Context context, String accountEmail) throws Exception {
        return GoogleAuthUtil.getToken(context, accountEmail, "oauth2:" + SCOPES);
    }

    /** Build and upload the backup zip to Drive. Run off the main thread. */
    public static void upload(Activity activity, byte[] zipBytes, Callback callback) {
        String email = getAccountEmail(activity);
        if (email == null) {
            callback.onError(activity.getString(R.string.drive_not_signed_in));
            return;
        }
        Async.run(() -> {
            try {
                String token = fetchToken(activity, email);
                String fileId = upsertFile(activity, token, zipBytes);
                if (fileId != null) {
                    prefs(activity).edit().putString(KEY_FILE_ID, fileId).apply();
                }
                Async.ui(() -> callback.onSuccess(activity.getString(R.string.drive_synced)));
            } catch (UserRecoverableAuthException e) {
                launchConsent(activity, e, callback);
            } catch (Throwable t) {
                Async.ui(() -> callback.onError(t.getMessage()));
            }
        });
    }

    /** Download the backup zip from Drive and import it into the database. Run off the main thread. */
    public static void download(Activity activity, Callback callback) {
        String email = getAccountEmail(activity);
        if (email == null) {
            callback.onError(activity.getString(R.string.drive_not_signed_in));
            return;
        }
        Async.run(() -> {
            try {
                String token = fetchToken(activity, email);
                String fileId = prefs(activity).getString(KEY_FILE_ID, null);
                if (fileId == null) {
                    fileId = findExistingFile(activity, token);
                }
                if (fileId == null) {
                    Async.ui(() -> callback.onError("Drive 中没有备份文件"));
                    return;
                }
                byte[] zipBytes = downloadFile(activity, token, fileId);
                ExportBundle bundle = BackupManager.readZipBytes(activity, zipBytes);
                PetRepository repository = PetRepository.get(activity);
                repository.importBundle(bundle, new Async.EmptyResult() {
                    @Override
                    public void onSuccess() {
                        RoutineScheduler.scheduleAll(activity);
                        RoutineNotifier.sync(activity);
                        Async.ui(() -> callback.onSuccess(activity.getString(R.string.drive_restored)));
                    }

                    @Override
                    public void onError(Throwable error) {
                        Async.ui(() -> callback.onError(error.getMessage()));
                    }
                });
            } catch (UserRecoverableAuthException e) {
                launchConsent(activity, e, callback);
            } catch (Throwable t) {
                Async.ui(() -> callback.onError(t.getMessage()));
            }
        });
    }

    /** Google needs a one-time consent for the Drive scope; hand the Intent to the activity to resume. */
    private static void launchConsent(Activity activity, UserRecoverableAuthException e, Callback callback) {
        Async.ui(() -> {
            try {
                activity.startActivityForResult(e.getIntent(), REQUEST_AUTH);
            } catch (Throwable ex) {
                callback.onError(ex.getMessage());
            }
        });
    }

    /** Returns the existing Drive file id for the backup, or null if none exists. */
    private static String findExistingFile(Context context, String token) throws Exception {
        URL url = new URL("https://www.googleapis.com/drive/v3/files?q=name='" + FILE_NAME
                + "'&fields=files(id)");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        int code = connection.getResponseCode();
        if (code != 200) {
            throw new IOException("Drive list failed: HTTP " + code + " " + readErrorBody(connection));
        }
        String body = readBody(connection.getInputStream());
        JsonObject obj = GSON.fromJson(body, JsonObject.class);
        JsonArray files = obj != null && obj.has("files") ? obj.getAsJsonArray("files") : new JsonArray();
        if (files.size() > 0) {
            return files.get(0).getAsJsonObject().get("id").getAsString();
        }
        return null;
    }

    /** Upload the file, replacing an existing one with the same name. Returns the file id. */
    private static String upsertFile(Context context, String token, byte[] zipBytes) throws Exception {
        String fileId = findExistingFile(context, token);
        String urlText = fileId != null
                ? "https://www.googleapis.com/upload/drive/v3/files/" + fileId + "?uploadType=multipart"
                : "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";
        String boundary = "PetProfileBoundary" + System.nanoTime();
        byte[] body = buildMultipart(boundary, zipBytes);
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod(fileId != null ? "PATCH" : "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }
        int code = connection.getResponseCode();
        if (code != 200 && code != 201) {
            throw new IOException("Drive upload failed: HTTP " + code + " " + readErrorBody(connection));
        }
        String bodyText = readBody(connection.getInputStream());
        JsonObject obj = GSON.fromJson(bodyText, JsonObject.class);
        if (obj != null && obj.has("id")) {
            return obj.get("id").getAsString();
        }
        return fileId;
    }

    /** Build a multipart/related body: JSON metadata (name) + zip bytes. */
    private static byte[] buildMultipart(String boundary, byte[] zipBytes) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        String meta = "{\"name\":\"" + FILE_NAME + "\"}";
        buffer.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        buffer.write(("Content-Type: application/json; charset=UTF-8\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        buffer.write(meta.getBytes(StandardCharsets.UTF_8));
        buffer.write("\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        buffer.write(("Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        buffer.write(zipBytes);
        buffer.write("\r\n".getBytes(StandardCharsets.UTF_8));
        buffer.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return buffer.toByteArray();
    }

    private static byte[] downloadFile(Context context, String token, String fileId) throws Exception {
        URL url = new URL("https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        int code = connection.getResponseCode();
        if (code != 200) {
            throw new IOException("Drive download failed: HTTP " + code);
        }
        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private static String readBody(InputStream input) throws IOException {
        try (InputStream in = input;
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String readErrorBody(HttpURLConnection connection) {
        try (InputStream in = connection.getErrorStream()) {
            if (in == null) {
                return "";
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[2048];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
