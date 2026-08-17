package io.github.jaffe2718.petprofile.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import io.github.jaffe2718.petprofile.data.entity.ProfileCustomFieldEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileEntity;
import io.github.jaffe2718.petprofile.data.entity.ProfileParentCrossRef;

import java.util.List;

@Dao
public interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt DESC")
    List<ProfileEntity> getAllProfiles();

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    List<ProfileEntity> getAllProfilesOldestFirst();

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    ProfileEntity getById(String id);

    @Query("SELECT * FROM profiles WHERE id IN (:ids)")
    List<ProfileEntity> getByIds(List<String> ids);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertProfile(ProfileEntity profile);

    @Update
    void updateProfile(ProfileEntity profile);

    @Query("DELETE FROM profiles WHERE id = :id")
    void deleteById(String id);

    @Query("SELECT * FROM profile_custom_fields WHERE profileId = :profileId ORDER BY position ASC")
    List<ProfileCustomFieldEntity> getCustomFields(String profileId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCustomFields(List<ProfileCustomFieldEntity> fields);

    @Query("DELETE FROM profile_custom_fields WHERE profileId = :profileId")
    void deleteCustomFields(String profileId);

    @Query("SELECT parentId FROM profile_parents WHERE childId = :childId")
    List<String> getParentIds(String childId);

    @Query("SELECT * FROM profile_parents WHERE childId = :childId ORDER BY role ASC")
    List<ProfileParentCrossRef> getParentLinks(String childId);

    @Query("SELECT parentId FROM profile_parents WHERE childId = :childId AND role = :role LIMIT 1")
    String getParentIdByRole(String childId, String role);

    @Query("SELECT childId FROM profile_parents WHERE parentId = :parentId")
    List<String> getChildIds(String parentId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertParents(List<ProfileParentCrossRef> parents);

    @Query("DELETE FROM profile_parents WHERE childId = :childId")
    void deleteParents(String childId);

    @Query("DELETE FROM profile_parents WHERE parentId = :parentId AND role = :role")
    void deleteParentLinksByRole(String parentId, String role);

    @Query("DELETE FROM profile_parents WHERE parentId = :parentId")
    void deleteParentLinksByParent(String parentId);
}
