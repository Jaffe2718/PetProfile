package io.github.jaffe2718.petprofile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "record_images",
        foreignKeys = @ForeignKey(
                entity = RecordEntity.class,
                parentColumns = "id",
                childColumns = "recordId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("recordId")}
)
public class RecordImageEntity {
    @PrimaryKey
    @NonNull
    public String id;
    public String recordId;
    public String uri;
    public int position;

    public RecordImageEntity() {
    }
}
