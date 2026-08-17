package io.github.jaffe2718.petprofile.data;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileParentCrossRef;
import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;
import io.github.jaffe2718.petprofile.data.dao.ProfileDao;
import io.github.jaffe2718.petprofile.data.dao.RecordDao;

@Database(
        entities = {
                ProfileEntity.class,
                ProfileCustomFieldEntity.class,
                ProfileParentCrossRef.class,
                RecordEntity.class,
                RecordFieldEntity.class,
                RecordImageEntity.class
        },
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;

    public abstract ProfileDao profileDao();

    public abstract RecordDao recordDao();

    public static AppDatabase getInstance(android.content.Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "pet_profile.db"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
