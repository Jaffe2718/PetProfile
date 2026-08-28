package io.github.jaffe2718.petprofile.mcp;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Holds the bearer token used to authenticate LAN MCP clients. The token is generated
 * automatically on first use and can be refreshed (invalidating the previous one).
 */
public final class McpTokenManager {
    private static final String PREFS_NAME = "pet_profile_mcp";
    private static final String KEY_TOKEN = "auth_token";
    private static final SecureRandom RANDOM = new SecureRandom();

    private McpTokenManager() {
    }

    public static String getToken(Context context) {
        SharedPreferences prefs = prefs(context);
        String token = prefs.getString(KEY_TOKEN, null);
        if (token == null || token.trim().isEmpty()) {
            token = generateToken();
            prefs.edit().putString(KEY_TOKEN, token).apply();
        }
        return token;
    }

    public static String refreshToken(Context context) {
        String token = generateToken();
        prefs(context).edit().putString(KEY_TOKEN, token).apply();
        return token;
    }

    private static String generateToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
