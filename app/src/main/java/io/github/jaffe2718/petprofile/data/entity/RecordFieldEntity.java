package io.github.jaffe2718.petprofile.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.annotation.NonNull;

@Entity(
        tableName = "record_fields",
        primaryKeys = {"recordId", "fieldKey"},
        foreignKeys = @ForeignKey(
                entity = RecordEntity.class,
                parentColumns = "id",
                childColumns = "recordId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("recordId")}
)
public class RecordFieldEntity {
    @NonNull
    public String recordId;
    @NonNull
    public String fieldKey;
    public String fieldName;
    public String fieldType;
    public Double numericValue;
    public String unit;
    public String textValue;
    public int position;

    public RecordFieldEntity() {
    }
}
