package io.github.jaffe2718.petprofile;

import android.app.Application;

import io.github.jaffe2718.petprofile.data.AppDatabase;
import io.github.jaffe2718.petprofile.util.Async;
import io.github.jaffe2718.petprofile.util.SyncNotifier;
import io.github.jaffe2718.petprofile.util.UpdateManager;

public class PetProfileApplication extends Application {
    private static AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        database = AppDatabase.getInstance(this);
        // A previous process may have been killed mid-sync, which leaves the progress notification
        // behind (it is a plain ongoing notification, not a foreground service's).
        SyncNotifier.finish(this);
        // Once the updated version is running, the APK that was installed from is dead weight.
        Async.run(() -> UpdateManager.cleanupStale(this));
    }

    public static AppDatabase getDatabase() {
        return database;
    }
}
