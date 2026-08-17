package io.github.jaffe2718.petprofile;

import android.app.Application;

import io.github.jaffe2718.petprofile.data.AppDatabase;

public class PetProfileApplication extends Application {
    private static AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        database = AppDatabase.getInstance(this);
    }

    public static AppDatabase getDatabase() {
        return database;
    }
}
