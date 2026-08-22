package io.github.jaffe2718.petprofile.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import io.github.jaffe2718.petprofile.data.entity.RoutineEntity;

import java.util.List;

@Dao
public interface RoutineDao {
    @Query("SELECT * FROM routines WHERE profileId = :profileId ORDER BY position ASC")
    List<RoutineEntity> getRoutinesForProfile(String profileId);

    @Query("SELECT * FROM routines WHERE enabled = 1 ORDER BY profileId")
    List<RoutineEntity> getEnabledRoutines();

    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    RoutineEntity getById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RoutineEntity> routines);

    @Update
    void update(RoutineEntity routine);

    @Query("DELETE FROM routines WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM routines WHERE profileId = :profileId")
    void deleteForProfile(String profileId);
}
