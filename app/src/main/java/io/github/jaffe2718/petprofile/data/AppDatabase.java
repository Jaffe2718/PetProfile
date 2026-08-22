package io.github.jaffe2718.petprofile.data;

import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileParentCrossRef;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;
import io.github.jaffe2718.petprofile.data.entity.RoutineEntity;
import io.github.jaffe2718.petprofile.data.dao.ProfileDao;
import io.github.jaffe2718.petprofile.data.dao.RecordDao;
import io.github.jaffe2718.petprofile.data.dao.RoutineDao;

@Database(
        entities = {
                ProfileEntity.class,
                ProfileCustomFieldEntity.class,
                ProfileParentCrossRef.class,
                RecordEntity.class,
                RecordFieldEntity.class,
                RecordImageEntity.class,
                RoutineEntity.class
        },
        version = 6,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE records ADD COLUMN keeperName TEXT");
        }
    };

    private static volatile AppDatabase instance;

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS routines ("
                    + "id TEXT NOT NULL PRIMARY KEY, "
                    + "profileId TEXT, "
                    + "type TEXT NOT NULL, "
                    + "enabled INTEGER NOT NULL DEFAULT 1, "
                    + "title TEXT NOT NULL, "
                    + "position INTEGER NOT NULL DEFAULT 0, "
                    + "weekdays TEXT NOT NULL DEFAULT '', "
                    + "hour INTEGER NOT NULL DEFAULT 0, "
                    + "minute INTEGER NOT NULL DEFAULT 0, "
                    + "second INTEGER NOT NULL DEFAULT 0, "
                    + "onceAt INTEGER, "
                    + "details TEXT NOT NULL DEFAULT '', "
                    + "lastFiredAt INTEGER)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_routines_profileId ON routines(profileId)");
        }
    };

    public abstract ProfileDao profileDao();

    public abstract RecordDao recordDao();

    public abstract RoutineDao routineDao();

    public static AppDatabase getInstance(android.content.Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                            AppDatabase.class,
                            "pet_profile.db"
                    )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
