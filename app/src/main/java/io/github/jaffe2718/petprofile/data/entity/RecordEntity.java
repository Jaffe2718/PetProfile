package io.github.jaffe2718.petprofile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "records",
        foreignKeys = @ForeignKey(
                entity = ProfileEntity.class,
                parentColumns = "id",
                childColumns = "profileId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("profileId"), @Index("timestamp")}
)
public class RecordEntity {
    @PrimaryKey
    @NonNull
    public String id;

    public String profileId;
    @NonNull
    public String title = "";
    public String keeperName;
    public String type;
    public long timestamp;

    public String locationName;
    public Double latitude;
    public Double longitude;

    public String notesMarkdown = "";
    public String establishmentSource;
    public String archiveReason;
    public String transferFromPerson;
    public String transferToPerson;
    public String transferFromPlace;
    public String transferToPlace;

    public RecordEntity() {
    }
}
