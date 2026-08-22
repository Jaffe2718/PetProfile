package io.github.jaffe2718.petprofile.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "routines",
        foreignKeys = @ForeignKey(
                entity = ProfileEntity.class,
                parentColumns = "id",
                childColumns = "profileId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("profileId")}
)
public class RoutineEntity {
    public static final String TYPE_WEEKLY = "WEEKLY";
    public static final String TYPE_ONCE = "ONCE";

    @PrimaryKey
    @NonNull
    public String id;

    public String profileId;
    public String type = TYPE_WEEKLY;
    public boolean enabled = true;
    public String title = "";
    public int position;

    /** Comma-separated day indexes: 0=Sun, 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat. */
    public String weekdays = "";

    public int hour;
    public int minute;
    public int second;

    /** For TYPE_ONCE: the timestamp at which the reminder fires. */
    public Long onceAt;

    public String details = "";

    /** Timestamp of the last time this reminder was delivered (used to auto-disable one-shot). */
    public Long lastFiredAt;

    public RoutineEntity() {
    }
}
