package io.github.jaffe2718.petprofile.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import io.github.jaffe2718.petprofile.data.entity.RecordEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.RecordImageEntity;

import java.util.List;

@Dao
public interface RecordDao {
    @Query("SELECT * FROM records WHERE profileId = :profileId ORDER BY timestamp DESC")
    List<RecordEntity> getRecordsForProfile(String profileId);

    @Query("SELECT MAX(timestamp) FROM records WHERE profileId = :profileId")
    Long getLatestTimestamp(String profileId);

    @Query("SELECT * FROM records WHERE profileId = :profileId ORDER BY timestamp ASC")
    List<RecordEntity> getRecordsForProfileOldestFirst(String profileId);

    @Query("SELECT * FROM records WHERE id = :id LIMIT 1")
    RecordEntity getById(String id);

    @Query("SELECT * FROM records WHERE profileId = :profileId AND type = :type LIMIT 1")
    RecordEntity getFirstByType(String profileId, String type);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecord(RecordEntity record);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRecords(List<RecordEntity> records);

    @Update
    void updateRecord(RecordEntity record);

    @Query("DELETE FROM records WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT COUNT(*) FROM records WHERE profileId = :profileId AND type = :type")
    int countByType(String profileId, String type);

    @Query("SELECT * FROM record_fields WHERE recordId = :recordId ORDER BY position ASC")
    List<RecordFieldEntity> getFields(String recordId);

    @Query("SELECT * FROM record_fields WHERE recordId IN (:recordIds) ORDER BY recordId, position ASC")
    List<RecordFieldEntity> getFieldsForRecords(List<String> recordIds);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFields(List<RecordFieldEntity> fields);

    @Query("DELETE FROM record_fields WHERE recordId = :recordId")
    void deleteFields(String recordId);

    @Query("SELECT * FROM record_images WHERE recordId = :recordId ORDER BY position ASC")
    List<RecordImageEntity> getImages(String recordId);

    @Query("SELECT * FROM record_images WHERE recordId IN (:recordIds) ORDER BY recordId, position ASC")
    List<RecordImageEntity> getImagesForRecords(List<String> recordIds);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertImages(List<RecordImageEntity> images);

    @Query("DELETE FROM record_images WHERE recordId = :recordId")
    void deleteImages(String recordId);

    @Query("DELETE FROM records WHERE profileId = :profileId")
    void deleteRecordsForProfile(String profileId);
}
