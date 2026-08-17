package io.github.jaffe2718.petprofile.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.annotation.NonNull;

@Entity(
        tableName = "profile_parents",
        primaryKeys = {"childId", "parentId"},
        foreignKeys = {
                @ForeignKey(
                        entity = ProfileEntity.class,
                        parentColumns = "id",
                        childColumns = "childId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = ProfileEntity.class,
                        parentColumns = "id",
                        childColumns = "parentId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {@Index("parentId")}
)
public class ProfileParentCrossRef {
    @NonNull
    public String childId;
    @NonNull
    public String parentId;
    @NonNull
    public String role = "";

    public ProfileParentCrossRef() {
    }
}
