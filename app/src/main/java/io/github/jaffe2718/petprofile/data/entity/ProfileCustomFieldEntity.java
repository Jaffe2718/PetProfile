package io.github.jaffe2718.petprofile.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.annotation.NonNull;

@Entity(
        tableName = "profile_custom_fields",
        primaryKeys = {"profileId", "fieldKey"},
        foreignKeys = @ForeignKey(
                entity = ProfileEntity.class,
                parentColumns = "id",
                childColumns = "profileId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("profileId")}
)
public class ProfileCustomFieldEntity {
    @NonNull
    public String profileId;
    @NonNull
    public String fieldKey;
    public String fieldName;
    public String fieldType;
    public String textValue;
    public Double numericValue;
    public String unit;
    public int position;

    public ProfileCustomFieldEntity() {
    }
}
