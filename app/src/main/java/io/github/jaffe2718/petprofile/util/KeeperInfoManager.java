package io.github.jaffe2718.petprofile.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;

import io.github.jaffe2718.petprofile.data.KeeperInfo;

public final class KeeperInfoManager {
    private static final String PREFS_NAME = "pet_profile_keeper_info";
    private static final String KEY_KEEPER = "keeper";
    private static final Gson GSON = new Gson();

    private KeeperInfoManager() {
    }

    public static KeeperInfo load(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = preferences.getString(KEY_KEEPER, null);
        if (json == null || json.trim().isEmpty()) {
            return new KeeperInfo();
        }
        try {
            KeeperInfo value = GSON.fromJson(json, KeeperInfo.class);
            return value == null ? new KeeperInfo() : value;
        } catch (Exception ignored) {
            return new KeeperInfo();
        }
    }

    public static void save(Context context, KeeperInfo info) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_KEEPER, GSON.toJson(info == null ? new KeeperInfo() : info)).apply();
    }

    public static String toJson(KeeperInfo info) {
        return GSON.toJson(info == null ? new KeeperInfo() : info);
    }

    public static KeeperInfo fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new KeeperInfo();
        }
        try {
            KeeperInfo value = GSON.fromJson(json, KeeperInfo.class);
            return value == null ? new KeeperInfo() : value;
        } catch (Exception ignored) {
            return new KeeperInfo();
        }
    }
}
